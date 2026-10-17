package com.wledcontroller.app

import android.content.Context
import com.wledcontroller.app.data.AppStore
import com.wledcontroller.app.data.DeviceManager
import com.wledcontroller.app.data.Discovery
import com.wledcontroller.app.data.WledApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Manual dependency container, created once by [WledApp]. */
class AppContainer(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val http = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .writeTimeout(6, TimeUnit.SECONDS)
        .build()

    private val wsClient = http.newBuilder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    val api = WledApi(http)
    val store = AppStore(context)
    val devices = DeviceManager(api, wsClient, store, scope)
    val discovery = Discovery(context, api)
}
