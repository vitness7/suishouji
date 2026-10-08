package com.hza.ledger.update

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection

/**
 * v1.5.3 回归测试：修复「Cannot set request property after connection is made」。
 *
 * ## 事故经过
 *
 * v1.5.1 为支持代理，引入了 `ProxySupport.openConnection()`。
 * 实现上为了「先试直连」，在应用调用方配置之前就调用了 `connect()`，
 * 结果与调用方的 `configure` 回调顺序颠倒：
 *
 * ```
 * openConnection(...).apply { connectTimeout = 8000; requestMethod = "GET" }
 * ```
 *
 * 实际执行顺序却是：connect() → setRequestProperty()，
 * 而 HttpURLConnection 规定连接建立后不允许再改请求属性，
 * 于是抛 IllegalStateException，用户看到「检查更新失败」。
 *
 * ## 教训
 *
 * 「先试直连再配参数」这个设计本身就危险 ——
 * **配置必须先于连接建立**。这类顺序错误编译能过、测试测不出，
 * 只有真机运行时才炸。
 */
class ProxySupportTest {

    @Test
    fun `配置必须在连接建立之前应用`() {
        // 关键断言：apply(configure) 必须发生在 connect() 之前。
        // 若顺序颠倒，设置请求属性时会抛
        // IllegalStateException("Cannot set request property after connection is made")
        val connection = ProxySupport.openConnection(
            url = "https://example.com/never-actually-connects.invalid/path.txt",
            directFirst = true,
        ) {
            connectTimeout = 8000
            readTimeout = 8000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
        }

        assertNotNull("应返回连接对象", connection)
        assertTrue("应为 HttpURLConnection", connection is HttpURLConnection)
        assertEqualsSafely("GET", connection.requestMethod)
    }

    @Test
    fun `configure 回调应被完整应用`() {
        val connection = ProxySupport.openConnection(
            url = "https://example.com/test.invalid/x.txt",
            directFirst = false,
        ) {
            connectTimeout = 1234
            readTimeout = 5678
            requestMethod = "GET"
        }

        assertEqualsSafely("GET", connection.requestMethod)
        assertEqualsSafely(1234, connection.connectTimeout)
        assertEqualsSafely(5678, connection.readTimeout)
    }

    @Test
    fun `代理模块不应在配置阶段抛异常`() {
        // 回归：直接覆盖用户遇到的崩溃路径
        var thrown: Exception? = null
        try {
            ProxySupport.openConnection(
                url = "https://github.com/vitness7/suishouji-releases/releases/download/v1.5.2/update.txt",
                directFirst = true,
            ) {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
            }
        } catch (e: IllegalStateException) {
            thrown = e
        }

        assertTrue(
            "不应抛 Cannot set request property 异常，实际：${thrown?.message}",
            thrown == null,
        )
    }

    @Test
    fun `reset 应可清空缓存以便重新探测代理`() {
        // 切换网络环境后需要重新探测
        ProxySupport.reset()
        ProxySupport.reset()   // 重复调用不应崩
    }

    private fun assertEqualsSafely(expected: Any, actual: Any?) {
        if (expected != actual) {
            throw AssertionError("期望 $expected，实际 $actual")
        }
    }
}