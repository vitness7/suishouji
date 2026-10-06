package com.hza.ledger.ui.screens

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
import com.hza.ledger.ui.components.SectionTitle
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
    onConfirm: (PendingTransactionEntity) -> Unit,
    onDiscard: (PendingTransactionEntity) -> Unit,
    onDiscardAll: () -> Unit,
    onOpenSetup: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var preview by remember { mutableStateOf<PendingTransactionEntity?>(null) }

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
                    onPreview = { preview = item },
                )
            }
        }
    }

    preview?.let { item ->
        PendingPreviewDialog(
            item = item,
            onDismiss = { preview = null },
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

@Composable
private fun PendingPreviewDialog(
    item: PendingTransactionEntity,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("识别详情") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailRow("金额", MoneyFormatter.format(item.amountMinor))
                DetailRow("方向", if (item.type == "INCOME") "收入" else "支出")
                DetailRow("渠道", channelLabel(item.channel))
                DetailRow("商户", item.merchant.ifBlank { "未知" })
                DetailRow("可信度", formatConfidence(item.confidence))
                Spacer(Modifier.height(4.dp))
                SectionTitle("原始文本")
                Text(
                    item.rawText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("知道了") }
        },
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
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
    "income-salary" -> "salary"
    else -> "other"
}
