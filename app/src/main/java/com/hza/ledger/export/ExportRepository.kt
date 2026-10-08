package com.hza.ledger.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.hza.ledger.data.local.LedgerDao
import com.hza.ledger.domain.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CSV 导出：把账本数据取出、翻译成中文、生成文件并分享。
 *
 * ## 隐私边界
 *
 * **完全不联网。** 生成的文件由用户主动分享，
 * 发到哪里由用户决定，应用不参与、不上传。
 */
class ExportRepository(
    private val dao: LedgerDao,
) {

    /**
     * 导出账目明细。
     *
     * @return 导出结果，成功时携带文件与记录数
     */
    suspend fun exportTransactions(
        context: Context,
        bookId: String,
    ): Result<ExportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val transactions = dao.getAllTransactions(bookId)
            val accounts = dao.getAccounts(bookId)
            // 分类按收支类型分开存放，两边都要取才能拿全
            val categories = dao.getCategories(bookId, TransactionType.EXPENSE) +
                dao.getCategories(bookId, TransactionType.INCOME)

            // ⚠️ 建立 ID → 中文名 的映射。
            // 导出给用户看的东西必须是「餐饮」而不是「expense-food」——
            // 内部 ID 对用户毫无意义。
            val accountNames = accounts.associate { it.id to it.name }
            val categoryNames = categories.associate { it.id to it.name }

            val rows = transactions.map { txn ->
                ExportRow.from(
                    type = typeLabel(txn.type),
                    amountMinor = txn.amountMinor,
                    categoryName = txn.categoryId
                        ?.let { categoryNames[it] }
                        .orEmpty()
                        .ifBlank { "未分类" },
                    accountName = accountNames[txn.accountId] ?: "未知账户",
                    toAccountName = txn.toAccountId
                        ?.let { accountNames[it] }
                        .orEmpty(),
                    merchant = txn.merchant,
                    note = txn.note,
                    occurredAt = txn.occurredAt,
                )
            }

            val csvBytes = CsvExporter.buildCsvBytes(rows)
            val file = writeToCache(context, csvBytes)

            ExportResult(
                file = file,
                recordCount = transactions.size,
            )
        }
    }

    /** 写入应用缓存目录，并通过 FileProvider 授权分享。 */
    private fun writeToCache(context: Context, content: ByteArray): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        // 清掉旧文件，避免缓存无限增长
        dir.listFiles()?.forEach { it.delete() }

        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())
        val file = File(dir, "随手记账本_$stamp.csv")
        // ⚠️ 直接写字节，编码已在生成阶段确定（含 UTF-8 BOM）。
        // 若在此处用 writeText 转码，BOM 可能丢失，Excel 打开会中文乱码。
        file.writeBytes(content)
        return file
    }

    private fun typeLabel(type: String): String = when (type) {
        TransactionType.EXPENSE -> "支出"
        TransactionType.INCOME -> "收入"
        TransactionType.TRANSFER -> "转账"
        else -> "其他"
    }

    /**
     * 生成分享 Intent。
     *
     * 用 FileProvider 而非 file:// Uri —— Android 7+ 禁止跨应用传 file:// ，
     * 会抛 FileUriExposedException。
     */
    fun buildShareIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "账本导出")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}

data class ExportResult(
    val file: File,
    val recordCount: Int,
)