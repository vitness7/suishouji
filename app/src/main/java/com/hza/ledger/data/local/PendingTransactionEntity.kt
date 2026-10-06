package com.hza.ledger.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 自动识别但尚未确认入账的候选交易。
 *
 * 设计要点：识别结果绝不直接写入 [TransactionEntity]，必须经用户确认后才转为正式流水。
 * 这样可以避免误识别污染账本——账目失真比没有记录更糟。
 */
@Entity(
    tableName = "pending_transactions",
    foreignKeys = [
        ForeignKey(
            entity = LedgerBookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("bookId"),
        Index("occurredAt"),
        // 同一来源同一时间只保留一条，避免通知重复投递造成堆积
        Index(value = ["sourceKey"], unique = true),
    ],
)
data class PendingTransactionEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val type: String,
    val amountMinor: Long,
    val merchant: String,
    val note: String,
    val channel: String,
    val suggestedCategoryKey: String?,
    val confidence: Float,
    val sourceKey: String,
    val rawText: String,
    val occurredAt: Long,
    val createdAt: Long,
)
