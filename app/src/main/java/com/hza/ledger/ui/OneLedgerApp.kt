package com.hza.ledger.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hza.ledger.ui.components.QuickAddSheet
import com.hza.ledger.ui.screens.AssetsScreen
import com.hza.ledger.ui.screens.BudgetDetailScreen
import com.hza.ledger.ui.screens.IncomeExpenseCalendarScreen
import com.hza.ledger.ui.screens.LedgerScreen
import com.hza.ledger.ui.screens.AutoDetectSetupScreen
import com.hza.ledger.ui.screens.AutoDetectDiagnosticsScreen
import com.hza.ledger.ui.screens.PendingScreen
import com.hza.ledger.ui.screens.SettingsScreen
import com.hza.ledger.autodetect.ListenerRecovery
import com.hza.ledger.autodetect.PendingNotifier
import com.hza.ledger.autodetect.isNotificationListenerEnabled
import com.hza.ledger.autodetect.isSmsPermissionGranted
import com.hza.ledger.ui.screens.SavingsScreen
import com.hza.ledger.ui.screens.StatisticsScreen
import com.hza.ledger.ui.theme.BrandBlue
import com.hza.ledger.ui.theme.BrandBlueDeep
import com.hza.ledger.ui.theme.BrandBlueLight
import com.hza.ledger.ui.theme.ExpenseCoral
import com.hza.ledger.ui.theme.OneLedgerMotion
import com.hza.ledger.util.MoneyFormatter
import com.hza.ledger.util.MonthWindow
import kotlinx.coroutines.launch

internal enum class Destination(
    val label: String,
    val icon: ImageVector,
) {
    LEDGER("账本", Icons.AutoMirrored.Filled.ReceiptLong),
    ASSETS("资产", Icons.Default.AccountBalanceWallet),
    SAVINGS("存钱", Icons.Default.Savings),
    STATISTICS("统计", Icons.Default.BarChart),
}

/** 账户表单数据，用于把界面输入与 ViewModel 调用解耦。 */
internal data class AccountDraft(
    val id: String?,
    val name: String,
    val subtitle: String,
    val type: String,
    val openingBalanceMinor: Long,
    val includeInNetWorth: Boolean,
)

/** 存钱目标表单数据。 */
internal data class SavingsDraft(
    val id: String?,
    val name: String,
    val method: String,
    val targetMinor: Long,
    val colorHex: Long,
)

internal enum class LedgerPage {    OVERVIEW,
    BUDGET,
    CALENDAR,
    PENDING,
    AUTO_SETUP,
    DIAGNOSTICS,
    SETTINGS,
}

@Composable
fun OneLedgerApp(
    viewModel: OneLedgerViewModel,
    openPendingOnStart: Boolean = false,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var destination by rememberSaveable { mutableStateOf(Destination.LEDGER) }
    var ledgerPage by rememberSaveable { mutableStateOf(LedgerPage.OVERVIEW) }
    var showQuickAdd by rememberSaveable { mutableStateOf(false) }
    var editingTransactionId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingError by rememberSaveable { mutableStateOf<String?>(null) }
    val editingTransaction = state.transactions.firstOrNull { it.id == editingTransactionId }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current

    // 从通知点击进来时直接落到待确认页
    LaunchedEffect(openPendingOnStart) {
        if (openPendingOnStart) {
            destination = Destination.LEDGER
            ledgerPage = LedgerPage.PENDING
        }
    }

    // 打开应用时若发现监听服务已停止，自动尝试恢复。
    //
    // 用户对「失灵了得去系统设置里关掉再开启」的评价是「太麻烦」——
    // 手动操作本身就是负担。既然系统支持重绑，就不该把这件事交给用户。
    // 内部有限流（1 小时一次）且只在「曾正常、现在停了」时触发，
    // 不会影响正常工作的服务。
    LaunchedEffect(Unit) {
        ListenerRecovery.autoRecoverIfNeeded(context)
    }

    // 识别出候选时发送系统提醒
    LaunchedEffect(state.pendingTransactions.size) {
        if (state.pendingTransactions.isNotEmpty()) {
            val latest = state.pendingTransactions.firstOrNull()
            if (latest != null) {
                PendingNotifier.notifyPending(
                    context = context,
                    amountText = MoneyFormatter.format(latest.amountMinor),
                    pendingCount = state.pendingTransactions.size,
                )
            }
        }
    }

    OneLedgerFrame(
        state = state,
        destination = destination,
        ledgerPage = ledgerPage,
        onDestinationSelected = {
            destination = it
            ledgerPage = LedgerPage.OVERVIEW
        },
        onLedgerPageChanged = { ledgerPage = it },
        onQuickAdd = {
            editingTransactionId = null
            editingError = null
            showQuickAdd = true
        },
        onTransactionClick = { transactionId ->
            showQuickAdd = false
            editingError = null
            editingTransactionId = transactionId
        },
        onBudgetSaved = { window, categoryId, limitMinor ->
            viewModel.saveBudget(
                periodStart = window.start,
                periodEnd = window.endExclusive,
                limitMinor = limitMinor,
                categoryId = categoryId,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = if (categoryId == null) "总预算已更新" else "分类预算已更新",
                        duration = SnackbarDuration.Short,
                    )
                }
            }
        },
        onConfirmPending = { pending ->
            viewModel.confirmPending(pending)
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = "已记入账本",
                    duration = SnackbarDuration.Short,
                )
            }
        },
        onConfirmPendingEdited = { edit ->
            viewModel.confirmPendingEdited(edit)
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = "已按修改后的内容记入账本",
                    duration = SnackbarDuration.Short,
                )
            }
        },
        onDiscardPending = { viewModel.discardPending(it) },
        onDiscardAllPending = {
            viewModel.discardAllPending()
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = "已忽略全部待确认",
                    duration = SnackbarDuration.Short,
                )
            }
        },
        onOpenSettings = {
            destination = Destination.LEDGER
            ledgerPage = LedgerPage.SETTINGS
        },
        onOpenDiagnostics = {
            destination = Destination.LEDGER
            ledgerPage = LedgerPage.DIAGNOSTICS
        },
        onSaveAccountRequest = { draft, onDone ->
            viewModel.saveAccount(
                id = draft.id,
                name = draft.name,
                subtitle = draft.subtitle,
                type = draft.type,
                openingBalanceMinor = draft.openingBalanceMinor,
                includeInNetWorth = draft.includeInNetWorth,
                onDone = { saved ->
                    onDone(saved)
                    if (saved != null) {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (draft.id == null) "账户已创建" else "账户已更新",
                            )
                        }
                    }
                },
            )
        },
        onDeleteAccountRequest = { id, onResult ->
            viewModel.deleteAccount(id) { result ->
                onResult(result)
                if (result.isSuccess) {
                    scope.launch {
                        snackbarHostState.showSnackbar("账户已删除")
                    }
                }
            }
        },
        onAccountUsageCountRequest = { id, onResult ->
            viewModel.accountUsageCount(id, onResult)
        },
        onSaveSavingsRequest = { draft, onDone ->
            viewModel.saveSavingsPlan(
                id = draft.id,
                name = draft.name,
                method = draft.method,
                targetMinor = draft.targetMinor,
                colorHex = draft.colorHex,
                startAt = System.currentTimeMillis(),
                endAt = null,
                onDone = { saved ->
                    onDone(saved)
                    if (saved != null) {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (draft.id == null) "目标已创建" else "目标已更新",
                            )
                        }
                    }
                },
            )
        },
        onDeleteSavingsRequest = { id, onDone ->
            viewModel.deleteSavingsPlan(id) {
                onDone()
                scope.launch {
                    snackbarHostState.showSnackbar("目标已删除")
                }
            }
        },
        onDepositSavingsRequest = { planId, amount, accountId, note, onResult ->
            viewModel.depositToSavingsPlan(planId, amount, accountId, note) { result ->
                onResult(result)
                scope.launch {
                    snackbarHostState.showSnackbar(
                        if (result.isSuccess) "已存入" else "存入失败：${result.exceptionOrNull()?.message ?: "未知错误"}",
                    )
                }
            }
        },
        snackbarHostState = snackbarHostState,
    )

    if (showQuickAdd) {
        QuickAddSheet(
            accounts = state.accounts,
            accountBalances = state.accountBalances,
            expenseCategories = state.expenseCategories,
            incomeCategories = state.incomeCategories,
            onDismiss = { showQuickAdd = false },
            onSave = { transaction, keepOpen ->
                viewModel.addTransaction(transaction) { transactionId ->
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    if (!keepOpen) showQuickAdd = false
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = if (keepOpen) "已保存，可以继续记账" else "已记入日常账本",
                            actionLabel = "撤销",
                            duration = SnackbarDuration.Short,
                        )
                        if (result == SnackbarResult.ActionPerformed) viewModel.undoTransaction(transactionId)
                    }
                }
            },
        )
    }

    if (editingTransaction != null) {
        QuickAddSheet(
            accounts = state.accounts,
            accountBalances = state.accountBalances,
            expenseCategories = state.expenseCategories,
            incomeCategories = state.incomeCategories,
            initialTransaction = editingTransaction,
            errorMessage = editingError,
            onDismiss = {
                editingError = null
                editingTransactionId = null
            },
            onSave = { transaction, _ ->
                editingError = null
                viewModel.updateTransaction(editingTransaction.id, transaction) { result ->
                    if (result.isSuccess) {
                        editingTransactionId = null
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = "账单已更新",
                                duration = SnackbarDuration.Short,
                            )
                        }
                    } else {
                        editingError = "账单未保存，请重试。已填写内容不会丢失。"
                    }
                }
            },
            onDelete = {
                val transactionId = editingTransaction.id
                editingError = null
                viewModel.deleteTransaction(transactionId) { result ->
                    if (result.isSuccess) {
                        editingTransactionId = null
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        scope.launch {
                            val snackbarResult = snackbarHostState.showSnackbar(
                                message = "账单已删除",
                                actionLabel = "撤销",
                                duration = SnackbarDuration.Long,
                            )
                            if (snackbarResult == SnackbarResult.ActionPerformed) {
                                viewModel.restoreTransaction(transactionId) { restoreResult ->
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = if (restoreResult.isSuccess) "账单已恢复" else "账单未恢复，请重试",
                                            duration = SnackbarDuration.Short,
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        editingError = "账单未删除，请重试。原账单仍然安全。"
                    }
                }
            },
        )
    }
}

@Composable
internal fun OneLedgerFrame(
    state: OneLedgerUiState,
    destination: Destination,
    onDestinationSelected: (Destination) -> Unit,
    onQuickAdd: () -> Unit,
    ledgerPage: LedgerPage = LedgerPage.OVERVIEW,
    onLedgerPageChanged: (LedgerPage) -> Unit = {},
    onTransactionClick: (String) -> Unit = {},
    onBudgetSaved: (MonthWindow, String?, Long) -> Unit = { _, _, _ -> },
    onConfirmPending: (com.hza.ledger.data.local.PendingTransactionEntity) -> Unit = {},
    onConfirmPendingEdited: (com.hza.ledger.pending.PendingEdit) -> Unit = {},
    onDiscardPending: (com.hza.ledger.data.local.PendingTransactionEntity) -> Unit = {},
    onDiscardAllPending: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenDiagnostics: () -> Unit = {},
    onSaveAccountRequest: (AccountDraft, (String?) -> Unit) -> Unit = { _, _ -> },
    onDeleteAccountRequest: (String, (Result<Unit>) -> Unit) -> Unit = { _, _ -> },
    onAccountUsageCountRequest: (String, (Long) -> Unit) -> Unit = { _, _ -> },
    onSaveSavingsRequest: (SavingsDraft, (String?) -> Unit) -> Unit = { _, _ -> },
    onDeleteSavingsRequest: (String, () -> Unit) -> Unit = { _, _ -> },
    onDepositSavingsRequest: (String, Long, String, String, (Result<Unit>) -> Unit) -> Unit =
        { _, _, _, _, _ -> },
    nowMillis: Long = System.currentTimeMillis(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    BackHandler(enabled = destination == Destination.LEDGER && ledgerPage != LedgerPage.OVERVIEW) {
        onLedgerPageChanged(LedgerPage.OVERVIEW)
    }
    val tabStateHolder = rememberSaveableStateHolder()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
        ) {
            AnimatedContent(
                targetState = destination,
                transitionSpec = {
                    fadeIn(
                        animationSpec = tween(
                            durationMillis = OneLedgerMotion.ContentEnterMillis,
                            delayMillis = 28,
                            easing = FastOutSlowInEasing,
                        ),
                    ) togetherWith fadeOut(
                        animationSpec = tween(OneLedgerMotion.ContentExitMillis),
                    )
                },
                contentKey = { it },
                label = "main-destination",
            ) { activeDestination ->
                tabStateHolder.SaveableStateProvider(activeDestination.name) {
                    when (activeDestination) {
                        Destination.LEDGER -> LedgerDestination(
                            state = state,
                            ledgerPage = ledgerPage,
                            onLedgerPageChanged = onLedgerPageChanged,
                            onQuickAdd = onQuickAdd,
                            onTransactionClick = onTransactionClick,
                            onBudgetSaved = onBudgetSaved,
                            onConfirmPending = onConfirmPending,
                            onConfirmPendingEdited = onConfirmPendingEdited,
                            onDiscardPending = onDiscardPending,
                            onDiscardAllPending = onDiscardAllPending,
                            onOpenSettings = onOpenSettings,
                            onOpenDiagnostics = onOpenDiagnostics,
                            onSaveAccountRequest = onSaveAccountRequest,
                            onDeleteAccountRequest = onDeleteAccountRequest,
                            onAccountUsageCountRequest = onAccountUsageCountRequest,
                            onSaveSavingsRequest = onSaveSavingsRequest,
                            onDeleteSavingsRequest = onDeleteSavingsRequest,
                            onDepositSavingsRequest = onDepositSavingsRequest,
                            nowMillis = nowMillis,
                        )
                        Destination.ASSETS -> AssetsScreen(
                            state = state,
                            nowMillis = nowMillis,
                            onSaveAccount = { id, name, subtitle, type, balance, includeInNetWorth, onDone ->
                                onSaveAccountRequest(
                                    AccountDraft(
                                        id = id,
                                        name = name,
                                        subtitle = subtitle,
                                        type = type,
                                        openingBalanceMinor = balance,
                                        includeInNetWorth = includeInNetWorth,
                                    ),
                                    onDone,
                                )
                            },
                            onDeleteAccount = onDeleteAccountRequest,
                            onAccountUsageCount = onAccountUsageCountRequest,
                        )
                        Destination.SAVINGS -> SavingsScreen(
                            state = state,
                            onSavePlan = { id, name, method, targetMinor, colorHex, onDone ->
                                onSaveSavingsRequest(
                                    SavingsDraft(
                                        id = id,
                                        name = name,
                                        method = method,
                                        targetMinor = targetMinor,
                                        colorHex = colorHex,
                                    ),
                                    onDone,
                                )
                            },
                            onDeletePlan = onDeleteSavingsRequest,
                            onDeposit = onDepositSavingsRequest,
                        )
                        Destination.STATISTICS -> StatisticsScreen(state, nowMillis = nowMillis)
                    }
                }
            }

            AnimatedVisibility(
                visible = destination == Destination.LEDGER && ledgerPage == LedgerPage.OVERVIEW,
                modifier = Modifier.align(Alignment.BottomEnd),
                enter = fadeIn(tween(OneLedgerMotion.ContentEnterMillis)) +
                    slideInVertically(
                        animationSpec = spring(
                            dampingRatio = OneLedgerMotion.NoBounceDamping,
                            stiffness = OneLedgerMotion.NavigationStiffness,
                        ),
                        initialOffsetY = { it / 3 },
                    ),
                exit = fadeOut(tween(OneLedgerMotion.ContentExitMillis)) +
                    slideOutVertically(
                        animationSpec = spring(
                            dampingRatio = OneLedgerMotion.NoBounceDamping,
                            stiffness = OneLedgerMotion.NavigationStiffness,
                        ),
                        targetOffsetY = { it / 3 },
                    ),
                label = "quick-add-visibility",
            ) {
                LiquidGlassAddButton(
                    onClick = onQuickAdd,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(end = 16.dp, bottom = 86.dp),
                )
            }

            AnimatedVisibility(
                visible = destination != Destination.LEDGER || ledgerPage == LedgerPage.OVERVIEW,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn(tween(OneLedgerMotion.ContentEnterMillis)) +
                    slideInVertically(
                        animationSpec = spring(
                            dampingRatio = OneLedgerMotion.NoBounceDamping,
                            stiffness = OneLedgerMotion.NavigationStiffness,
                        ),
                        initialOffsetY = { it / 2 },
                    ),
                exit = fadeOut(tween(OneLedgerMotion.ContentExitMillis)) +
                    slideOutVertically(
                        animationSpec = spring(
                            dampingRatio = OneLedgerMotion.NoBounceDamping,
                            stiffness = OneLedgerMotion.NavigationStiffness,
                        ),
                        targetOffsetY = { it / 2 },
                    ),
                label = "bottom-bar-visibility",
            ) {
                OneLedgerBottomBar(
                    selected = destination,
                    onSelected = onDestinationSelected,
                    pendingBadge = state.pendingTransactions.size,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun LedgerDestination(
    state: OneLedgerUiState,
    ledgerPage: LedgerPage,
    onLedgerPageChanged: (LedgerPage) -> Unit,
    onQuickAdd: () -> Unit,
    onTransactionClick: (String) -> Unit,
    onBudgetSaved: (MonthWindow, String?, Long) -> Unit,
    onConfirmPending: (com.hza.ledger.data.local.PendingTransactionEntity) -> Unit,
    onConfirmPendingEdited: (com.hza.ledger.pending.PendingEdit) -> Unit,
    onDiscardPending: (com.hza.ledger.data.local.PendingTransactionEntity) -> Unit,
    onDiscardAllPending: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onSaveAccountRequest: (AccountDraft, (String?) -> Unit) -> Unit,
    onDeleteAccountRequest: (String, (Result<Unit>) -> Unit) -> Unit,
    onAccountUsageCountRequest: (String, (Long) -> Unit) -> Unit,
    onSaveSavingsRequest: (SavingsDraft, (String?) -> Unit) -> Unit,
    onDeleteSavingsRequest: (String, () -> Unit) -> Unit,
    onDepositSavingsRequest: (String, Long, String, String, (Result<Unit>) -> Unit) -> Unit,
    nowMillis: Long,
) {
    var budgetMonthStart by rememberSaveable {
        mutableLongStateOf(MonthWindow.current(nowMillis).start)
    }
    AnimatedContent(
        targetState = ledgerPage,
        transitionSpec = {
            val isForward = targetState != LedgerPage.OVERVIEW
            val enterOffset: (Int) -> Int = { width -> if (isForward) width / 12 else -width / 12 }
            val exitOffset: (Int) -> Int = { width -> if (isForward) -width / 12 else width / 12 }
            val positionSpec = spring<IntOffset>(
                dampingRatio = OneLedgerMotion.NoBounceDamping,
                stiffness = OneLedgerMotion.NavigationStiffness,
            )
            (fadeIn(tween(OneLedgerMotion.NavigationEnterMillis, easing = FastOutSlowInEasing)) +
                slideInHorizontally(positionSpec, enterOffset)) togetherWith
                (fadeOut(tween(OneLedgerMotion.NavigationExitMillis)) +
                    slideOutHorizontally(positionSpec, exitOffset))
        },
        contentKey = { it },
        label = "ledger-page",
    ) { activePage ->
        when (activePage) {
            LedgerPage.OVERVIEW -> LedgerScreen(
                state = state,
                nowMillis = nowMillis,
                onBudgetClick = { month ->
                    budgetMonthStart = month.start
                    onLedgerPageChanged(LedgerPage.BUDGET)
                },
                onCalendarClick = { onLedgerPageChanged(LedgerPage.CALENDAR) },
                onTransactionClick = onTransactionClick,
                onPendingClick = { onLedgerPageChanged(LedgerPage.PENDING) },
                onSettingsClick = { onLedgerPageChanged(LedgerPage.SETTINGS) },
                // 主页的异常提示卡跳这里，让用户立刻看到原因与恢复方法
                onDiagnosticsClick = onOpenDiagnostics,
            )
            LedgerPage.BUDGET -> BudgetDetailScreen(
                state = state,
                onBack = { onLedgerPageChanged(LedgerPage.OVERVIEW) },
                onSaveBudget = onBudgetSaved,
                nowMillis = nowMillis,
                initialMonthStart = budgetMonthStart,
            )
            LedgerPage.CALENDAR -> IncomeExpenseCalendarScreen(
                state = state,
                onBack = { onLedgerPageChanged(LedgerPage.OVERVIEW) },
                onQuickAdd = onQuickAdd,
                onTransactionClick = onTransactionClick,
                nowMillis = nowMillis,
            )
            LedgerPage.PENDING -> PendingScreen(
                pending = state.pendingTransactions,
                categoryNames = (state.expenseCategories + state.incomeCategories)
                    .associate { it.id to it.name },
                // 编辑时需要按类型切换分类，所以两种分类都要传
                expenseCategories = state.expenseCategories,
                incomeCategories = state.incomeCategories,
                accounts = state.accounts,
                onConfirm = onConfirmPending,
                onConfirmEdited = onConfirmPendingEdited,
                onDiscard = onDiscardPending,
                onDiscardAll = onDiscardAllPending,
                onOpenSetup = { onLedgerPageChanged(LedgerPage.AUTO_SETUP) },
            )
            LedgerPage.AUTO_SETUP -> AutoDetectSetupScreen(
                notificationEnabled = isNotificationListenerEnabled(),
                smsGranted = isSmsPermissionGranted(),
                onBack = { onLedgerPageChanged(LedgerPage.PENDING) },
                onOpenDiagnostics = { onLedgerPageChanged(LedgerPage.DIAGNOSTICS) },
                onOpenSettings = { onLedgerPageChanged(LedgerPage.SETTINGS) },
            )
            LedgerPage.DIAGNOSTICS -> AutoDetectDiagnosticsScreen(
                notificationEnabled = isNotificationListenerEnabled(),
                smsGranted = isSmsPermissionGranted(),
                onBack = { onLedgerPageChanged(LedgerPage.AUTO_SETUP) },
            )
            LedgerPage.SETTINGS -> SettingsScreen(
                onBack = { onLedgerPageChanged(LedgerPage.OVERVIEW) },
                onOpenDiagnostics = { onLedgerPageChanged(LedgerPage.DIAGNOSTICS) },
            )
        }
    }
}

@Composable
private fun LiquidGlassAddButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = if (pressed) {
            snap()
        } else {
            spring(
                dampingRatio = OneLedgerMotion.NoBounceDamping,
                stiffness = OneLedgerMotion.PressStiffness,
            )
        },
        label = "quick-add-press",
    )
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier
            .size(58.dp)
            .scale(scale),
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.38f)),
        shadowElevation = 16.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        listOf(
                            BrandBlueLight.copy(alpha = 0.92f),
                            BrandBlue,
                            BrandBlueDeep,
                        ),
                    ),
                    shape = shape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(24.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.28f), Color.Transparent),
                        ),
                        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                    ),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(18.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, BrandBlueDeep.copy(alpha = 0.34f)),
                        ),
                        RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp),
                    ),
            )
            Icon(
                Icons.Default.Add,
                contentDescription = "快速记账",
                tint = Color.White,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

@Composable
private fun OneLedgerBottomBar(
    selected: Destination,
    onSelected: (Destination) -> Unit,
    pendingBadge: Int = 0,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = Destination.entries.indexOf(selected)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(66.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.8f),
                shape = RoundedCornerShape(24.dp),
            ),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.97f),
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 12.dp,
    ) {
        BoxWithConstraints(modifier = Modifier.padding(4.dp)) {
            val itemWidth = maxWidth / Destination.entries.size
            val indicatorOffset by animateDpAsState(
                targetValue = itemWidth * selectedIndex,
                animationSpec = spring(
                    dampingRatio = OneLedgerMotion.NoBounceDamping,
                    stiffness = OneLedgerMotion.NavigationStiffness,
                ),
                label = "bottom-indicator-position",
            )
            Box(
                modifier = Modifier
                    .offset { IntOffset(indicatorOffset.roundToPx(), 0) }
                    .width(itemWidth)
                    .fillMaxHeight()
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        RoundedCornerShape(20.dp),
                    ),
            )
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Destination.entries.forEach { destination ->
                    BottomBarItem(
                        destination = destination,
                        selected = destination == selected,
                        onClick = { onSelected(destination) },
                        // 仅账本页显示待确认角标，避免与其他页面混淆
                        badge = if (destination == Destination.LEDGER) pendingBadge else 0,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomBarItem(
    destination: Destination,
    selected: Boolean,
    onClick: () -> Unit,
    badge: Int = 0,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = if (pressed) {
            snap()
        } else {
            spring(
                dampingRatio = OneLedgerMotion.NoBounceDamping,
                stiffness = OneLedgerMotion.PressStiffness,
            )
        },
        label = "bottom-item-press",
    )
    val content by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(OneLedgerMotion.SelectionMillis),
        label = "bottom-item-content",
    )
    Column(
        modifier = modifier
            .height(58.dp)
            .scale(scale)
            .semantics { this.selected = selected }
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(destination.icon, contentDescription = destination.label, tint = content, modifier = Modifier.size(20.dp))

            // 待确认数量角标：识别出候选时主动提示，不必用户主动翻账本
            if (badge > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .sizeIn(minWidth = 16.dp, minHeight = 16.dp)
                        .background(ExpenseCoral, CircleShape)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (badge > 99) "99+" else "$badge",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            destination.label,
            color = content,
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .width(16.dp)
                .height(2.dp)
                .background(content.copy(alpha = if (selected) 1f else 0f), CircleShape),
        )
    }
}
