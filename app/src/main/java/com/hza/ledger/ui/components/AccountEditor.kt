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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import com.hza.ledger.data.local.AccountEntity
import com.hza.ledger.domain.model.AccountType
import com.hza.ledger.util.MoneyFormatter
import com.hza.ledger.util.formatMinorInput

/** 账户类型选项，含显示名与配色。 */
data class AccountTypeOption(
    val type: String,
    val label: String,
    val color: Color,
)

val ACCOUNT_TYPE_OPTIONS = listOf(
    AccountTypeOption(AccountType.CASH, "现金", Color(0xFFFFC247)),
    AccountTypeOption(AccountType.BANK, "银行卡", Color(0xFF4D7CFE)),
    AccountTypeOption(AccountType.DIGITAL, "数字账户", Color(0xFF57D3A2)),
    AccountTypeOption(AccountType.CREDIT, "信用账户", Color(0xFFFF6B5F)),
    AccountTypeOption(AccountType.INVESTMENT, "投资账户", Color(0xFF9B7BFF)),
)

/**
 * 账户编辑弹窗。
 *
 * 同时用于「新建」与「编辑」—— 传 [existing] 即进入编辑模式。
 * 删除按钮只在编辑模式出现，且由调用方决定是否展示（[showDelete]）。
 */
@Composable
fun AccountEditorDialog(
    existing: AccountEntity? = null,
    showDelete: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        subtitle: String,
        type: String,
        openingBalanceMinor: Long,
        includeInNetWorth: Boolean,
    ) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var subtitle by remember { mutableStateOf(existing?.subtitle.orEmpty()) }
    var type by remember { mutableStateOf(existing?.type ?: AccountType.CASH) }
    var balanceText by remember {
        mutableStateOf(
            existing?.let { (it.openingBalanceMinor / 100.0).toString().removeSuffix(".0") }.orEmpty(),
        )
    }
    var includeInNetWorth by remember { mutableStateOf(existing?.includeInNetWorth ?: true) }
    var showBalanceInput by remember { mutableStateOf(false) }

    val balanceMinor = formatMinorInput(balanceText)
    val canSave = name.isNotBlank() && balanceMinor != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (existing == null) "添加账户" else "编辑账户",
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("账户名称") },
                    placeholder = { Text("如：招商银行储蓄卡") },
                    singleLine = true,
                )

                OutlinedTextField(
                    value = subtitle,
                    onValueChange = { subtitle = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("备注（可选）") },
                    placeholder = { Text("如：日常消费") },
                    singleLine = true,
                )

                // 账户类型：横向滚动的选项条
                Column {
                    Text(
                        "账户类型",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ACCOUNT_TYPE_OPTIONS.size) { index ->
                            val option = ACCOUNT_TYPE_OPTIONS[index]
                            TypeChip(
                                label = option.label,
                                color = option.color,
                                selected = option.type == type,
                                onClick = { type = option.type },
                            )
                        }
                    }
                }

                // 期初余额：默认折叠，避免首次使用显得繁琐
                Column {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("期初余额", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (showBalanceInput) "当前账户已有余额" else MoneyFormatter.format(balanceMinor ?: 0L),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { showBalanceInput = !showBalanceInput }) {
                            Text(if (showBalanceInput) "收起" else "修改")
                        }
                    }
                    if (showBalanceInput) {
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = balanceText,
                            onValueChange = { balanceText = it.filter { c -> c.isDigit() || c == '.' } },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("金额（元）") },
                            singleLine = true,
                        )
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("计入净资产", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (type == AccountType.CREDIT) "信用账户默认计入负债" else "关闭后不参与净资产汇总",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = includeInNetWorth, onCheckedChange = { includeInNetWorth = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    onSave(name.trim(), subtitle.trim(), type, balanceMinor ?: 0L, includeInNetWorth)
                },
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

@Composable
private fun TypeChip(
    label: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .background(
                if (selected) color.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/** 账户类型的中文名，供列表展示。 */
fun accountTypeLabel(type: String): String =
    ACCOUNT_TYPE_OPTIONS.firstOrNull { it.type == type }?.label ?: "其他"

/** 账户类型的配色，供图标展示。 */
fun accountTypeColor(type: String): Color =
    ACCOUNT_TYPE_OPTIONS.firstOrNull { it.type == type }?.color ?: Color(0xFF8992A3)
