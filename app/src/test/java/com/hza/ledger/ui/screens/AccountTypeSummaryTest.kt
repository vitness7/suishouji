package com.hza.ledger.ui.screens

import com.hza.ledger.data.local.AccountBalanceItem
import com.hza.ledger.data.local.AccountEntity
import com.hza.ledger.domain.model.AccountType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountTypeSummaryTest {

    private fun account(
        id: String,
        type: String,
        opening: Long = 0L,
        includeInNetWorth: Boolean = true,
    ) = AccountEntity(
        id = id,
        bookId = "book-test",
        name = id,
        subtitle = "",
        type = type,
        colorHex = 0,
        openingBalanceMinor = opening,
        includeInNetWorth = includeInNetWorth,
        sortOrder = 0,
        createdAt = 0L,
        updatedAt = 0L,
    )

    private fun balance(id: String, value: Long) =
        AccountBalanceItem(accountId = id, currentBalanceMinor = value)

    @Test
    fun `按类型分别汇总金额`() {
        val accounts = listOf(
            account("a1", AccountType.CASH),
            account("a2", AccountType.CASH),
            account("b1", AccountType.BANK),
        )
        val balances = listOf(
            balance("a1", 50000L),
            balance("a2", 30000L),
            balance("b1", 200000L),
        )

        val result = summarizeByAccountType(accounts, balances)

        val cash = result.first { it.type == AccountType.CASH }
        assertEquals("现金应为500+300=800元", 80000L, cash.totalMinor)
        assertEquals(2, cash.count)

        val bank = result.first { it.type == AccountType.BANK }
        assertEquals(200000L, bank.totalMinor)
        assertEquals(1, bank.count)
    }

    @Test
    fun `结果按总额降序排列`() {
        val accounts = listOf(
            account("a", AccountType.CASH),
            account("b", AccountType.BANK),
            account("c", AccountType.DIGITAL),
        )
        val balances = listOf(
            balance("a", 1000L),
            balance("b", 90000L),
            balance("c", 50000L),
        )

        val result = summarizeByAccountType(accounts, balances)

        assertEquals(listOf(AccountType.BANK, AccountType.DIGITAL, AccountType.CASH), result.map { it.type })
    }

    @Test
    fun `不计入净资产的账户被排除`() {
        val accounts = listOf(
            account("a", AccountType.CASH, includeInNetWorth = true),
            account("b", AccountType.CASH, includeInNetWorth = false),
        )
        val balances = listOf(balance("a", 10000L), balance("b", 99999L))

        val result = summarizeByAccountType(accounts, balances)

        assertEquals("被排除的账户不应计入", 10000L, result.first().totalMinor)
        assertEquals(1, result.first().count)
    }

    @Test
    fun `负余额账户参与汇总（信用卡欠款）`() {
        val accounts = listOf(
            account("a", AccountType.CREDIT),
            account("b", AccountType.BANK),
        )
        val balances = listOf(balance("a", -50000L), balance("b", 10000L))

        val result = summarizeByAccountType(accounts, balances)

        val credit = result.first { it.type == AccountType.CREDIT }
        assertEquals("欠款为负值", -50000L, credit.totalMinor)
        // 负数排在最后
        assertEquals(AccountType.BANK, result.first().type)
    }

    @Test
    fun `余额缺失时回退到期初余额`() {
        val accounts = listOf(account("a", AccountType.CASH, opening = 12345L))

        val result = summarizeByAccountType(accounts, emptyList())

        assertEquals(12345L, result.first().totalMinor)
    }

    @Test
    fun `空账户列表返回空结果`() {
        assertTrue(summarizeByAccountType(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun `五类账户都能正确汇总`() {
        val types = listOf(
            AccountType.CASH,
            AccountType.BANK,
            AccountType.DIGITAL,
            AccountType.CREDIT,
            AccountType.INVESTMENT,
        )
        val accounts = types.mapIndexed { index, type -> account("a$index", type) }
        val balances = types.mapIndexed { index, _ -> balance("a$index", (index + 1) * 10000L) }

        val result = summarizeByAccountType(accounts, balances)

        assertEquals("5 种类型应各自成组", 5, result.size)
        // 按金额降序：投资 50000 > 信用 40000 > 数字 30000 > 银行 20000 > 现金 10000
        assertEquals(50000L, result.first { it.type == AccountType.INVESTMENT }.totalMinor)
        assertEquals(10000L, result.first { it.type == AccountType.CASH }.totalMinor)
    }

    @Test
    fun `汇总总额与净资产口径一致`() {
        val accounts = listOf(
            account("a", AccountType.CASH),
            account("b", AccountType.CREDIT),
            account("c", AccountType.BANK),
        )
        val balances = listOf(
            balance("a", 100000L),
            balance("b", -30000L),
            balance("c", 50000L),
        )

        val byType = summarizeByAccountType(accounts, balances)
            .groupBy { it.type }
            .mapValues { it.value.sumOf { s -> s.totalMinor } }
        val netWorth = calculateNetWorth(accounts, balances).netWorthMinor

        // 净资产 = 正余额合计 - 负余额绝对值，按类型汇总后应当一致
        val assetsFromTypes = byType.filterValues { it > 0 }.values.sum()
        val liabilitiesFromTypes = -byType.filterValues { it < 0 }.values.sum()
        assertEquals(assetsFromTypes - liabilitiesFromTypes, netWorth)
    }
}
