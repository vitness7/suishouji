package com.hza.ledger.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.input.KeyboardType
import com.hza.ledger.data.local.AccountEntity
import com.hza.ledger.data.local.CategoryEntity
import com.hza.ledger.pending.PendingEdit
import com.hza.ledger.domain.model.TransactionType
import com.hza.ledger.pending.describeAmountProblem
import com.hza.ledger.pending.formatMinorForInput
import com.hza.ledger.pending.message
import com.hza.ledger.pending.parseAmountInputToMinor
import com.hza.ledger.pending.validatePendingEdit
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hza.ledger.data.local.PendingTransactionEntity
import com.hza.ledger.ui.components.CategoryGlyph
import com.hza.ledger.ui.components.OneLedgerCard
import com.hza.ledger.ui.components.PressableSurface
import com.hza.ledger.ui.components.ScreenHeader
import com.hza.ledger.ui.theme.ExpenseCoral
import com.hza.ledger.ui.theme.IncomeMint
import com.hza.ledger.ui.theme.SavingsAmber
import com.hza.ledger.ui.theme.Violet
import com.hza.ledger.util.MoneyFormatter
import com.hza.ledger.util.formatConfidence

/**
 * 待确认列表。
 *
 * 设计原则：识别结果绝不自动入账。用户逐条确认后才写入正式账本，
 * 避免误识别导致账目失真——失真的账本比没有账本更糟。
 */
@Composable
fun PendingScreen(
    pending: List<PendingTransactionEntity>,
    categoryNames: Map<String, String>,
    /** 支出分类，供编辑时更换。 */
    expenseCategories: List<CategoryEntity> = emptyList(),
    /** 收入分类，供编辑时更换。 */
    incomeCategories: List<CategoryEntity> = emptyList(),
    /** 账户列表，供编辑时选择。 */
    accounts: List<AccountEntity> = emptyList(),
    onConfirm: (PendingTransactionEntity) -> Unit,
    onConfirmEdited: (PendingEdit) -> Unit = {},
    onDiscard: (PendingTransactionEntity) -> Unit,
    onDiscardAll: () -> Unit,
    onOpenSetup: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // 编辑中的条目。非空即弹出编辑面板。
    var editing by remember { mutableStateOf<PendingTransactionEntity?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        ScreenHeader(
            title = "待确认",
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
            action = {
                if (pending.isNotEmpty()) {
                    PressableSurface(
                        onClick = onDiscardAll,
                        modifier = Modifier.size(42.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Close, contentDescription = "全部忽略")
                        }
                    }
                }
            },
        )

        if (pending.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp),
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "暂时没有待确认的账目",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "开启通知监听后，微信、支付宝、银行的支付提示会自动出现在这里。\n识别结果需要你确认才会入账。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(Modifier.height(18.dp))
                    PressableSurface(
                        onClick = onOpenSetup,
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary,
                    ) {
                        Box(
                            Modifier
                                .padding(horizontal = 22.dp, vertical = 11.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "去开启自动记账",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
            }
            return
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "识别到 ${pending.size} 笔交易，确认后才会计入账本",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            items(pending, key = { it.id }) { item ->
                PendingCard(
                    item = item,
                    categoryName = item.suggestedCategoryKey?.let { categoryNames[it] },
                    onConfirm = { onConfirm(item) },
                    onDiscard = { onDiscard(item) },
                    onPreview = { editing = item },
                )
            }
        }
    }

    editing?.let { item ->
        PendingEditDialog(
            item = item,
            expenseCategories = expenseCategories,
            incomeCategories = incomeCategories,
            accounts = accounts,
            onDismiss = { editing = null },
            onConfirm = { edit ->
                editing = null
                onConfirmEdited(edit)
            },
        )
    }
}

@Composable
private fun PendingCard(
    item: PendingTransactionEntity,
    categoryName: String?,
    onConfirm: () -> Unit,
    onDiscard: () -> Unit,
    onPreview: () -> Unit,
) {
    val isIncome = item.type == "INCOME"
    val amountColor = if (isIncome) IncomeMint else ExpenseCoral

    OneLedgerCard(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryGlyph(
                    iconKey = categoryIconFor(item.suggestedCategoryKey),
                    color = amountColor,
                    modifier = Modifier.size(34.dp),
                )

                Column(
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .weight(1f),
                ) {
                    Text(
                        item.merchant.ifBlank { "未知商户" },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        buildString {
                            append(channelLabel(item.channel))
                            if (categoryName != null) append(" · 建议「$categoryName」")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    (if (isIncome) "+" else "-") + MoneyFormatter.format(item.amountMinor),
                    style = MaterialTheme.typography.titleMedium,
                    color = amountColor,
                    fontWeight = FontWeight.Bold,
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 置信度条：直观显示识别可靠程度
                LinearProgressIndicator(
                    progress = { item.confidence },
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp),
                    color = when {
                        item.confidence >= 0.75f -> IncomeMint
                        item.confidence >= 0.55f -> SavingsAmber
                        else -> ExpenseCoral
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "可信度 ${formatConfidence(item.confidence)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = onDiscard, modifier = Modifier.weight(1f)) {
                    Text("忽略")
                }
                PressableSurface(
                    onClick = onPreview,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("修改", style = MaterialTheme.typography.labelLarge)
                    }
                }
                PressableSurface(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "确认",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 待确认流水的编辑面板。
 *
 * ## 为什么需要它
 *
 * 自动识别不可能 100% 准，尤其「这笔是进账还是出账」——
 * 通知文案里常常没有明确的收付方向词，只能靠渠道和上下文推断。
 * 早先这个入口的按钮写着「修改」，打开的却是只读详情，
 * 用户根本改不了，只能把识别错的记录整条忽略掉。
 *
 * ## 交互设计
 *
 * 字段按类型**动态显示**，避免让用户面对一堆无关选项：
 * - 支出/收入：类型、金额、分类、账户
 * - 转账：类型、金额、转出账户、转入账户（转账不归属任何收支科目）
 *
 * 切换类型时分类会**自动切换到对应类型的第一个分类** ——
 * 否则从「支出」改成「收入」后会残留「餐饮」这种支出分类，
 * 产生「收入 + 餐饮」的脏数据，统计报表会出现无法解释的科目。
 */
@Composable
private fun PendingEditDialog(
    item: PendingTransactionEntity,
    expenseCategories: List<CategoryEntity>,
    incomeCategories: List<CategoryEntity>,
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onConfirm: (PendingEdit) -> Unit,
) {
    var type by remember { mutableStateOf(item.type) }
    var amountText by remember { mutableStateOf(formatMinorForInput(item.amountMinor)) }
    var categoryId by remember { mutableStateOf(item.suggestedCategoryKey) }
    var accountId by remember { mutableStateOf(accounts.firstOrNull()?.id) }
    var toAccountId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val isTransfer = type == TransactionType.TRANSFER
    // 分类随类型切换 —— 收入和支出用的是两套独立分类体系
    val availableCategories = when (type) {
        "INCOME" -> incomeCategories
        TransactionType.TRANSFER -> emptyList()
        else -> expenseCategories
    }

    // 类型变化后，若原分类不在新类型的分类表里，自动落到第一个
    LaunchedEffect(type) {
        if (!isTransfer && availableCategories.none { it.id == categoryId }) {
            categoryId = availableCategories.firstOrNull()?.id
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改并确认") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                // ---- 类型 ----
                EditFieldLabel("类型")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TypeChip("支出", type == "EXPENSE", ExpenseCoral) { type = "EXPENSE" }
                    TypeChip("收入", type == "INCOME", IncomeMint) { type = "INCOME" }
                    TypeChip("转账", isTransfer, MaterialTheme.colorScheme.primary) {
                        type = TransactionType.TRANSFER
                    }
                }

                // ---- 金额 ----
                EditFieldLabel("金额")
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    prefix = { Text("¥") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = error != null,
                )

                // ---- 分类（转账不需要）----
                if (!isTransfer) {
                    EditFieldLabel("分类")
                    if (availableCategories.isEmpty()) {
                        Text(
                            "当前账本还没有${if (type == "INCOME") "收入" else "支出"}分类",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        PickerRow(
                            options = availableCategories.map { it.id to it.name },
                            selectedId = categoryId,
                            onSelect = {
                                categoryId = it
                                error = null
                            },
                        )
                    }
                }

                // ---- 账户 ----
                EditFieldLabel(if (isTransfer) "转出账户" else "账户")
                if (accounts.isEmpty()) {
                    Text(
                        "当前账本还没有账户，请先到资产页添加",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    PickerRow(
                        options = accounts.map { it.id to it.name },
                        selectedId = accountId,
                        onSelect = {
                            accountId = it
                            error = null
                        },
                    )
                }

                // ---- 转入账户（仅转账）----
                if (isTransfer) {
                    EditFieldLabel("转入账户")
                    PickerRow(
                        options = accounts.map { it.id to it.name },
                        selectedId = toAccountId,
                        onSelect = {
                            toAccountId = it
                            error = null
                        },
                    )
                }

                // ---- 识别依据（保留原文，便于判断该怎么改）----
                if (item.rawText.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "识别原文",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        item.rawText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                error?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val minor = parseAmountInputToMinor(amountText)
                    if (minor == null) {
                        error = describeAmountProblem(amountText)?.message() ?: "金额格式不正确"
                        return@TextButton
                    }
                    val problem = validatePendingEdit(
                        type = type,
                        amountMinor = minor,
                        accountId = accountId,
                        toAccountId = toAccountId,
                        categoryId = categoryId,
                    )
                    if (problem != null) {
                        error = problem.message()
                        return@TextButton
                    }
                    onConfirm(
                        PendingEdit(
                            pendingId = item.id,
                            type = type,
                            amountMinor = minor,
                            categoryId = if (isTransfer) null else categoryId,
                            accountId = accountId.orEmpty(),
                            toAccountId = if (isTransfer) toAccountId else null,
                            merchant = item.merchant,
                        ),
                    )
                },
            ) {
                Text("保存并入账")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun EditFieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 类型选择按钮。 */
@Composable
private fun TypeChip(
    text: String,
    selected: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    PressableSurface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(
            Modifier
                .height(38.dp)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            )
        }
    }
}

/**
 * 横向可滚动的单选行。
 *
 * 用 chips 而不是下拉菜单：选中状态一眼可见，
 * 分类与账户数量都不多，横向滚动足够容纳。
 */
@Composable
private fun PickerRow(
    options: List<Pair<String, String>>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (id, name) ->
            val selected = id == selectedId
            PressableSurface(
                onClick = { onSelect(id) },
                shape = RoundedCornerShape(10.dp),
                color = if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ) {
                Box(
                    Modifier
                        .height(36.dp)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        name,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

private fun channelLabel(channel: String): String = when (channel) {
    "WECHAT" -> "微信支付"
    "ALIPAY" -> "支付宝"
    "BANK_CARD" -> "银行卡"
    "CREDIT_CARD" -> "信用卡"
    else -> "未知渠道"
}

private fun categoryIconFor(key: String?): String = when (key) {
    "expense-food" -> "food"
    "expense-transport" -> "car"
    "expense-shopping" -> "shopping"
    "expense-housing" -> "housing"
    "expense-health" -> "health"
    "expense-fun" -> "fun"
    "expense-study" -> "study"
    "expense-daily" -> "daily"
    "expense-business" -> "office"
    "income-salary" -> "salary"
    else -> "other"
}
