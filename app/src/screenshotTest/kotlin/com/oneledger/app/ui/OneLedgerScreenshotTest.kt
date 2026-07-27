package com.oneledger.app.ui

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.oneledger.app.R
import com.oneledger.app.data.local.AccountEntity
import com.oneledger.app.data.local.AccountBalanceItem
import com.oneledger.app.data.local.BudgetEntity
import com.oneledger.app.data.local.SavingsPlanEntity
import com.oneledger.app.data.local.TransactionListItem
import com.oneledger.app.data.repository.defaultExpenseCategories
import com.oneledger.app.data.repository.defaultIncomeCategories
import com.oneledger.app.domain.model.AccountType
import com.oneledger.app.domain.model.SavingsMethod
import com.oneledger.app.domain.model.TransactionType
import com.oneledger.app.ui.components.AccountSelectionPreviewSurface
import com.oneledger.app.ui.components.QuickAddPreviewSurface
import com.oneledger.app.ui.components.AmountCalculatorState
import com.oneledger.app.ui.components.TransactionDateTimePreviewSurface
import com.oneledger.app.ui.screens.BudgetDetailScreen
import com.oneledger.app.ui.screens.BudgetEditorPreviewSurface
import com.oneledger.app.ui.screens.IncomeExpenseCalendarScreen
import com.oneledger.app.ui.theme.OneLedgerTheme
import com.oneledger.app.util.MonthWindow
import com.oneledger.app.util.shift
import java.util.Calendar

private const val PHONE = "spec:width=393dp,height=852dp,dpi=440"
private const val ICON_CANVAS = "spec:width=256dp,height=256dp,dpi=320"
private const val PREVIEW_NOW = 1_784_379_780_000L

@PreviewTest
@Preview(name = "App Icon · 居中", device = ICON_CANVAS)
@Composable
fun AppIconCenteredScreenshot() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE9EEF5)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.size(192.dp),
            shape = RoundedCornerShape(48.dp),
            color = Color.White,
            shadowElevation = 10.dp,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@PreviewTest
@Preview(name = "启动页标志 · 深色", device = ICON_CANVAS, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun DarkSplashMarkScreenshot() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0D10)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.splash_brand_mark),
            contentDescription = null,
            modifier = Modifier.size(192.dp),
        )
    }
}

@PreviewTest
@Preview(name = "账本 · 浅色", device = PHONE, uiMode = Configuration.UI_MODE_NIGHT_NO, showSystemUi = true)
@Composable
fun LedgerScreenshot() = PreviewFrame(Destination.LEDGER)

@PreviewTest
@Preview(name = "资产 · 浅色", device = PHONE, uiMode = Configuration.UI_MODE_NIGHT_NO, showSystemUi = true)
@Composable
fun AssetsScreenshot() = PreviewFrame(Destination.ASSETS)

@PreviewTest
@Preview(name = "存钱 · 浅色", device = PHONE, uiMode = Configuration.UI_MODE_NIGHT_NO, showSystemUi = true)
@Composable
fun SavingsScreenshot() = PreviewFrame(Destination.SAVINGS)

@PreviewTest
@Preview(name = "统计 · 浅色", device = PHONE, uiMode = Configuration.UI_MODE_NIGHT_NO, showSystemUi = true)
@Composable
fun StatisticsScreenshot() = PreviewFrame(Destination.STATISTICS)

@PreviewTest
@Preview(name = "预算详情 · 浅色", device = PHONE, uiMode = Configuration.UI_MODE_NIGHT_NO, showSystemUi = true)
@Composable
fun BudgetDetailsScreenshot() = PreviewLedgerPage(LedgerPage.BUDGET)

@PreviewTest
@Preview(name = "预算详情 · 未来月份 · 浅色", device = PHONE, uiMode = Configuration.UI_MODE_NIGHT_NO, showSystemUi = true)
@Composable
fun BudgetDetailsFutureMonthScreenshot() {
    OneLedgerTheme(darkTheme = false) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            BudgetDetailScreen(
                state = previewState(),
                onBack = {},
                onSaveBudget = { _, _, _ -> },
                nowMillis = PREVIEW_NOW,
                initialMonthStart = MonthWindow.current(PREVIEW_NOW).shift(6).start,
            )
        }
    }
}

@PreviewTest
@Preview(name = "预算编辑 · 浅色", device = PHONE, uiMode = Configuration.UI_MODE_NIGHT_NO, showSystemUi = true)
@Composable
fun BudgetEditorScreenshot() {
    OneLedgerTheme(darkTheme = false) {
        BudgetEditorPreviewSurface()
    }
}

@PreviewTest
@Preview(name = "收支日历 · 浅色", device = PHONE, uiMode = Configuration.UI_MODE_NIGHT_NO, showSystemUi = true)
@Composable
fun IncomeExpenseCalendarScreenshot() = PreviewLedgerPage(LedgerPage.CALENDAR)

@PreviewTest
@Preview(name = "收支日历 · 改选日期 · 浅色", device = PHONE, uiMode = Configuration.UI_MODE_NIGHT_NO, showSystemUi = true)
@Composable
fun IncomeExpenseCalendarSelectedDayScreenshot() {
    OneLedgerTheme(darkTheme = false) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            IncomeExpenseCalendarScreen(
                state = previewState(),
                onBack = {},
                onQuickAdd = {},
                nowMillis = PREVIEW_NOW,
                initialSelectedDayStart = daysAgo(PREVIEW_NOW, 6),
            )
        }
    }
}

@PreviewTest
@Preview(name = "收支日历 · 未来月份 · 浅色", device = PHONE, uiMode = Configuration.UI_MODE_NIGHT_NO, showSystemUi = true)
@Composable
fun IncomeExpenseCalendarFutureMonthScreenshot() {
    OneLedgerTheme(darkTheme = false) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            IncomeExpenseCalendarScreen(
                state = previewState(),
                onBack = {},
                onQuickAdd = {},
                nowMillis = PREVIEW_NOW,
                initialMonthOffset = 1,
            )
        }
    }
}

@PreviewTest
@Preview(name = "快速记账 · 浅色", device = PHONE, uiMode = Configuration.UI_MODE_NIGHT_NO, showSystemUi = true)
@Composable
fun QuickAddScreenshot() {
    val state = previewState()
    OneLedgerTheme(darkTheme = false) {
        QuickAddPreviewSurface(
            accounts = state.accounts,
            accountBalances = state.accountBalances,
            expenseCategories = state.expenseCategories,
            incomeCategories = state.incomeCategories,
            nowMillis = PREVIEW_NOW,
        )
    }
}

@PreviewTest
@Preview(name = "快速记账 · 餐饮二级分类 · 浅色", device = PHONE, uiMode = Configuration.UI_MODE_NIGHT_NO, showSystemUi = true)
@Composable
fun QuickAddExpandedCategoryScreenshot() {
    val state = previewState()
    OneLedgerTheme(darkTheme = false) {
        QuickAddPreviewSurface(
            accounts = state.accounts,
            accountBalances = state.accountBalances,
            expenseCategories = state.expenseCategories,
            incomeCategories = state.incomeCategories,
            nowMillis = PREVIEW_NOW,
            initialExpandedCategoryId = "expense-food",
        )
    }
}

@PreviewTest
@Preview(name = "快速记账 · 收入 · 浅色", device = PHONE, uiMode = Configuration.UI_MODE_NIGHT_NO, showSystemUi = true)
@Composable
fun QuickAddIncomeScreenshot() {
    val state = previewState()
    OneLedgerTheme(darkTheme = false) {
        QuickAddPreviewSurface(
            accounts = state.accounts,
            accountBalances = state.accountBalances,
            expenseCategories = state.expenseCategories,
            incomeCategories = state.incomeCategories,
            nowMillis = PREVIEW_NOW,
            initialType = TransactionType.INCOME,
        )
    }
}

@PreviewTest
@Preview(name = "快速记账 · 转账 · 浅色", device = PHONE, uiMode = Configuration.UI_MODE_NIGHT_NO, showSystemUi = true)
@Composable
fun QuickAddTransferScreenshot() {
    val state = previewState()
    OneLedgerTheme(darkTheme = false) {
        QuickAddPreviewSurface(
            accounts = state.accounts,
            accountBalances = state.accountBalances,
            expenseCategories = state.expenseCategories,
            incomeCategories = state.incomeCategories,
            nowMillis = PREVIEW_NOW,
            initialType = TransactionType.TRANSFER,
        )
    }
}

@PreviewTest
@Preview(name = "快速记账 · 账户选择 · 浅色", device = PHONE, uiMode = Configuration.UI_MODE_NIGHT_NO, showSystemUi = true)
@Composable
fun QuickAddAccountSelectionScreenshot() {
    val state = previewState()
    OneLedgerTheme(darkTheme = false) {
        AccountSelectionPreviewSurface(
            accounts = state.accounts,
            accountBalances = state.accountBalances,
        )
    }
}

@PreviewTest
@Preview(name = "快速记账 · 表达式 · 浅色", device = PHONE, uiMode = Configuration.UI_MODE_NIGHT_NO, showSystemUi = true)
@Composable
fun QuickAddExpressionScreenshot() {
    val state = previewState()
    val expression = AmountCalculatorState()
        .inputDigit("1")
        .inputDigit("2")
        .inputOperator("+")
        .inputDigit("1")
        .inputOperator("−")
        .inputDigit("2")
    OneLedgerTheme(darkTheme = false) {
        QuickAddPreviewSurface(
            accounts = state.accounts,
            accountBalances = state.accountBalances,
            expenseCategories = state.expenseCategories,
            incomeCategories = state.incomeCategories,
            nowMillis = PREVIEW_NOW,
            initialCalculatorState = expression,
        )
    }
}

@PreviewTest
@Preview(name = "编辑账单 · 深色", device = PHONE, uiMode = Configuration.UI_MODE_NIGHT_YES, showSystemUi = true)
@Composable
fun TransactionEditDarkScreenshot() = TransactionEditPreview(darkTheme = true)

@PreviewTest
@Preview(name = "编辑账单 · 浅色", device = PHONE, uiMode = Configuration.UI_MODE_NIGHT_NO, showSystemUi = true)
@Composable
fun TransactionEditLightScreenshot() = TransactionEditPreview(darkTheme = false)

@Composable
private fun TransactionEditPreview(darkTheme: Boolean) {
    val state = previewState()
    OneLedgerTheme(darkTheme = darkTheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            QuickAddPreviewSurface(
                accounts = state.accounts,
                accountBalances = state.accountBalances,
                expenseCategories = state.expenseCategories,
                incomeCategories = state.incomeCategories,
                nowMillis = PREVIEW_NOW,
                initialTransaction = state.transactions.first(),
            )
        }
    }
}

@PreviewTest
@Preview(name = "记账时间 · 浅色", device = PHONE, uiMode = Configuration.UI_MODE_NIGHT_NO, showSystemUi = true)
@Composable
fun TransactionDateTimeScreenshot() {
    OneLedgerTheme(darkTheme = false) {
        TransactionDateTimePreviewSurface(nowMillis = PREVIEW_NOW)
    }
}

@Composable
private fun PreviewFrame(destination: Destination) {
    OneLedgerTheme(darkTheme = false) {
        OneLedgerFrame(
            state = previewState(),
            destination = destination,
            onDestinationSelected = {},
            onQuickAdd = {},
            nowMillis = PREVIEW_NOW,
        )
    }
}

@Composable
private fun PreviewLedgerPage(page: LedgerPage) {
    OneLedgerTheme(darkTheme = false) {
        OneLedgerFrame(
            state = previewState(),
            destination = Destination.LEDGER,
            ledgerPage = page,
            onDestinationSelected = {},
            onLedgerPageChanged = {},
            onQuickAdd = {},
            nowMillis = PREVIEW_NOW,
        )
    }
}

private fun previewState(): OneLedgerUiState {
    val now = PREVIEW_NOW
    val month = MonthWindow.current(now)
    val bookId = "book-preview"
    val accounts = listOf(
        AccountEntity(
            id = "daily-card",
            bookId = bookId,
            name = "日常卡",
            subtitle = "储蓄账户",
            type = AccountType.BANK,
            colorHex = 0xFF4D7CFE,
            openingBalanceMinor = 328_600,
            includeInNetWorth = true,
            sortOrder = 0,
            createdAt = now,
            updatedAt = now,
        ),
        AccountEntity(
            id = "cash",
            bookId = bookId,
            name = "现金",
            subtitle = "随身钱包",
            type = AccountType.CASH,
            colorHex = 0xFFFFC247,
            openingBalanceMinor = 86_500,
            includeInNetWorth = true,
            sortOrder = 1,
            createdAt = now,
            updatedAt = now,
        ),
        AccountEntity(
            id = "credit",
            bookId = bookId,
            name = "信用卡",
            subtitle = "本期应还",
            type = AccountType.CREDIT,
            colorHex = 0xFFFF6B5F,
            openingBalanceMinor = -42_600,
            includeInNetWorth = true,
            sortOrder = 2,
            createdAt = now,
            updatedAt = now,
        ),
    )

    val categories = defaultExpenseCategories(bookId, now)
    val incomeCategories = defaultIncomeCategories(bookId, now)

    val transactions = listOf(
        transaction("t1", "expense-food", "餐饮", "food", 0xFFFF6B5F, 2_680, "daily-card", "日常卡", "咖啡和早餐", daysAgo(now, 0)),
        transaction("t2", "expense-transport", "交通", "transport", 0xFF4D7CFE, 1_400, "cash", "现金", "地铁", daysAgo(now, 0) - 3_600_000),
        transaction("t3", "expense-shopping", "购物", "shopping", 0xFFFFA94D, 18_900, "credit", "信用卡", "生活用品", daysAgo(now, 1)),
        transaction("t4", "expense-housing", "住房", "housing", 0xFF57D3A2, 7_500, "daily-card", "日常卡", "网络服务", daysAgo(now, 3)),
        TransactionListItem(
            id = "t5",
            type = TransactionType.INCOME,
            amountMinor = 520_000,
            categoryId = "income-salary",
            categoryName = "工资",
            iconKey = "salary",
            colorHex = 0xFF57D3A2,
            accountId = "daily-card",
            accountName = "日常卡",
            toAccountId = null,
            note = "七月工资",
            merchant = "",
            occurredAt = daysAgo(now, 5),
        ),
    )

    return OneLedgerUiState(
        activeBookId = bookId,
        accounts = accounts,
        accountBalances = listOf(
            AccountBalanceItem(accountId = "daily-card", currentBalanceMinor = 838_420),
            AccountBalanceItem(accountId = "cash", currentBalanceMinor = 85_100),
            AccountBalanceItem(accountId = "credit", currentBalanceMinor = -61_500),
        ),
        transactions = transactions,
        budgets = listOf(
            BudgetEntity(
                id = "budget-preview",
                bookId = bookId,
                periodStart = month.start,
                periodEnd = month.endExclusive,
                limitMinor = 500_000,
                createdAt = now,
                updatedAt = now,
            ),
            BudgetEntity(
                id = "budget-preview-food",
                bookId = bookId,
                categoryId = "expense-food",
                periodStart = month.start,
                periodEnd = month.endExclusive,
                limitMinor = 100_000,
                createdAt = now,
                updatedAt = now,
            ),
            BudgetEntity(
                id = "budget-preview-transport",
                bookId = bookId,
                categoryId = "expense-transport",
                periodStart = month.start,
                periodEnd = month.endExclusive,
                limitMinor = 80_000,
                createdAt = now,
                updatedAt = now,
            ),
        ),
        savingsPlans = listOf(
            SavingsPlanEntity(
                id = "plan-travel",
                bookId = bookId,
                name = "去看海",
                method = SavingsMethod.FLEXIBLE,
                targetMinor = 800_000,
                savedMinor = 356_000,
                colorHex = 0xFF57D3A2,
                startAt = daysAgo(now, 45),
                endAt = daysFrom(now, 120),
                isArchived = false,
                createdAt = now,
                updatedAt = now,
            ),
            SavingsPlanEntity(
                id = "plan-emergency",
                bookId = bookId,
                name = "应急备用金",
                method = SavingsMethod.FIXED,
                targetMinor = 2_000_000,
                savedMinor = 1_280_000,
                colorHex = 0xFF9B7BFF,
                startAt = daysAgo(now, 180),
                endAt = daysFrom(now, 185),
                isArchived = false,
                createdAt = now - 1,
                updatedAt = now,
            ),
        ),
        expenseCategories = categories,
        incomeCategories = incomeCategories,
    )
}

private fun transaction(
    id: String,
    categoryId: String,
    category: String,
    icon: String,
    color: Long,
    amount: Long,
    accountId: String,
    accountName: String,
    note: String,
    occurredAt: Long,
) = TransactionListItem(
    id = id,
    type = TransactionType.EXPENSE,
    amountMinor = amount,
    categoryId = categoryId,
    categoryName = category,
    iconKey = icon,
    colorHex = color,
    accountId = accountId,
    accountName = accountName,
    toAccountId = null,
    note = note,
    merchant = "",
    occurredAt = occurredAt,
)

private fun daysAgo(now: Long, days: Int): Long = Calendar.getInstance().apply {
    timeInMillis = now
    add(Calendar.DAY_OF_YEAR, -days)
}.timeInMillis

private fun daysFrom(now: Long, days: Int): Long = Calendar.getInstance().apply {
    timeInMillis = now
    add(Calendar.DAY_OF_YEAR, days)
}.timeInMillis
