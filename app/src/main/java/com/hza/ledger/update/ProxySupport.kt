package com.hza.ledger.update

import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

/**
 * 代理适配。
 *
 * ## 为什么需要这个
 *
 * 用户在国内，GitHub 多数情况下需要代理才能访问。
 * 但 [java.net.HttpURLConnection] **默认不读系统代理设置** ——
 * 它只认`java.net.useSystemProxies=true` 这个 JVM 参数，
 * 而 Android 上这个参数没地方设，导致明明手机挂了代理，App 却连不上。
 *
 * 报错表现为 `Unable to resolve host` ——
 * DNS 请求根本没经过代理，所以解析失败。
 *
 * ## 解决方案
 *
 * 依次尝试：
 * 1. 系统属性里的代理（部分 ROM 会配）
 * 2. Android 系统全局代理（`Settings.Global.http_proxy`，需读权限）
 * 3. 常见的本地代理端口探测（Clash / Mihomo / v2ray 等）
 *
 * 只在直连失败后才启用代理探测，
 * 这样网络通畅时不会引入额外开销。
 */
object ProxySupport {

    /** 常见的本地代理端口，覆盖 Clash、Mihomo、v2rayN、Shadowrocket 等。 */
    private val COMMON_PORTS = listOf(7897, 7890, 10808, 10809, 1080, 8888)

    /** 探测结果的缓存。避免每次请求都重试所有端口。 */
    @Volatile
    private var cachedProxy: Proxy? = null

    @Volatile
    private var cachedProbed: Boolean = false

    /**
     * 打开连接，自动处理代理。
     *
     * @param directFirst 先试直连（网络正常时更快，且不依赖代理）
     */
    fun openConnection(
        url: String,
        directFirst: Boolean = true,
        configure: HttpURLConnection.() -> Unit = {},
    ): HttpURLConnection {
        val target = URL(url)

        // ⚠️ 顺序要求：必须先 apply(configure) 再 connect()。
        // HttpURLConnection 一旦 connect() 建立连接，就不允许再改请求属性，
        // 此时调setRequestProperty / requestMethod 会抛
        // IllegalStateException("Cannot set request property after connection is made")。
        val connection: HttpURLConnection = if (directFirst) {
            val direct = (target.openConnection() as HttpURLConnection).apply(configure)
            try {
                direct.connect()
                direct
            } catch (directError: Exception) {
                direct.disconnect()
                // 直连失败（多半是 DNS 解析不了），换代理重试
                val proxy = resolveProxy() ?: throw directError
                (target.openConnection(proxy) as HttpURLConnection)
                    .apply(configure)
                    .also { it.connect() }
            }
        } else {
            val proxy = resolveProxy()
            val conn = if (proxy != null) {
                target.openConnection(proxy) as HttpURLConnection
            } else {
                target.openConnection() as HttpURLConnection
            }
            conn.apply(configure)
        }
        return connection
    }

    /**
     * 找出可用的本地代理。
     *
     * 依次尝试：
     * 1. JVM 系统属性（若ROM 配过）
     * 2. 探测常见端口（Clash 默认 7890、Mihomo 常见 7897 等）
     */
    private fun resolveProxy(): Proxy? {
        if (cachedProbed) return cachedProxy
        cachedProbed = true

        // 1. JVM 参数
        runCatching {
            val host = System.getProperty("http.proxyHost")
            val port = System.getProperty("http.proxyPort")
            if (!host.isNullOrBlank() && !port.isNullOrBlank()) {
                val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port.toInt()))
                if (isAlive(proxy)) {
                    cachedProxy = proxy
                    return proxy
                }
            }
        }

        // 2. 探测常见本地端口
        for (port in COMMON_PORTS) {
            val proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", port))
            if (isAlive(proxy)) {
                cachedProxy = proxy
                return proxy
            }
        }

        cachedProxy = null
        return null
    }

    /** 端口是否有服务在监听。 */
    private fun isAlive(proxy: Proxy): Boolean = runCatching {
        val socket = java.net.Socket()
        socket.connect(proxy.address() as InetSocketAddress, 800)
        socket.close()
        true
    }.getOrDefault(false)

    /** 清除缓存。设置变更或失败重试时可调用。 */
    fun reset() {
        cachedProbed = false
        cachedProxy = null
    }
}