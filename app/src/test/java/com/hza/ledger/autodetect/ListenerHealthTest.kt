package com.hza.ledger.autodetect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.7.1 回归测试：修复「前几天能用、今天忽然不行」无法定位的问题。
 *
 * ## 事故经过
 *
 * 用户反馈：前几天自动识别正常，今天付款不弹确认。
 *
 * 排查发现两个设计缺陷：
 *
 * **1. 诊断数据只在内存里**
 * `NotificationDiagnostics` 全是 `@Volatile var`，
 * App 一重启就全部清零。用户看到「收到 0 条」，
 * 却无法区分「从来没收到过」与「刚重启所以清零」——
 * 而这个问题跨越了好几天，中间应用重启过无数次。
 *
 * **2. 权限检测存在假信号**
 * `ListenerConnectionProbe` 一旦持久化过 `true`，
 * 服务被系统杀掉后冷启动仍显示「已授权」。
 * 于是出现「权限开着、收到 0 条」的矛盾现象。
 *
 * ## 修复思路
 *
 * 不再依赖布尔标志，改用**时间**判断：
 * 「上次收到通知是多久以前」是唯一不会说谎的信号 ——
 * 系统可以让权限开关保持开启，但没法伪造"刚刚收到过通知"。
 */
class ListenerHealthTest {

    private val now = 1_700_000_000_000L
    private val hour = 60L * 60 * 1000
    private val day = 24 * hour

    // ---------- 服务停止判定 ----------

    @Test
    fun `超过一天没收到通知应判定为疑似停止`() {
        assertTrue(
            "收到过通知但已隔 2 天，服务大概率被系统杀了",
            isListenerStalled(receivedCount = 100, lastReceivedAt = now - 2 * day, now = now),
        )
    }

    @Test
    fun `刚刚收到过通知不算停止`() {
        assertFalse(
            isListenerStalled(receivedCount = 100, lastReceivedAt = now - 60_000, now = now),
        )
    }

    @Test
    fun `从未收到过通知不算停止而是从未工作`() {
        // ⚠️ 关键区分：刚安装还没触发过通知，不是"停止"。
        // 若这里返回 true，新用户会立刻看到"服务已停止"的错误提示。
        assertFalse(
            "count 为 0 时应由 NEVER_WORKED 处理，不能报成停止",
            isListenerStalled(receivedCount = 0, lastReceivedAt = 0L, now = now),
        )
    }

    @Test
    fun `计数大于零但无时间戳时不应误判`() {
        // 防御：数据异常（只有计数没有时间）时不能报停止
        assertFalse(isListenerStalled(receivedCount = 5, lastReceivedAt = 0L, now = now))
    }

    @Test
    fun `刚好到阈值不应报停止`() {
        // 边界：必须"超过"阈值才算，等于不算 —— 避免临界抖动
        assertFalse(
            isListenerStalled(receivedCount = 1, lastReceivedAt = now - 24 * hour, now = now),
        )
        assertTrue(
            isListenerStalled(receivedCount = 1, lastReceivedAt = now - 24 * hour - 1, now = now),
        )
    }

    @Test
    fun `时间倒流时不应报停止`() {
        // 用户手动改了系统时间，lastReceivedAt 可能"在未来"
        assertFalse(isListenerStalled(receivedCount = 10, lastReceivedAt = now + day, now = now))
    }

    // ---------- 静默时长格式化 ----------

    @Test
    fun `无记录时显示从未收到过`() {
        assertEquals("从未收到过", formatSilentDuration(0L, now))
    }

    @Test
    fun `最近收到显示刚刚`() {
        assertEquals("刚刚", formatSilentDuration(now - 30_000, now))
    }

    @Test
    fun `分钟级显示`() {
        assertEquals("5 分钟前", formatSilentDuration(now - 5 * 60_000, now))
        assertEquals("59 分钟前", formatSilentDuration(now - 59 * 60_000, now))
    }

    @Test
    fun `小时级显示`() {
        assertEquals("1 小时前", formatSilentDuration(now - hour, now))
        assertEquals("23 小时前", formatSilentDuration(now - 23 * hour, now))
    }

    @Test
    fun `天级显示`() {
        // 这正是用户遇到的情形：权限显示开启，但上次收到通知已是 3 天前
        assertEquals("3 天前", formatSilentDuration(now - 3 * day, now))
        assertEquals("30 天前", formatSilentDuration(now - 30 * day, now))
    }

    @Test
    fun `时间倒流时不出现负数`() {
        assertEquals("刚刚", formatSilentDuration(now + day, now))
    }

    // ---------- 健康状态语义 ----------

    @Test
    fun `需要用户处理的状态只有停止与未授权`() {
        assertTrue(ListenerHealth.STALLED.needsAttention)
        assertTrue(ListenerHealth.DISABLED.needsAttention)
        assertFalse("正常运行不需要提示", ListenerHealth.WORKING.needsAttention)
        assertFalse(
            "刚安装还没触发过通知不算异常，不该弹提示打扰用户",
            ListenerHealth.NEVER_WORKED.needsAttention,
        )
    }

    @Test
    fun `每种状态都要有可操作的中文提示`() {
        // 提示文案必须存在且非空 —— 否则界面会出现空白，用户更困惑
        ListenerHealth.entries.forEach { health ->
            assertTrue("$health 缺少提示文案", health.hint().isNotBlank())
        }
    }

    @Test
    fun `停止状态的提示要给出具体操作`() {
        val hint = ListenerHealth.STALLED.hint()
        assertTrue("应告知用户如何处理", hint.contains("通知使用权"))
    }
}