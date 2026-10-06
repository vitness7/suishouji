package com.hza.ledger.ui.components

import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.DialogProperties
import com.hza.ledger.data.local.AccountEntity
import com.hza.ledger.data.local.AccountBalanceItem
import com.hza.ledger.data.local.CategoryEntity
import com.hza.ledger.data.local.TransactionListItem
import com.hza.ledger.domain.model.NewTransaction
import com.hza.ledger.domain.model.TransactionType
import com.hza.ledger.ui.theme.BrandBlue
import com.hza.ledger.ui.theme.BrandBlueLight
import com.hza.ledger.ui.theme.ExpenseCoral
import com.hza.ledger.ui.theme.IncomeMint
import com.hza.ledger.ui.theme.OneLedgerMotion
import com.hza.ledger.ui.theme.SavingsAmber
import com.hza.ledger.util.CalendarDateCell
import com.hza.ledger.util.MoneyFormatter
import com.hza.ledger.util.MonthWindow
import com.hza.ledger.util.calendarDateCells
import com.hza.ledger.util.monthLabel
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

internal enum class KeyboardMode {
    NONE,
    CUSTOM_NUMBER,
    CUSTOM_TO_SYSTEM,
    SYSTEM_IME,
    SYSTEM_TO_CUSTOM,
}

internal enum class QuickAddFocusField {
    AMOUNT,
    NOTE,
}

private enum class TransferAccountTarget {
    FROM,
    TO,
}

internal data class QuickAddKeyboardState(
    val mode: KeyboardMode,
    val currentFocusField: QuickAddFocusField,
    val imeVisible: Boolean,
) {
    val customKeyboardVisible: Boolean
        get() = mode == KeyboardMode.CUSTOM_NUMBER ||
            mode == KeyboardMode.CUSTOM_TO_SYSTEM ||
            mode == KeyboardMode.SYSTEM_TO_CUSTOM

    val customKeyboardEnabled: Boolean
        get() = mode == KeyboardMode.CUSTOM_NUMBER || mode == KeyboardMode.SYSTEM_TO_CUSTOM

    val noteInputEnabled: Boolean
        get() = mode == KeyboardMode.CUSTOM_TO_SYSTEM || mode == KeyboardMode.SYSTEM_IME

    val systemKeyboardVisible: Boolean
        get() = (mode == KeyboardMode.CUSTOM_TO_SYSTEM || mode == KeyboardMode.SYSTEM_IME) && imeVisible
}

private val QuickAddCustomKeyboardHeight = 231.dp

/** Resilience fallback for hardware/floating keyboards that never publish an IME inset target. */
private const val ImeStartFallbackMillis = 240L

private object KeyboardKeyStyle {
    val Height = 50.dp
    val CornerRadius = 15.dp
    val GlyphFontSize = 26.sp
    val GlyphLineHeight = 30.sp
    val DecimalFontSize = 30.sp
    val DeleteIconSize = 22.dp
    val FunctionIconSize = 18.dp
    val FunctionFontSize = 16.sp
    val GlyphWeight = FontWeight.SemiBold
    val SelectedGlyphWeight = FontWeight.Bold
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(
    accounts: List<AccountEntity>,
    accountBalances: List<AccountBalanceItem>,
    expenseCategories: List<CategoryEntity>,
    incomeCategories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (NewTransaction, Boolean) -> Unit,
    initialTransaction: TransactionListItem? = null,
    onDelete: (() -> Unit)? = null,
    errorMessage: String? = null,
    nowMillis: Long = System.currentTimeMillis(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        QuickAddContent(
            accounts = accounts,
            accountBalances = accountBalances,
            expenseCategories = expenseCategories,
            incomeCategories = incomeCategories,
            onDismiss = onDismiss,
            onSave = onSave,
            initialTransaction = initialTransaction,
            onDelete = onDelete,
            errorMessage = errorMessage,
            nowMillis = nowMillis,
            initialTypeOverride = null,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.97f),
        )
    }
}

@Suppress("DEPRECATION")
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickAddContent(
    accounts: List<AccountEntity>,
    accountBalances: List<AccountBalanceItem>,
    expenseCategories: List<CategoryEntity>,
    incomeCategories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (NewTransaction, Boolean) -> Unit,
    initialTransaction: TransactionListItem?,
    onDelete: (() -> Unit)?,
    errorMessage: String?,
    nowMillis: Long,
    modifier: Modifier = Modifier,
    initialCalculatorState: AmountCalculatorState? = null,
    initialTypeOverride: String? = null,
    initialExpandedCategoryId: String? = null,
) {
    val editKey = initialTransaction?.id
    val isEditing = initialTransaction != null
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val imeTargetInsets = WindowInsets.imeAnimationTarget
    val amountFocusRequester = remember(editKey) { FocusRequester() }
    val noteFocusRequester = remember(editKey) { FocusRequester() }
    val hostView = LocalView.current
    val initialType = initialTypeOverride ?: initialTransaction?.type ?: TransactionType.EXPENSE
    val initialCategories = when (initialType) {
        TransactionType.EXPENSE -> expenseCategories
        TransactionType.INCOME -> incomeCategories
        else -> emptyList()
    }
    val initialCategoryState = defaultQuickAddCategoryState(initialCategories)
    var type by rememberSaveable(editKey) {
        mutableStateOf(initialType)
    }
    var categoryStateType by remember(editKey) { mutableStateOf(initialType) }
    var calculatorState by rememberSaveable(
        editKey,
        stateSaver = AmountCalculatorState.Saver,
    ) {
        mutableStateOf(
            initialCalculatorState ?: AmountCalculatorState.fromAmount(
                initialTransaction?.amountMinor?.amountInputValue().orEmpty(),
            ),
        )
    }
    var note by rememberSaveable(editKey) { mutableStateOf(initialTransaction?.note.orEmpty()) }
    var requestedFocusField by rememberSaveable(editKey) { mutableStateOf(QuickAddFocusField.AMOUNT) }
    var currentFocusField by remember(editKey) { mutableStateOf(requestedFocusField) }
    var inputRequestVersion by remember(editKey) { mutableIntStateOf(0) }
    var keyboardMode by remember(editKey) {
        mutableStateOf(
            if (requestedFocusField == QuickAddFocusField.AMOUNT) {
                KeyboardMode.CUSTOM_NUMBER
            } else {
                KeyboardMode.SYSTEM_IME
            },
        )
    }
    val customKeyboardAnimation = remember(editKey) {
        Animatable(if (requestedFocusField == QuickAddFocusField.AMOUNT) 1f else 0f)
    }
    var selectedCategoryId by rememberSaveable(editKey) {
        mutableStateOf(
            initialTransaction?.categoryId ?: initialCategoryState.selectedCategoryId,
        )
    }
    var selectedAccountId by rememberSaveable(editKey) {
        mutableStateOf(
            initialTransaction?.accountId ?: if (initialType == TransactionType.TRANSFER) {
                null
            } else {
                accounts.firstOrNull()?.id
            },
        )
    }
    var selectedToAccountId by rememberSaveable(editKey) { mutableStateOf(initialTransaction?.toAccountId) }
    var expandedCategoryId by rememberSaveable(editKey) {
        mutableStateOf(
            initialExpandedCategoryId ?: if (isEditing) {
                null
            } else {
                initialCategoryState.expandedCategoryId
            },
        )
    }
    var accountPickerTarget by rememberSaveable(editKey) { mutableStateOf<TransferAccountTarget?>(null) }
    var occurredAt by rememberSaveable(editKey) { mutableLongStateOf(initialTransaction?.occurredAt ?: nowMillis) }
    var showDateTimePicker by rememberSaveable(editKey) { mutableStateOf(false) }
    var showDeleteConfirm by rememberSaveable(editKey) { mutableStateOf(false) }
    val categories = when (type) {
        TransactionType.EXPENSE -> expenseCategories
        TransactionType.INCOME -> incomeCategories
        else -> emptyList()
    }
    val accent = when (type) {
        TransactionType.EXPENSE -> ExpenseCoral
        TransactionType.INCOME -> IncomeMint
        else -> SavingsAmber
    }
    val balanceByAccountId = remember(accountBalances) {
        accountBalances.associate { it.accountId to it.currentBalanceMinor }
    }
    val parsedAmount = MoneyFormatter.parseToMinor(calculatorState.settledAmountOrNull.orEmpty())
    val canSave = parsedAmount != null && parsedAmount > 0 && selectedAccountId != null && when (type) {
        TransactionType.TRANSFER -> selectedToAccountId != null && selectedToAccountId != selectedAccountId
        else -> selectedCategoryId != null
    }
    val systemKeyboardVisible = WindowInsets.isImeVisible
    val keyboardState = QuickAddKeyboardState(
        mode = keyboardMode,
        currentFocusField = currentFocusField,
        imeVisible = systemKeyboardVisible,
    )

    DisposableEffect(hostView) {
        val dialogWindow = ((hostView.parent as? DialogWindowProvider) ?: (hostView as? DialogWindowProvider))?.window
        val previousSoftInputMode = dialogWindow?.attributes?.softInputMode
        // Let the platform resize the dialog for the system IME exactly once. The Compose dock below
        // reserves only the custom keypad and therefore cannot double-count the IME on MIUI devices.
        dialogWindow?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        onDispose {
            if (previousSoftInputMode != null) dialogWindow.setSoftInputMode(previousSoftInputMode)
        }
    }

    fun requestInput(field: QuickAddFocusField, force: Boolean = true) {
        val changed = requestedFocusField != field
        requestedFocusField = field
        keyboardMode = when (field) {
            QuickAddFocusField.NOTE -> if (
                keyboardMode == KeyboardMode.SYSTEM_IME || keyboardMode == KeyboardMode.CUSTOM_TO_SYSTEM
            ) {
                keyboardMode
            } else {
                KeyboardMode.CUSTOM_TO_SYSTEM
            }

            QuickAddFocusField.AMOUNT -> if (
                keyboardMode == KeyboardMode.CUSTOM_NUMBER || keyboardMode == KeyboardMode.SYSTEM_TO_CUSTOM
            ) {
                keyboardMode
            } else {
                KeyboardMode.SYSTEM_TO_CUSTOM
            }
        }
        if (changed || force) inputRequestVersion += 1
    }

    LaunchedEffect(editKey, requestedFocusField, inputRequestVersion) {
        when (requestedFocusField) {
            QuickAddFocusField.NOTE -> {
                keyboardMode = KeyboardMode.CUSTOM_TO_SYSTEM
                noteFocusRequester.requestFocus()
                keyboardController?.show()

                // Keep the outgoing keypad in place until Android announces the incoming IME. The
                // bounded fallback is used only when a hardware/floating keyboard has no inset target.
                withTimeoutOrNull(ImeStartFallbackMillis) {
                    snapshotFlow {
                        imeTargetInsets.getBottom(density)
                    }.first { it > 0 }
                }
                customKeyboardAnimation.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = OneLedgerMotion.NoBounceDamping,
                        stiffness = OneLedgerMotion.KeyboardStiffness,
                    ),
                )
                if (requestedFocusField == QuickAddFocusField.NOTE) {
                    keyboardMode = KeyboardMode.SYSTEM_IME
                }
            }

            QuickAddFocusField.AMOUNT -> {
                keyboardMode = KeyboardMode.SYSTEM_TO_CUSTOM
                amountFocusRequester.requestFocus()
                keyboardController?.hide()
                customKeyboardAnimation.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = OneLedgerMotion.NoBounceDamping,
                        stiffness = OneLedgerMotion.KeyboardStiffness,
                    ),
                )
                if (requestedFocusField == QuickAddFocusField.AMOUNT) {
                    keyboardMode = KeyboardMode.CUSTOM_NUMBER
                }
            }
        }
    }

    LaunchedEffect(type, categories) {
        val typeChanged = categoryStateType != type
        categoryStateType = type
        when {
            type == TransactionType.TRANSFER -> expandedCategoryId = null
            categories.isNotEmpty() &&
                (typeChanged || categories.none { it.id == selectedCategoryId }) -> {
                val defaultState = defaultQuickAddCategoryState(categories)
                selectedCategoryId = defaultState.selectedCategoryId
                expandedCategoryId = defaultState.expandedCategoryId
            }
        }
    }
    LaunchedEffect(type) {
        accountPickerTarget = null
        if (type == TransactionType.TRANSFER && !isEditing) {
            selectedAccountId = null
            selectedToAccountId = null
        }
    }
    LaunchedEffect(accounts, type) {
        if (type == TransactionType.TRANSFER) {
            if (accounts.none { it.id == selectedAccountId }) selectedAccountId = null
            if (accounts.none { it.id == selectedToAccountId } || selectedToAccountId == selectedAccountId) {
                selectedToAccountId = null
            }
        } else if (accounts.none { it.id == selectedAccountId }) {
            selectedAccountId = accounts.firstOrNull()?.id
        }
    }

    fun inputDigit(value: String) {
        calculatorState = calculatorState.inputDigit(value)
    }

    fun inputOperator(operator: String) {
        calculatorState = calculatorState.inputOperator(operator)
    }

    fun backspace() {
        calculatorState = calculatorState.backspace()
    }

    fun save(keepOpen: Boolean) {
        if (!canSave) return
        onSave(
            NewTransaction(
                type = type,
                amountMinor = requireNotNull(parsedAmount),
                categoryId = if (type == TransactionType.TRANSFER) null else selectedCategoryId,
                accountId = requireNotNull(selectedAccountId),
                toAccountId = if (type == TransactionType.TRANSFER) selectedToAccountId else null,
                note = note,
                occurredAt = occurredAt,
            ),
            keepOpen,
        )
        if (keepOpen) {
            calculatorState = AmountCalculatorState()
            note = ""
            occurredAt = nowMillis
            if (type != TransactionType.TRANSFER) {
                val defaultState = defaultQuickAddCategoryState(categories)
                selectedCategoryId = defaultState.selectedCategoryId
                expandedCategoryId = defaultState.expandedCategoryId
            }
        }
    }

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 10.dp),
    ) {
        QuickAddHeader(type = type, isEditing = isEditing, onTypeSelected = { type = it }, onDismiss = onDismiss)
        Spacer(Modifier.height(10.dp))
        AnimatedContent(
            targetState = type,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            transitionSpec = {
                fadeIn(tween(OneLedgerMotion.ContentEnterMillis)) togetherWith
                    fadeOut(tween(OneLedgerMotion.ContentExitMillis))
            },
            contentKey = { it },
            label = "quick-add-type-content",
        ) { activeType ->
            if (activeType == TransactionType.TRANSFER) {
                TransferAccountSelector(
                    accounts = accounts,
                    accountBalances = balanceByAccountId,
                    fromAccountId = selectedAccountId,
                    toAccountId = selectedToAccountId,
                    onFromClick = { accountPickerTarget = TransferAccountTarget.FROM },
                    onToClick = { accountPickerTarget = TransferAccountTarget.TO },
                    onSwap = {
                        val previousFrom = selectedAccountId
                        selectedAccountId = selectedToAccountId
                        selectedToAccountId = previousFrom
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                val activeCategories = if (activeType == TransactionType.EXPENSE) expenseCategories else incomeCategories
                CategoryGrid(
                    categories = activeCategories,
                    accent = if (activeType == TransactionType.EXPENSE) ExpenseCoral else IncomeMint,
                    selectedCategoryId = selectedCategoryId,
                    onSelected = { selectedCategoryId = it },
                    expandedCategoryId = expandedCategoryId,
                    onExpandedCategoryChanged = { expandedCategoryId = it },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (type == TransactionType.TRANSFER) {
            TransferOptionRow()
        } else {
            QuickOptionRow(
                accounts = accounts,
                selectedAccountId = selectedAccountId,
                onAccountSelected = { selectedAccountId = it },
                onDateClick = { showDateTimePicker = true },
            )
        }
        Spacer(Modifier.height(8.dp))
        AmountPanel(
            displayAmount = calculatorState.displayText.ifBlank { "0.00" },
            accent = accent,
            note = note,
            occurredAt = occurredAt,
            noteInputEnabled = keyboardState.noteInputEnabled,
            amountFocusRequester = amountFocusRequester,
            noteFocusRequester = noteFocusRequester,
            onNoteChange = { if (it.length <= 60) note = it },
            onNoteFocusChanged = { focused ->
                if (focused) {
                    currentFocusField = QuickAddFocusField.NOTE
                    if (requestedFocusField != QuickAddFocusField.NOTE) {
                        requestInput(QuickAddFocusField.NOTE, force = false)
                    }
                }
            },
            onAmountFocusChanged = { focused ->
                if (focused) {
                    currentFocusField = QuickAddFocusField.AMOUNT
                    if (requestedFocusField != QuickAddFocusField.AMOUNT) {
                        requestInput(QuickAddFocusField.AMOUNT, force = false)
                    }
                }
            },
            onAmountClick = { requestInput(QuickAddFocusField.AMOUNT) },
            onNoteRequest = { requestInput(QuickAddFocusField.NOTE) },
            onNoteDone = { requestInput(QuickAddFocusField.AMOUNT) },
            onDateClick = { showDateTimePicker = true },
        )
        if (!errorMessage.isNullOrBlank()) {
            Text(
                text = errorMessage,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 7.dp, start = 4.dp, end = 4.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                color = ExpenseCoral,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        QuickAddKeyboardDock(
            state = keyboardState,
            customKeyboardProgress = { customKeyboardAnimation.value },
            accent = accent,
            pendingOperator = calculatorState.waitingOperator?.symbol,
            expressionMode = calculatorState.isExpressionMode,
            canEvaluate = calculatorState.canEvaluate,
            canSave = canSave,
            isEditing = isEditing,
            onDigit = ::inputDigit,
            onOperator = ::inputOperator,
            onBackspace = ::backspace,
            onEvaluate = { calculatorState = calculatorState.evaluate() },
            onSaveAgain = { save(true) },
            onDone = { save(false) },
            onDelete = { showDeleteConfirm = true },
        )
    }

    if (showDateTimePicker) {
        TransactionDateTimeDialog(
            initialMillis = occurredAt,
            onDismiss = { showDateTimePicker = false },
            onConfirm = {
                occurredAt = it
                showDateTimePicker = false
            },
        )
    }

    if (showDeleteConfirm && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除这笔账单？") },
            text = { Text("账单会从余额、预算和统计中移除，删除后可在提示中撤销。") },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                ) {
                    Text("删除账单", color = ExpenseCoral, fontWeight = FontWeight.Bold)
                }
            },
        )
    }

    accountPickerTarget?.let { target ->
        AccountSelectionSheet(
            accounts = accounts,
            accountBalances = balanceByAccountId,
            selectedAccountId = when (target) {
                TransferAccountTarget.FROM -> selectedAccountId
                TransferAccountTarget.TO -> selectedToAccountId
            },
            disabledAccountId = when (target) {
                TransferAccountTarget.FROM -> selectedToAccountId
                TransferAccountTarget.TO -> selectedAccountId
            },
            onDismiss = { accountPickerTarget = null },
            onSelected = { accountId ->
                when (target) {
                    TransferAccountTarget.FROM -> selectedAccountId = accountId
                    TransferAccountTarget.TO -> selectedToAccountId = accountId
                }
                accountPickerTarget = null
            },
        )
    }
}

@Composable
private fun QuickAddHeader(
    type: String,
    isEditing: Boolean,
    onTypeSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PressableSurface(
            onClick = onDismiss,
            modifier = Modifier.size(44.dp),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(15.dp),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = if (isEditing) "关闭账单编辑" else "关闭记账",
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
                .padding(3.dp),
        ) {
            TransactionTypeTab("支出", TransactionType.EXPENSE, type, ExpenseCoral, onTypeSelected)
            TransactionTypeTab("收入", TransactionType.INCOME, type, IncomeMint, onTypeSelected)
            TransactionTypeTab("转账", TransactionType.TRANSFER, type, SavingsAmber, onTypeSelected)
        }
        Spacer(Modifier.weight(1f))
        Box(modifier = Modifier.width(44.dp), contentAlignment = Alignment.CenterEnd) {
            Text(
                if (isEditing) "编辑" else "账本",
                color = BrandBlueLight,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun TransactionTypeTab(
    label: String,
    value: String,
    selected: String,
    accent: Color,
    onSelected: (String) -> Unit,
) {
    val background by animateColorAsState(
        targetValue = if (value == selected) accent.copy(alpha = 0.20f) else Color.Transparent,
        animationSpec = tween(140),
        label = "quick-type-color",
    )
    PressableSurface(
        onClick = { onSelected(value) },
        color = background,
        shape = RoundedCornerShape(15.dp),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
            color = if (value == selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (value == selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun CategoryGrid(
    categories: List<CategoryEntity>,
    accent: Color,
    selectedCategoryId: String?,
    onSelected: (String) -> Unit,
    expandedCategoryId: String?,
    onExpandedCategoryChanged: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tree = remember(categories) { quickAddCategoryTree(categories) }
    val rows = remember(tree.roots) { tree.roots.chunked(5) }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (rows.isEmpty()) {
            item("empty-categories") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 42.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "暂无可用分类",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        itemsIndexed(
            items = rows,
            key = { _, row -> row.joinToString(separator = "|", transform = CategoryEntity::id) },
        ) { _, row ->
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    row.forEach { category ->
                        val children = tree.childrenOf(category.id)
                        CategoryCell(
                            category = category,
                            selected = category.id == selectedCategoryId,
                            hasChildren = children.isNotEmpty(),
                            accent = accent,
                            onClick = {
                                onSelected(category.id)
                                onExpandedCategoryChanged(
                                    toggledQuickAddCategory(
                                        expandedCategoryId = expandedCategoryId,
                                        tappedCategoryId = category.id,
                                        hasChildren = children.isNotEmpty(),
                                    ),
                                )
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(5 - row.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }

                val expandedInRow = row.firstOrNull { it.id == expandedCategoryId }
                AnimatedContent(
                    targetState = expandedInRow,
                    transitionSpec = {
                        val symmetricDuration = OneLedgerMotion.ContentEnterMillis
                        val enter = fadeIn(
                            animationSpec = tween(
                                durationMillis = symmetricDuration,
                                easing = FastOutSlowInEasing,
                            ),
                        )
                        val exit = fadeOut(
                            animationSpec = tween(
                                durationMillis = symmetricDuration,
                                easing = FastOutSlowInEasing,
                            ),
                        )
                        (enter togetherWith exit) using SizeTransform(
                            clip = true,
                            sizeAnimationSpec = { _, _ ->
                                tween(
                                    durationMillis = symmetricDuration,
                                    easing = FastOutSlowInEasing,
                                )
                            },
                        )
                    },
                    contentAlignment = Alignment.TopStart,
                    contentKey = { it?.id },
                    label = "quick-add-subcategories",
                ) { parent ->
                    parent?.let {
                        SubcategoryPanel(
                            categories = tree.childrenOf(it.id),
                            selectedCategoryId = selectedCategoryId,
                            accent = accent,
                            onSelected = onSelected,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryCell(
    category: CategoryEntity,
    selected: Boolean,
    hasChildren: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PressableSurface(
        onClick = onClick,
        modifier = modifier,
        color = Color.Transparent,
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.size(50.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(
                            if (selected) accent else MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape,
                        )
                        .border(
                            1.dp,
                            if (selected) Color.White.copy(alpha = 0.30f)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.24f),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        quickAddCategoryIcon(category),
                        contentDescription = category.name,
                        tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(23.dp),
                    )
                }
                if (hasChildren) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(16.dp)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.background, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.MoreHoriz,
                            contentDescription = "${category.name}包含二级分类",
                            tint = MaterialTheme.colorScheme.background,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }
            Text(
                category.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 3.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SubcategoryPanel(
    categories: List<CategoryEntity>,
    selectedCategoryId: String?,
    accent: Color,
    onSelected: (String) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        shape = RoundedCornerShape(18.dp),
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(categories, key = CategoryEntity::id) { category ->
                val selected = category.id == selectedCategoryId
                PressableSurface(
                    onClick = { onSelected(category.id) },
                    modifier = Modifier.width(70.dp),
                    color = if (selected) accent.copy(alpha = 0.16f) else Color.Transparent,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(
                                    if (selected) accent.copy(alpha = 0.18f)
                                    else MaterialTheme.colorScheme.surface,
                                    CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                quickAddCategoryIcon(category),
                                contentDescription = category.name,
                                tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Text(
                            category.name,
                            modifier = Modifier.padding(top = 3.dp),
                            color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TransferAccountSelector(
    accounts: List<AccountEntity>,
    accountBalances: Map<String, Long>,
    fromAccountId: String?,
    toAccountId: String?,
    onFromClick: () -> Unit,
    onToClick: () -> Unit,
    onSwap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fromAccount = accounts.firstOrNull { it.id == fromAccountId }
    val toAccount = accounts.firstOrNull { it.id == toAccountId }
    Column(
        modifier = modifier.padding(horizontal = 2.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TransferAccountCard(
            placeholder = "请选择转出账户",
            account = fromAccount,
            balanceMinor = fromAccount?.let { accountBalances[it.id] ?: it.openingBalanceMinor },
            onClick = onFromClick,
        )
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            PressableSurface(
                onClick = onSwap,
                enabled = fromAccount != null || toAccount != null,
                modifier = Modifier.size(44.dp),
                color = SavingsAmber.copy(alpha = 0.16f),
                shape = CircleShape,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.SwapVert,
                        contentDescription = "交换转出和转入账户",
                        tint = SavingsAmber,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
        TransferAccountCard(
            placeholder = "请选择转入账户",
            account = toAccount,
            balanceMinor = toAccount?.let { accountBalances[it.id] ?: it.openingBalanceMinor },
            onClick = onToClick,
        )
    }
}

@Composable
private fun TransferAccountCard(
    placeholder: String,
    account: AccountEntity?,
    balanceMinor: Long?,
    onClick: () -> Unit,
) {
    PressableSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(86.dp)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (account == null) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(SavingsAmber.copy(alpha = 0.16f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.AccountBalanceWallet,
                        contentDescription = null,
                        tint = SavingsAmber,
                        modifier = Modifier.size(21.dp),
                    )
                }
                Text(
                    placeholder,
                    modifier = Modifier.padding(start = 13.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                AccountGlyph(
                    accountType = account.type,
                    color = colorFromLong(account.colorHex),
                    modifier = Modifier.size(44.dp),
                )
                Column(
                    modifier = Modifier
                        .padding(start = 13.dp)
                        .weight(1f),
                ) {
                    Text(
                        account.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        account.subtitle.ifBlank { account.type },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    MoneyFormatter.format(balanceMinor ?: account.openingBalanceMinor),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun TransferOptionRow() {
    val options = listOf(
        Triple(Icons.Default.Redeem, "优惠", IncomeMint),
        Triple(Icons.Default.Image, "图片", SavingsAmber),
        Triple(Icons.AutoMirrored.Filled.Label, "标签", BrandBlueLight),
        Triple(Icons.Default.Percent, "手续费", ExpenseCoral),
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options, key = { it.second }) { (icon, label, color) ->
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(14.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(19.dp)
                            .background(color, RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                    Text(
                        label,
                        modifier = Modifier.padding(start = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountSelectionSheet(
    accounts: List<AccountEntity>,
    accountBalances: Map<String, Long>,
    selectedAccountId: String?,
    disabledAccountId: String?,
    onDismiss: () -> Unit,
    onSelected: (String?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 9.dp, bottom = 7.dp)
                    .width(42.dp)
                    .height(4.dp)
                    .background(MaterialTheme.colorScheme.outline, CircleShape),
            )
        },
    ) {
        AccountSelectionContent(
            accounts = accounts,
            accountBalances = accountBalances,
            selectedAccountId = selectedAccountId,
            disabledAccountId = disabledAccountId,
            onDismiss = onDismiss,
            onSelected = onSelected,
        )
    }
}

@Composable
private fun AccountSelectionContent(
    accounts: List<AccountEntity>,
    accountBalances: Map<String, Long>,
    selectedAccountId: String?,
    disabledAccountId: String?,
    onDismiss: () -> Unit,
    onSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val visibleAccounts = remember(accounts, searchQuery) {
        filteredQuickAddAccounts(accounts, searchQuery)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
            .padding(start = 16.dp, end = 16.dp, bottom = 18.dp),
    ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PressableSurface(
                    onClick = onDismiss,
                    modifier = Modifier.size(44.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(15.dp),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "关闭账户选择",
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Text(
                    "选择账户",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    modifier = Modifier.width(92.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = BrandBlueLight.copy(alpha = 0.72f),
                        modifier = Modifier.size(17.dp),
                    )
                    Text(
                        "添加账户",
                        color = BrandBlueLight.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(18.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it.take(30) },
                        modifier = Modifier
                            .padding(start = 10.dp)
                            .weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Search,
                        ),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (searchQuery.isBlank()) {
                                    Text(
                                        "请输入账户名称",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 110.dp, max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item("no-account") {
                    AccountSelectionRow(
                        account = null,
                        balanceMinor = null,
                        selected = selectedAccountId == null,
                        enabled = true,
                        onClick = { onSelected(null) },
                    )
                }
                items(visibleAccounts, key = AccountEntity::id) { account ->
                    AccountSelectionRow(
                        account = account,
                        balanceMinor = accountBalances[account.id] ?: account.openingBalanceMinor,
                        selected = account.id == selectedAccountId,
                        enabled = account.id != disabledAccountId,
                        onClick = { onSelected(account.id) },
                    )
                }
                if (accounts.isNotEmpty() && visibleAccounts.isEmpty()) {
                    item("empty-search-result") {
                        Text(
                            "没有匹配的账户",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 22.dp),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
    }
}

@Composable
internal fun AccountSelectionPreviewSurface(
    accounts: List<AccountEntity>,
    accountBalances: List<AccountBalanceItem>,
) {
    val balanceByAccountId = remember(accountBalances) {
        accountBalances.associate { it.accountId to it.currentBalanceMinor }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.16f)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 9.dp, bottom = 7.dp)
                        .width(42.dp)
                        .height(4.dp)
                        .background(MaterialTheme.colorScheme.outline, CircleShape),
                )
                AccountSelectionContent(
                    accounts = accounts,
                    accountBalances = balanceByAccountId,
                    selectedAccountId = null,
                    disabledAccountId = null,
                    onDismiss = {},
                    onSelected = {},
                )
            }
        }
    }
}

@Composable
private fun AccountSelectionRow(
    account: AccountEntity?,
    balanceMinor: Long?,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    PressableSurface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        color = if (selected) BrandBlue.copy(alpha = 0.10f) else Color.Transparent,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp)
                .graphicsLayer { alpha = if (enabled) 1f else 0.38f },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (account == null) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(SavingsAmber.copy(alpha = 0.20f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.AccountBalanceWallet,
                        contentDescription = null,
                        tint = SavingsAmber,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Text(
                    "不选择账户",
                    modifier = Modifier
                        .padding(start = 13.dp)
                        .weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            } else {
                AccountGlyph(
                    accountType = account.type,
                    color = colorFromLong(account.colorHex),
                    modifier = Modifier.size(46.dp),
                )
                Column(
                    modifier = Modifier
                        .padding(start = 13.dp)
                        .weight(1f),
                ) {
                    Text(
                        account.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        account.subtitle.ifBlank { account.type },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    MoneyFormatter.format(balanceMinor ?: account.openingBalanceMinor),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun QuickOptionRow(
    accounts: List<AccountEntity>,
    selectedAccountId: String?,
    onAccountSelected: (String) -> Unit,
    onDateClick: () -> Unit,
) {
    val selectedIndex = accounts.indexOfFirst { it.id == selectedAccountId }.coerceAtLeast(0)
    val account = accounts.getOrNull(selectedIndex)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            QuickOptionChip(
                icon = Icons.Default.AccountBalanceWallet,
                label = account?.name ?: "选择账户",
                accent = BrandBlue,
                onClick = {
                    if (accounts.isNotEmpty()) onAccountSelected(accounts[(selectedIndex + 1) % accounts.size].id)
                },
            )
        }
        item {
            QuickOptionChip(Icons.Default.CalendarMonth, "修改时间", BrandBlueLight, onClick = onDateClick)
        }
    }
}

@Composable
private fun QuickOptionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accent: Color,
    onClick: () -> Unit,
) {
    PressableSurface(onClick = onClick, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(14.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            Text(label, modifier = Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun AmountPanel(
    displayAmount: String,
    accent: Color,
    note: String,
    occurredAt: Long,
    noteInputEnabled: Boolean,
    amountFocusRequester: FocusRequester,
    noteFocusRequester: FocusRequester,
    onNoteChange: (String) -> Unit,
    onNoteFocusChanged: (Boolean) -> Unit,
    onAmountFocusChanged: (Boolean) -> Unit,
    onAmountClick: () -> Unit,
    onNoteRequest: () -> Unit,
    onNoteDone: () -> Unit,
    onDateClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f), RoundedCornerShape(22.dp)),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 11.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "¥",
                    modifier = Modifier.alignByBaseline(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = accent,
                )
                AmountValueButton(
                    displayAmount = displayAmount,
                    accent = accent,
                    focusRequester = amountFocusRequester,
                    onFocusChanged = onAmountFocusChanged,
                    onClick = onAmountClick,
                    modifier = Modifier
                        .weight(1f)
                        .alignByBaseline(),
                )
            }
            Spacer(Modifier.height(7.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)))
            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PressableSurface(
                    onClick = onDateClick,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(17.dp))
                        Text(
                            SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(occurredAt)),
                            modifier = Modifier.padding(start = 5.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .padding(start = 10.dp)
                        .weight(1f),
                ) {
                    BasicTextField(
                        value = note,
                        onValueChange = onNoteChange,
                        readOnly = !noteInputEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusProperties { canFocus = noteInputEnabled }
                            .focusRequester(noteFocusRequester)
                            .onFocusChanged { onNoteFocusChanged(it.isFocused) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { onNoteDone() },
                        ),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                if (note.isBlank()) {
                                    Text(
                                        "点击填写备注",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                    if (!noteInputEnabled) {
                        val noteInteractionSource = remember { MutableInteractionSource() }
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable(
                                    interactionSource = noteInteractionSource,
                                    indication = null,
                                    onClick = onNoteRequest,
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AmountValueButton(
    displayAmount: String,
    accent: Color,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scrollState = rememberScrollState()
    val pressed by interactionSource.collectIsPressedAsState()
    val pressProgress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = if (pressed) snap() else spring(
            dampingRatio = OneLedgerMotion.NoBounceDamping,
            stiffness = OneLedgerMotion.PressStiffness,
        ),
        label = "amount-press-feedback",
    )
    LaunchedEffect(displayAmount, scrollState.maxValue) {
        scrollState.scrollTo(scrollState.maxValue)
    }
    Row(
        modifier = modifier
            .padding(start = 2.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { onFocusChanged(it.isFocused) }
            .graphicsLayer {
                scaleX = 1f - (pressProgress * 0.018f)
                scaleY = 1f - (pressProgress * 0.018f)
                alpha = 1f - (pressProgress * 0.14f)
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .horizontalScroll(scrollState)
            .padding(top = 3.dp, end = 8.dp, bottom = 3.dp),
    ) {
        Text(
            text = displayAmount,
            modifier = Modifier.alignByBaseline(),
            style = MaterialTheme.typography.displaySmall.copy(
                fontSize = 36.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = accent,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickAddKeyboardDock(
    state: QuickAddKeyboardState,
    customKeyboardProgress: () -> Float,
    accent: Color,
    pendingOperator: String?,
    expressionMode: Boolean,
    canEvaluate: Boolean,
    canSave: Boolean,
    isEditing: Boolean,
    onDigit: (String) -> Unit,
    onOperator: (String) -> Unit,
    onBackspace: () -> Unit,
    onEvaluate: () -> Unit,
    onSaveAgain: () -> Unit,
    onDone: () -> Unit,
    onDelete: () -> Unit,
) {
    val progress = customKeyboardProgress().coerceIn(0f, 1f)
    val keyboardFloor = QuickAddCustomKeyboardHeight * progress
    val keyboardTravelPx = with(LocalDensity.current) { 18.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
            .heightIn(min = keyboardFloor),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            // Keep the keypad composed so rapid focus changes retarget one transition instead of rebuilding it.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // Keep the complete key grid measured as one surface; only the whole surface moves.
                    .requiredHeight(QuickAddCustomKeyboardHeight)
                    .graphicsLayer {
                        alpha = progress
                        translationY = keyboardTravelPx * (1f - progress)
                    },
            ) {
                Spacer(Modifier.height(10.dp))
                CalculatorPad(
                    accent = accent,
                    pendingOperator = pendingOperator,
                    expressionMode = expressionMode,
                    canEvaluate = canEvaluate,
                    canSave = canSave,
                    isEditing = isEditing,
                    keyboardEnabled = state.customKeyboardEnabled,
                    onDigit = onDigit,
                    onOperator = onOperator,
                    onBackspace = onBackspace,
                    onEvaluate = onEvaluate,
                    onSaveAgain = onSaveAgain,
                    onDone = onDone,
                    onDelete = onDelete,
                )
            }
        }
    }
}

@Composable
private fun CalculatorPad(
    accent: Color,
    pendingOperator: String?,
    expressionMode: Boolean,
    canEvaluate: Boolean,
    canSave: Boolean,
    isEditing: Boolean,
    keyboardEnabled: Boolean,
    onDigit: (String) -> Unit,
    onOperator: (String) -> Unit,
    onBackspace: () -> Unit,
    onEvaluate: () -> Unit,
    onSaveAgain: () -> Unit,
    onDone: () -> Unit,
    onDelete: () -> Unit,
) {
    val secondaryAction = if (isEditing) "删除" else "再记"
    val completionAction = if (isEditing) "保存" else "完成"
    val primaryAction = if (expressionMode) "=" else completionAction
    val rows = listOf(
        listOf("1", "2", "3", "+"),
        listOf("4", "5", "6", "−"),
        listOf("7", "8", "9", secondaryAction),
        listOf(".", "0", "⌫", primaryAction),
    )
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                row.forEach { key ->
                    val primary = key == primaryAction
                    val secondary = key == secondaryAction
                    val operator = key == "+" || key == "−"
                    val selectedOperator = operator && key == pendingOperator
                    PressableSurface(
                        onClick = {
                            when (key) {
                                "+", "−" -> onOperator(key)
                                "⌫" -> onBackspace()
                                "再记" -> onSaveAgain()
                                "删除" -> onDelete()
                                primaryAction -> if (expressionMode) onEvaluate() else onDone()
                                else -> onDigit(key)
                            }
                        },
                        enabled = when (key) {
                            primaryAction -> keyboardEnabled && if (expressionMode) canEvaluate else canSave
                            "再记" -> keyboardEnabled && canSave
                            else -> keyboardEnabled
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(KeyboardKeyStyle.Height),
                        color = when {
                            primary -> accent
                            key == "删除" -> ExpenseCoral.copy(alpha = 0.16f)
                            secondary -> accent.copy(alpha = 0.16f)
                            selectedOperator -> accent.copy(alpha = 0.18f)
                            operator -> MaterialTheme.colorScheme.surfaceVariant
                            else -> MaterialTheme.colorScheme.surface
                        },
                        shape = RoundedCornerShape(KeyboardKeyStyle.CornerRadius),
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            when (key) {
                                "+", "−" -> Text(
                                    text = key,
                                    color = if (selectedOperator) accent else MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontSize = KeyboardKeyStyle.GlyphFontSize,
                                        lineHeight = KeyboardKeyStyle.GlyphLineHeight,
                                        fontWeight = if (selectedOperator) {
                                            KeyboardKeyStyle.SelectedGlyphWeight
                                        } else {
                                            KeyboardKeyStyle.GlyphWeight
                                        },
                                    ),
                                )
                                "⌫" -> Icon(
                                    Icons.AutoMirrored.Filled.Backspace,
                                    contentDescription = "退格",
                                    modifier = Modifier.size(KeyboardKeyStyle.DeleteIconSize),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                primaryAction -> Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (!expressionMode) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(KeyboardKeyStyle.FunctionIconSize),
                                        )
                                    }
                                    Text(
                                        primaryAction,
                                        modifier = Modifier.padding(start = if (expressionMode) 0.dp else 4.dp),
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontSize = if (expressionMode) {
                                                KeyboardKeyStyle.GlyphFontSize
                                            } else {
                                                KeyboardKeyStyle.FunctionFontSize
                                            },
                                            fontWeight = FontWeight.Bold,
                                        ),
                                    )
                                }
                                else -> Text(
                                    key,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontSize = when {
                                            secondary -> KeyboardKeyStyle.FunctionFontSize
                                            key == "." -> KeyboardKeyStyle.DecimalFontSize
                                            else -> KeyboardKeyStyle.GlyphFontSize
                                        },
                                        lineHeight = KeyboardKeyStyle.GlyphLineHeight,
                                        fontWeight = if (secondary) FontWeight.Bold else KeyboardKeyStyle.GlyphWeight,
                                    ),
                                    color = if (key == "删除") ExpenseCoral else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionDateTimeDialog(
    initialMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.46f))
                .padding(horizontal = 22.dp),
            contentAlignment = Alignment.Center,
        ) {
            TransactionDateTimeContent(
                initialMillis = initialMillis,
                onDismiss = onDismiss,
                onConfirm = onConfirm,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TransactionDateTimeContent(
    initialMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draftMillis by rememberSaveable { mutableLongStateOf(initialMillis) }
    var monthStart by rememberSaveable {
        mutableLongStateOf(MonthWindow.current(initialMillis).start)
    }
    var showClock by rememberSaveable { mutableStateOf(false) }
    val window = remember(monthStart) { MonthWindow.current(monthStart) }
    val cells = remember(window) { window.calendarDateCells() }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(28.dp),
        shadowElevation = 18.dp,
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(window.monthLabel(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
                DateArrow(Icons.Default.ChevronLeft, "上个月") { monthStart = monthStart.shiftMonth(-1) }
                Spacer(Modifier.width(8.dp))
                DateArrow(Icons.Default.ChevronRight, "下个月") { monthStart = monthStart.shiftMonth(1) }
            }
            Spacer(Modifier.height(16.dp))
            DateGrid(
                cells = cells,
                selectedMillis = draftMillis,
                onSelected = { dayStart -> draftMillis = combineDateAndTime(dayStart, draftMillis) },
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("时间", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                PressableSurface(
                    onClick = { showClock = true },
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(18.dp))
                        Text(
                            SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(draftMillis)),
                            modifier = Modifier.padding(start = 6.dp),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    shape = RoundedCornerShape(15.dp),
                ) { Text("取消", fontWeight = FontWeight.Bold) }
                Button(
                    onClick = { onConfirm(draftMillis) },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue, contentColor = Color.White),
                    shape = RoundedCornerShape(15.dp),
                ) { Text("确定", fontWeight = FontWeight.Bold) }
            }
        }
    }

    if (showClock) {
        ClockDialog(
            initialMillis = draftMillis,
            onDismiss = { showClock = false },
            onConfirm = { hour, minute ->
                draftMillis = draftMillis.withTime(hour, minute)
                showClock = false
            },
        )
    }
}

@Composable
private fun DateArrow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    PressableSurface(
        onClick = onClick,
        modifier = Modifier.size(38.dp),
        color = BrandBlue.copy(alpha = 0.15f),
        shape = RoundedCornerShape(12.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, tint = BrandBlueLight)
        }
    }
}

@Composable
private fun DateGrid(
    cells: List<CalendarDateCell>,
    selectedMillis: Long,
    onSelected: (Long) -> Unit,
) {
    val selectedDay = selectedMillis.localDayKey()
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("日", "一", "二", "三", "四", "五", "六").forEach {
                Text(
                    it,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { cell ->
                    val selected = cell.startMillis.localDayKey() == selectedDay
                    PressableSurface(
                        onClick = { onSelected(cell.startMillis) },
                        enabled = cell.inCurrentMonth,
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp),
                        color = if (selected) BrandBlue else Color.Transparent,
                        shape = CircleShape,
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                if (cell.inCurrentMonth) cell.dayNumber.toString() else "",
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClockDialog(
    initialMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    val initial = remember(initialMillis) { Calendar.getInstance().apply { timeInMillis = initialMillis } }
    val state = rememberTimePickerState(
        initialHour = initial.get(Calendar.HOUR_OF_DAY),
        initialMinute = initial.get(Calendar.MINUTE),
        is24Hour = true,
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(26.dp)) {
            Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("选择时间", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(14.dp))
                TimePicker(state = state)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) { Text("取消") }
                    Button(
                        onClick = { onConfirm(state.hour, state.minute) },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue, contentColor = Color.White),
                    ) { Text("确定") }
                }
            }
        }
    }
}

@Composable
internal fun QuickAddPreviewSurface(
    accounts: List<AccountEntity>,
    accountBalances: List<AccountBalanceItem> = emptyList(),
    expenseCategories: List<CategoryEntity>,
    incomeCategories: List<CategoryEntity>,
    nowMillis: Long,
    initialTransaction: TransactionListItem? = null,
    initialCalculatorState: AmountCalculatorState? = null,
    initialType: String? = null,
    initialExpandedCategoryId: String? = null,
) {
    QuickAddContent(
        accounts = accounts,
        accountBalances = accountBalances,
        expenseCategories = expenseCategories,
        incomeCategories = incomeCategories,
        onDismiss = {},
        onSave = { _, _ -> },
        initialTransaction = initialTransaction,
        onDelete = if (initialTransaction == null) null else ({ }),
        errorMessage = null,
        nowMillis = nowMillis,
        initialCalculatorState = initialCalculatorState,
        initialTypeOverride = initialType,
        initialExpandedCategoryId = initialExpandedCategoryId,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
internal fun TransactionDateTimePreviewSurface(nowMillis: Long) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        BrandBlue.copy(alpha = 0.18f),
                    ),
                ),
            )
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        TransactionDateTimeContent(
            initialMillis = nowMillis,
            onDismiss = {},
            onConfirm = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun Long.amountInputValue(): String = BigDecimal.valueOf(this, 2)
    .setScale(2, RoundingMode.UNNECESSARY)
    .toPlainString()

private fun Long.shiftMonth(delta: Int): Long = Calendar.getInstance().apply {
    timeInMillis = this@shiftMonth
    add(Calendar.MONTH, delta)
}.timeInMillis

private fun combineDateAndTime(dayStart: Long, timeSource: Long): Long {
    val day = Calendar.getInstance().apply { timeInMillis = dayStart }
    val time = Calendar.getInstance().apply { timeInMillis = timeSource }
    day.set(Calendar.HOUR_OF_DAY, time.get(Calendar.HOUR_OF_DAY))
    day.set(Calendar.MINUTE, time.get(Calendar.MINUTE))
    day.set(Calendar.SECOND, 0)
    day.set(Calendar.MILLISECOND, 0)
    return day.timeInMillis
}

private fun Long.withTime(hour: Int, minute: Int): Long = Calendar.getInstance().apply {
    timeInMillis = this@withTime
    set(Calendar.HOUR_OF_DAY, hour)
    set(Calendar.MINUTE, minute)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun Long.localDayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(this))
