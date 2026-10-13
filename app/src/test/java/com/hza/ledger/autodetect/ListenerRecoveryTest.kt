package com.hza.ledger.autodetect

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 监听服务自动恢复的触发条件测试。
 *
 * ## 背景
 *
 * 用户反馈「失灵了要去设置里关掉再开启，太麻烦了」。
 * 于是加了自动恢复：打开应用时若发现服务已停止，自动请求系统重绑。
 *
 * **但自动操作是有风险的** —— 如果误判，
 * 会在服务本来正常时把它重建，造成无谓的中断。
 * 所以触发条件必须严格，这里把边界一条条钉死。
 */
class ListenerRecoveryTest {

    private val now = 1_700_000_000_000L
    private val oneHour = 60L * 60 * 1000

    // ---------- 该触发的情况 ----------

    @Test
    fun `服务疑似停止且从未尝试过时应自动恢复`() {
        assertTrue(
            shouldAutoRecover(
                health = ListenerHealth.STALLED,
                lastAttemptAt = 0L,
                now = now,
                minIntervalMs = oneHour,
            ),
        )
    }

    @Test
    fun `服务疑似停止且距上次尝试超过间隔时应重试`() {
        assertTrue(
            shouldAutoRecover(
                health = ListenerHealth.STALLED,
                lastAttemptAt = now - 2 * oneHour,
                now = now,
                minIntervalMs = oneHour,
            ),
        )
    }

    @Test
    fun `恰好到达间隔时应允许重试`() {
        assertTrue(
            shouldAutoRecover(
                health = ListenerHealth.STALLED,
                lastAttemptAt = now - oneHour,
                now = now,
                minIntervalMs = oneHour,
            ),
        )
    }

    // ---------- 绝不该触发的情况（更重要） ----------

    @Test
    fun `服务正常时绝不能重绑`() {
        // ⚠️ 最关键的一条：服务好好的却去重建，会造成无谓的中断。
        // 自动操作误伤正常状态比不自动更糟。
        assertFalse(
            "服务正常时不该打扰",
            shouldAutoRecover(
                health = ListenerHealth.WORKING,
                lastAttemptAt = 0L,
                now = now,
                minIntervalMs = oneHour,
            ),
        )
    }

    @Test
    fun `从未收到过通知时不该自动恢复`() {
        // 新装用户还没触发过通知，属于正常状态，不是故障。
        // 若这里返回 true，新用户每次打开应用都会触发一次无意义的重绑。
        assertFalse(
            shouldAutoRecover(
                health = ListenerHealth.NEVER_WORKED,
                lastAttemptAt = 0L,
                now = now,
                minIntervalMs = oneHour,
            ),
        )
    }

    @Test
    fun `未授权时不该自动恢复`() {
        // 没授权的情况下重绑没有任何作用，只会让用户白等
        assertFalse(
            shouldAutoRecover(
                health = ListenerHealth.DISABLED,
                lastAttemptAt = 0L,
                now = now,
                minIntervalMs = oneHour,
            ),
        )
    }

    @Test
    fun `间隔内不应重复尝试`() {
        // 限流：避免频繁重建服务
        assertFalse(
            shouldAutoRecover(
                health = ListenerHealth.STALLED,
                lastAttemptAt = now - 10 * 60 * 1000,   // 10 分钟前
                now = now,
                minIntervalMs = oneHour,
            ),
        )
    }

    @Test
    fun `刚尝试过一秒不应重复`() {
        assertFalse(
            shouldAutoRecover(
                health = ListenerHealth.STALLED,
                lastAttemptAt = now - 1000,
                now = now,
                minIntervalMs = oneHour,
            ),
        )
    }

    // ---------- 异常输入 ----------

    @Test
    fun `系统时间被调早时仍应允许重试`() {
        // 用户手动改过系统时间，lastAttemptAt 可能"在未来"。
        // 此时不应永久锁死自动恢复。
        assertTrue(
            shouldAutoRecover(
                health = ListenerHealth.STALLED,
                lastAttemptAt = now + oneHour,
                now = now,
                minIntervalMs = oneHour,
            ),
        )
    }

    @Test
    fun `所有健康状态都被明确处理`() {
        // 防止将来新增状态时漏掉判断 —— 默认不该自动操作
        ListenerHealth.entries
            .filter { it != ListenerHealth.STALLED }
            .forEach { health ->
                assertFalse(
                    "$health 不应触发自动恢复",
                    shouldAutoRecover(health, 0L, now, oneHour),
                )
            }
    }
}