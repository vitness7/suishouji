package com.hza.ledger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hza.ledger.data.local.SavingsPlanEntity
import com.hza.ledger.domain.model.SavingsMethod
import com.hza.ledger.util.MoneyFormatter
import com.hza.ledger.util.formatMinorInput

/** 存钱方式选项。 */
data class SavingsMethodOption(
    val method: String,
    val label: String,
    val description: String,
    val color: Color,
)

val SAVINGS_METHOD_OPTIONS = listOf(
    SavingsMethodOption(
        SavingsMethod.FIXED, "固定金额",
        "每月存固定数额，适合攒钱有明确节奏的人",
        Color(0xFF4D7CFE),
    ),
    SavingsMethodOption(
        SavingsMethod.FLEXIBLE, "灵活金额",
        "想存多少存多少，没有固定计划",
        Color(0xFF57D3A2),
    ),
    SavingsMethodOption(
        SavingsMethod.WEEK_52, "一年52 周",
        "每周存一笔，一年完成一个目标",
        Color(0xFFFFC247),
    ),
    SavingsMethodOption(
        SavingsMethod.DAY_365, "一年365 天",
        "每天存一点，靠日积月累完成",
        Color(0xFF9B7BFF),
    ),
)

fun savingsMethodLabel(method: String): String =
    SAVINGS_METHOD_OPTIONS.firstOrNull { it.method == method }?.label ?: "自定义"

fun savingsMethodColor(method: String): Color =
    SAVINGS_METHOD_OPTIONS.firstOrNull { it.method == method }?.color ?: Color(0xFF8992A3)

/** 存钱目标配色，可循环选用。 */
private val PLAN_COLORS = listOf(
    0xFF4D7CFEL, 0xFF57D3A2L, 0xFFFFC247L, 0xFF9B7BFFL, 0xFFFF6B5FL, 0xFF29B6F6L,
)

/**
 * 存钱目标编辑弹窗。
 *
 * 支持新建与编辑已有目标；目标金额用 BigDecimal 解析，避免浮点误差。
 */
@Composable
fun SavingsPlanEditorDialog(
    existing: SavingsPlanEntity? = null,
    showDelete: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (name: String, method: String, targetMinor: Long, colorHex: Long) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var method by remember { mutableStateOf(existing?.method ?: SavingsMethod.FIXED) }
    var targetText by remember {
        mutableStateOf(
            existing?.let { (it.targetMinor / 100.0).toString().removeSuffix(".0") }.orEmpty(),
        )
    }
    var colorIndex by remember {
        mutableStateOf(
            existing?.let { plan ->
                PLAN_COLORS.indexOfFirst { it == plan.colorHex }.takeIf { it >= 0 }
            } ?: 0,
        )
    }

    val targetMinor = formatMinorInput(targetText)
    val canSave = name.isNotBlank() && targetMinor != null && targetMinor > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "新建存钱目标" else "编辑目标") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("目标名称") },
                    placeholder = { Text("如：换新手机") },
                    singleLine = true,
                )

                OutlinedTextField(
                    value = targetText,
                    onValueChange = { targetText = it.filter { c -> c.isDigit() || c == '.' } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("目标金额（元）") },
                    singleLine = true,
                )

                Column {
                    Text(
                        "存钱方式",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(SAVINGS_METHOD_OPTIONS.size) { index ->
                            val option = SAVINGS_METHOD_OPTIONS[index]
                            val selected = option.method == method
                            Column(
                                modifier = Modifier
                                    .background(
                                        if (selected) option.color.copy(alpha = 0.16f)
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(12.dp),
                                    )
                                    .clickable { method = option.method }
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                            ) {
                                Text(
                                    option.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) option.color
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                )
                                Text(
                                    option.description,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                Column {
                    Text(
                        "颜色",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PLAN_COLORS.forEachIndexed { index, hex ->
                            val selected = index == colorIndex
                            Box(
                                modifier = Modifier
                                    .size(if (selected) 32.dp else 26.dp)
                                    .background(Color(hex.toULong()), CircleShape)
                                    .clickable { colorIndex = index },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selected) {
                                    Text(
                                        "✓",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = { onSave(name.trim(), method, targetMinor ?: 0L, PLAN_COLORS[colorIndex]) },
            ) {
                Text(if (existing == null) "创建" else "保存", fontWeight = FontWeight.Medium)
            }
        },
        dismissButton = {
            Row {
                if (showDelete && existing != null && onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

/**
 * 存入一笔弹窗。
 *
 * 存入本质是「这笔钱从账户离开」，所以要选来源账户 —— 写入后账户余额才会同步减少。
 */
@Composable
fun SavingsDepositDialog(
    plan: SavingsPlanEntity,
    accounts: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onDeposit: (amountMinor: Long, accountId: String, note: String) -> Unit,
) {
    var amountText by remember { mutableStateOf("") }
    var accountId by remember { mutableStateOf(accounts.firstOrNull()?.first.orEmpty()) }
    var note by remember { mutableStateOf("") }

    val amountMinor = formatMinorInput(amountText)
    val canDeposit = amountMinor != null && amountMinor > 0 && accountId.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("存入「${plan.name}」") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "已存 ${MoneyFormatter.format(plan.savedMinor)}　目标 ${MoneyFormatter.format(plan.targetMinor)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("存入金额（元）") },
                    singleLine = true,
                )

                if (accounts.isEmpty()) {
                    Text(
                        "还没有可用账户，请先到「资产」页添加一个账户。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Column {
                        Text(
                            "从哪个账户转出",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(accounts.size) { index ->
                                val (id, name) = accounts[index]
                                val selected = id == accountId
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                            else MaterialTheme.colorScheme.surfaceVariant,
                                            RoundedCornerShape(12.dp),
                                        )
                                        .clickable { accountId = id }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                ) {
                                    Text(
                                        name,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("备注（可选）") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canDeposit,
                onClick = { onDeposit(amountMinor ?: 0L, accountId, note.trim()) },
            ) {
                Text("确认存入", fontWeight = FontWeight.Medium)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
