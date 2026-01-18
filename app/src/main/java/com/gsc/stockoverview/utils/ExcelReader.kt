package com.gsc.stockoverview.utils

import android.content.Context
import android.net.Uri
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream

class ExcelReader(private val context: Context) {

    /**
     * Reads a specific sheet from an Excel file and maps each row to an object.
     * 
     * @param uri The Uri of the Excel file.
     * @param sheetName The name of the sheet to read.
     * @param skipHeader Whether to skip the first row (header). Default is true.
     * @param rowMapper A function that converts a [RowReader] to an object of type T.
     */
    fun <T> readExcelSheet(
        uri: Uri,
        sheetName: String,
        skipHeader: Boolean = true,
        rowMapper: (RowReader) -> T?
    ): List<T> {
        val results = mutableListOf<T>()
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)

        inputStream?.use { stream ->
            val workbook = XSSFWorkbook(stream)
            val sheet = workbook.getSheet(sheetName) ?: return@use
            val startRow = if (skipHeader) 1 else 0

            for (i in startRow..sheet.lastRowNum) {
                val row = sheet.getRow(i) ?: continue
                try {
                    val item = rowMapper(RowReader(row))
                    if (item != null) {
                        results.add(item)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            workbook.close()
        }
        return results
    }

    /**
     * Reads all required sheets from the stock Excel file.
     */
    fun readAllStockData(uri: Uri): Triple<List<TransactionData>, List<TradingLogData>, List<OverseasTradingLogData>> {
        var transactions = emptyList<TransactionData>()
        var tradingLogs = emptyList<TradingLogData>()
        var overseasTradingLogs = emptyList<OverseasTradingLogData>()

        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        inputStream?.use { stream ->
            val workbook = XSSFWorkbook(stream)
            
            // Read "거래내역"
            workbook.getSheet("거래내역")?.let { sheet ->
                transactions = (1..sheet.lastRowNum).mapNotNull { i ->
                    sheet.getRow(i)?.let { row -> TransactionData(RowReader(row)) }
                }
            }

            // Read "매매일지"
            workbook.getSheet("매매일지")?.let { sheet ->
                tradingLogs = (1..sheet.lastRowNum).mapNotNull { i ->
                    sheet.getRow(i)?.let { row -> TradingLogData(RowReader(row)) }
                }
            }

            // Read "해외매매일지"
            workbook.getSheet("해외매매일지")?.let { sheet ->
                overseasTradingLogs = (1..sheet.lastRowNum).mapNotNull { i ->
                    sheet.getRow(i)?.let { row -> OverseasTradingLogData(RowReader(row)) }
                }
            }

            workbook.close()
        }
        return Triple(transactions, tradingLogs, overseasTradingLogs)
    }

    // Data wrappers to decouple Entity from ExcelReader while keeping mapping logic accessible
    class TransactionData(val reader: RowReader)
    class TradingLogData(val reader: RowReader)
    class OverseasTradingLogData(val reader: RowReader)

    class RowReader(private val row: Row) {
        fun getString(index: Int): String {
            val cell = row.getCell(index)
            return when (cell?.cellType) {
                CellType.STRING -> cell.stringCellValue
                CellType.NUMERIC -> {
                    val value = cell.numericCellValue
                    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
                }
                else -> ""
            }
        }

        fun getLong(index: Int): Long {
            val cell = row.getCell(index)
            return when (cell?.cellType) {
                CellType.NUMERIC -> cell.numericCellValue.toLong()
                CellType.STRING -> cell.stringCellValue.replace(",", "").toLongOrNull() ?: 0L
                else -> 0L
            }
        }

        fun getDouble(index: Int): Double {
            val cell = row.getCell(index)
            return when (cell?.cellType) {
                CellType.NUMERIC -> cell.numericCellValue
                CellType.STRING -> cell.stringCellValue.replace(",", "").toDoubleOrNull() ?: 0.0
                else -> 0.0
            }
        }
    }
}
