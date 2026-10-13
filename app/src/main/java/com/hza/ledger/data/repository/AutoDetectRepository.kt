package com.hza.ledger.data.repository

import com.hza.ledger.autodetect.MIN_CONFIDENCE
import com.hza.ledger.autodetect.ParsedTransaction
import com.hza.ledger.autodetect.ParseChannel
import com.hza.ledger.autodetect.ParseDirection
import com.hza.ledger.autodetect.parseTransactionText
import com.hza.ledger.data.local.LedgerDao
import com.hza.ledger.data.local.PendingTransactionEntity
import com.hza.ledger.data.local.TransactionEntity
import com.hza.ledger.domain.model.AccountType
import com.hza.ledger.domain.model.TransactionType
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest

/**
 * 自动识别候选的接入与确认。
 *
 * 独立于 [OfflineLedgerRepository] 的业务规则，只负责把「解析结果」落到「待确认表」，
 * 以及用户确认后转成正式流水。放在独立文件是为了让解析落库逻辑可被单元测试覆盖。
 */
class AutoDetectRepository(
    private val dao: LedgerDao,
    private val activeBookId: Flow<String>,
) {
    val pendingTransactions: Flow<List<PendingTransactionEntity>> =
        activeBookId.flatMapLatest { dao.observePendingTransactions(it) }

    val pendingCount: Flow<Int> = activeBookId.flatMapLatest { dao.observePendingCount(it) }

    /**
     * 接入一段文本。解析成功且置信度达标才写入候选表。
     *
     * @return 是否成功产生候选
     */
    suspend fun ingest(
        bookId: String,
        text: String,
        sourceKey: String,
        packageName: String? = null,
    ): Boolean {
        // ⚠️ 必须传 packageName，否则电商场景的经营识别拿不到来源信息，
        // 会退化成只看文案判断，商家 App 的支出可能被归到生活类。
        val parsed = parseTransactionText(text, packageName) ?: return false
        if (parsed.confidence < MIN_CONFIDENCE) return false

        // 同一条来源只留一条，防止通知重复投递堆积
        if (dao.pendingSourceExists(sourceKey)) return false

        val now = System.currentTimeMillis()
        val entity = PendingTransactionEntity(
            id = "pending-${UUID.randomUUID()}",
            bookId = bookId,
            type = parsed.direction.toTransactionType(),
            amountMinor = parsed.amountMinor,
            merchant = parsed.merchant.orEmpty(),
            note = "",
            channel = parsed.channel.name,
            suggestedCategoryKey = parsed.suggestedCategoryKey,
            confidence = parsed.confidence,
            sourceKey = sourceKey,
            rawText = parsed.rawText,
            occurredAt = now,
            createdAt = now,
        )
        return dao.insertPending(entity) != -1L
    }

    /**
     * 确认候选并写入正式流水。
     *
     * 分类与账户由调用方（UI 层）给出——识别只是给建议，最终由用户决定。
     *
     * ## 为什么要在这里再校验一次
     *
     * UI 层已经校验过（`validatePendingEdit`），但**不能只靠 UI**：
     * 识别结果允许用户改成任意类型与分类，一旦出现
     * 「收入 + 餐饮分类」这种错配，统计报表里会冒出无法解释的科目，
     * 而且是静默写入、事后极难排查。
     *
     * 这与 [OfflineLedgerRepository.validateReferences] 的约束保持一致 ——
     * 两条写入路径必须用同一套规则，否则手工记账校验、自动记账不校验，
     * 等于留了个后门。
     */
    suspend fun confirm(
        bookId: String,
        pendingId: String,
        categoryId: String?,
        accountId: String,
        type: String,
        amountMinor: Long,
        merchant: String,
        note: String,
        occurredAt: Long,
        /** 转账时必填：转入账户。 */
        toAccountId: String? = null,
    ) {
        require(dao.accountExists(bookId, accountId)) { "账户不属于当前账本" }

        if (type == TransactionType.TRANSFER) {
            val target = toAccountId
            require(!target.isNullOrBlank()) { "转账必须指定转入账户" }
            require(target != accountId) { "转出与转入不能是同一个账户" }
            require(dao.accountExists(bookId, target)) { "转入账户不属于当前账本" }
        } else {
            val category = categoryId
            require(!category.isNullOrBlank()) { "请先选择分类" }
            // ⚠️ 这一条校验「分类类型与交易类型是否匹配」，
            // 是防止「收入 + 餐饮」错配的关键
            require(dao.categoryExists(bookId, category, type)) { "分类与收支类型不匹配" }
        }

        val now = System.currentTimeMillis()
        val transaction = TransactionEntity(
            id = "txn-${UUID.randomUUID()}",
            bookId = bookId,
            type = type,
            amountMinor = amountMinor,
            categoryId = if (type == TransactionType.TRANSFER) null else categoryId,
            accountId = accountId,
            toAccountId = if (type == TransactionType.TRANSFER) toAccountId else null,
            merchant = merchant,
            note = note,
            occurredAt = occurredAt,
            relatedTransactionId = null,
            reimbursementState = "NONE",
            createdAt = now,
            updatedAt = now,
        )
        // 写流水与删候选必须在同一事务，避免重复记账
        dao.confirmPending(bookId, pendingId, transaction)
    }

    suspend fun discard(bookId: String, pendingId: String) = dao.deletePending(bookId, pendingId)

    suspend fun discardAll(bookId: String, ids: List<String>) = dao.deletePendingAll(bookId, ids)
}

/** 解析方向映射到交易类型。方向未知时默认按支出处理。 */
internal fun ParseDirection.toTransactionType(): String = when (this) {
    ParseDirection.EXPENSE -> TransactionType.EXPENSE
    ParseDirection.INCOME -> TransactionType.INCOME
    ParseDirection.UNKNOWN -> TransactionType.EXPENSE
}

/** 解析渠道映射到建议账户类型，用于给用户预选账户。 */
internal fun ParseChannel.toAccountType(): String = when (this) {
    ParseChannel.WECHAT, ParseChannel.ALIPAY -> AccountType.DIGITAL
    ParseChannel.BANK_CARD -> AccountType.BANK
    ParseChannel.CREDIT_CARD -> AccountType.CREDIT
    ParseChannel.UNKNOWN -> AccountType.CASH
}
