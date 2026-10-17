package com.wledcontroller.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

data class FoundDevice(val name: String, val host: String, val mac: String, val ledCount: Int, val version: String)

/** Finds WLED devices via mDNS (_wled._tcp) and a parallel sweep of the local /24 subnet. */
class Discovery(private val context: Context, private val api: WledApi) {

    fun discover(): Flow<FoundDevice> = channelFlow {
        val seen = ConcurrentHashMap.newKeySet<String>()

        suspend fun verify(host: String) {
            if (!seen.add(host)) return
            api.probe(host)?.let { send(FoundDevice(it.name, host, it.mac, it.ledCount, it.version)) }
        }

        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        val lock = wifi?.createMulticastLock("wled-discovery")?.apply {
            setReferenceCounted(false)
            runCatching { acquire() }
        }

        val nsd = context.getSystemService(NsdManager::class.java)
        val toResolve = Channel<NsdServiceInfo>(Channel.UNLIMITED)
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) { toResolve.trySend(info) }
            override fun onServiceLost(info: NsdServiceInfo) = Unit
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }
        val nsdStarted = nsd != null && runCatching {
            nsd.discoverServices("_wled._tcp", NsdManager.PROTOCOL_DNS_SD, listener)
        }.isSuccess

        if (nsd != null) {
            launch {
                // NsdManager only resolves one service at a time on older Android versions.
                for (info in toResolve) {
                    val host = withTimeoutOrNull(5_000) { resolve(nsd, info) }
                    if (host != null) launch { verify(host) }
                }
            }
        }
        launch { subnetScan { verify(it) } }

        awaitClose {
            if (nsdStarted) runCatching { nsd?.stopServiceDiscovery(listener) }
            toResolve.close()
            runCatching { lock?.release() }
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun resolve(nsd: NsdManager, info: NsdServiceInfo): String? =
        suspendCancellableCoroutine { cont ->
            try {
                nsd.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        if (cont.isActive) cont.resume(null)
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        val addr = serviceInfo.host as? Inet4Address
                        val host = addr?.hostAddress?.let { if (serviceInfo.port == 80 || serviceInfo.port <= 0) it else "$it:${serviceInfo.port}" }
                        if (cont.isActive) cont.resume(host)
                    }
                })
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(null)
            }
        }

    private fun localIpv4(): Inet4Address? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val lp = cm.getLinkProperties(cm.activeNetwork) ?: return null
        return lp.linkAddresses.map { it.address }.filterIsInstance<Inet4Address>().firstOrNull { !it.isLoopbackAddress }
    }

    private suspend fun subnetScan(onHost: suspend (String) -> Unit) = coroutineScope {
        val ip = localIpv4() ?: return@coroutineScope
        val b = ip.address
        val prefix = "${b[0].toInt() and 0xFF}.${b[1].toInt() and 0xFF}.${b[2].toInt() and 0xFF}."
        val self = b[3].toInt() and 0xFF
        val permits = Semaphore(32)
        for (i in 1..254) {
            if (i == self) continue
            launch { permits.withPermit { onHost(prefix + i) } }
        }
    }
}
