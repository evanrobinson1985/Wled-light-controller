package com.wledcontroller.app.sim

import com.wledcontroller.app.data.WledApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.Buffer
import okio.ByteString
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Where simulated controllers live. The app's HTTP client, WebSocket and DDP sender check here
 * first, so everything the app does with a real controller works the same on a simulated one.
 */
object SimHub {

    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    /** Socket callbacks run in order on one thread, like a real connection. */
    internal val socketThread = Executors.newSingleThreadExecutor { r -> Thread(r, "sim-socket").apply { isDaemon = true } }

    private val json = Json { ignoreUnknownKeys = true }
    private val byHost = ConcurrentHashMap<String, SimDevice>()
    private val _devices = MutableStateFlow<List<SimDevice>>(emptyList())
    val devices: StateFlow<List<SimDevice>> = _devices.asStateFlow()

    /** Bumped whenever any simulated controller's state changes. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    @Volatile private var file: File? = null
    private var ticker: Job? = null
    private var saveJob: Job? = null

    private fun key(host: String) = WledApi.normalizeHost(host).substringBefore('/').substringBefore(':')

    fun find(host: String?): SimDevice? = if (host == null || byHost.isEmpty()) null else byHost[key(host)]

    fun isSimulated(host: String?) = find(host) != null

    /** Loads the simulated controllers saved in [f] and starts them. */
    @Synchronized
    fun load(f: File) {
        unload()
        file = f
        val list = runCatching { json.decodeFromString(ListSerializer(SimDeviceData.serializer()), f.readText()) }.getOrNull().orEmpty()
        list.forEach { byHost[key(it.host)] = SimDevice(it, this) }
        publish()
        ticker = scope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                byHost.values.forEach { runCatching { it.tick(now) } }
                delay(200)
            }
        }
    }

    /** Stops and forgets every simulated controller (after saving them). */
    @Synchronized
    fun unload() {
        ticker?.cancel(); ticker = null
        saveJob?.cancel(); saveJob = null
        if (file != null) saveNow()
        byHost.values.forEach { d -> d.sockets.toList().forEach { it.cancel() } }
        byHost.clear()
        file = null
        publish()
    }

    @Synchronized
    fun add(data: SimDeviceData): SimDevice {
        val d = SimDevice(data, this)
        byHost[key(data.host)] = d
        publish(); scheduleSave()
        return d
    }

    @Synchronized
    fun remove(host: String) {
        byHost.remove(key(host))?.let { d -> d.sockets.toList().forEach { it.cancel() } }
        publish(); scheduleSave()
    }

    /** A free address for a new simulated controller (never a real network). */
    fun nextHost(): String {
        val used = byHost.keys.mapNotNull { it.substringAfterLast('.').toIntOrNull() }.toSet()
        return "127.77.0.${(1..254).first { it !in used }}"
    }

    private fun publish() {
        _devices.value = byHost.values.sortedBy { it.host.substringAfterLast('.').toIntOrNull() ?: 0 }
        _version.value++
    }

    internal fun changed(@Suppress("UNUSED_PARAMETER") d: SimDevice) {
        _version.value++
        scheduleSave()
    }

    private fun scheduleSave() {
        if (file == null) return
        saveJob?.cancel()
        saveJob = scope.launch { delay(1500); saveNow() }
    }

    @Synchronized
    fun saveNow() {
        val f = file ?: return
        val data = byHost.values.map { it.data() }
        runCatching {
            f.parentFile?.mkdirs()
            val tmp = File(f.path + ".tmp")
            tmp.writeText(json.encodeToString(ListSerializer(SimDeviceData.serializer()), data))
            tmp.renameTo(f)
        }
    }

    /** A master's network output arriving at another simulated controller. */
    internal fun forward(ip: List<Int>, part: IntArray, from: String) {
        find(ip.joinToString("."))?.realtime(part, find(from)?.name ?: from)
    }

    /** Takes a DDP frame meant for [host] if it's simulated. */
    fun deliverDdp(host: String, rgb: ByteArray): Boolean {
        val d = find(host) ?: return false
        d.realtime(rgb, "this phone")
        return true
    }

    /** Answers HTTP requests to simulated controllers; everything else goes to the network. */
    val interceptor = Interceptor { chain ->
        val req = chain.request()
        val d = find(req.url.host) ?: return@Interceptor chain.proceed(req)
        val body = req.body?.let { b -> Buffer().also { b.writeTo(it) }.readUtf8() }
        val path = req.url.encodedPath + (req.url.encodedQuery?.let { "?$it" } ?: "")
        val r = d.handle(req.method, path, body)
        Response.Builder()
            .request(req)
            .protocol(Protocol.HTTP_1_1)
            .code(r.code)
            .message(if (r.code == 200) "OK" else "Error")
            .body(r.body.toResponseBody(r.type.toMediaType()))
            .build()
    }

    /** A WebSocket to a simulated controller, or null when [host] is a real one. */
    fun openSocket(host: String, request: Request, listener: WebSocketListener): WebSocket? {
        val d = find(host) ?: return null
        return SimSocket(d, request, listener).also { it.start() }
    }
}

/** The simulated end of a WLED WebSocket: pushes state changes and takes commands. */
internal class SimSocket(
    private val device: SimDevice,
    private val req: Request,
    private val listener: WebSocketListener,
) : WebSocket {
    private val open = AtomicBoolean(true)

    fun start() {
        device.sockets += this
        SimHub.socketThread.execute {
            if (!open.get()) return@execute
            listener.onOpen(this, Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(101).message("Switching Protocols").build())
            listener.onMessage(this, device.welcome())
        }
    }

    fun push(msg: String) {
        if (open.get()) SimHub.socketThread.execute { if (open.get()) listener.onMessage(this, msg) }
    }

    override fun send(text: String): Boolean {
        if (!open.get()) return false
        SimHub.socketThread.execute { runCatching { device.handleSocket(text) } }
        return true
    }

    override fun send(bytes: ByteString) = false

    override fun close(code: Int, reason: String?): Boolean {
        if (!open.getAndSet(false)) return false
        device.sockets -= this
        SimHub.socketThread.execute { listener.onClosed(this, code, reason ?: "") }
        return true
    }

    override fun cancel() {
        if (!open.getAndSet(false)) return
        device.sockets -= this
        SimHub.socketThread.execute { listener.onFailure(this, IOException("Closed"), null) }
    }

    override fun queueSize() = 0L
    override fun request() = req
}
