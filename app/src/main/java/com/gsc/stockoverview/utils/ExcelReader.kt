package com.gsc.stockoverview.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream

/**
 * 엑셀 파일을 읽기 위한 유틸리티 클래스
 */
class ExcelReader(private val context: Context) {

    /**
     * 엑셀 파일의 여러 시트를 한 번에 읽어 메모리 효율을 높이고 URI 권한 문제를 방지합니다.
     */
    fun readAllSheets(uri: Uri, sheetNames: List<String>): Map<String, List<RowReader>> {
        val result = mutableMapOf<String, List<RowReader>>()
        var inputStream: InputStream? = null
        try {
            inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) return emptyMap()

            XSSFWorkbook(inputStream).use { workbook ->
                val evaluator = workbook.creationHelper.createFormulaEvaluator()
                
                for (name in sheetNames) {
                    val sheet = workbook.getSheet(name)
                    if (sheet != null) {
                        val rowReaders = mutableListOf<RowReader>()
                        // 헤더 제외하고 데이터 행만 읽음 (1행부터 시작)
                        for (i in 1..sheet.lastRowNum) {
                            val row = sheet.getRow(i) ?: continue
                            rowReaders.add(RowReader(row, evaluator))
                        }
                        result[name] = rowReaders
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ExcelReader", "Error reading Excel: ${e.message}", e)
        } finally {
            try { inputStream?.close() } catch (e: Exception) {}
        }
        return result
    }

    /**
     * 특정 시트 하나만 읽어오는 기존 방식 (안정성 강화 버전)
     */
    fun <T> readExcelSheet(
        uri: Uri,
        sheetName: String,
        skipHeader: Boolean = true,
        rowMapper: (RowReader) -> T?
    ): List<T> {
        val results = mutableListOf<T>()
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                XSSFWorkbook(stream).use { workbook ->
                    val sheet = workbook.getSheet(sheetName) ?: return@use
                    val evaluator = workbook.creationHelper.createFormulaEvaluator()
                    val startRow = if (skipHeader) 1 else 0

                    for (i in startRow..sheet.lastRowNum) {
                        val row = sheet.getRow(i) ?: continue
                        try {
                            val item = rowMapper(RowReader(row, evaluator))
                            if (item != null) results.add(item)
                        } catch (e: Exception) {
                            Log.e("ExcelReader", "Error mapping row $i", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ExcelReader", "Failed to read sheet $sheetName", e)
        }
        return results
    }

    /**
     * 엑셀 행의 데이터를 안전하게 읽기 위한 래퍼 클래스
     */
    class RowReader(private val row: Row, private val evaluator: FormulaEvaluator) {
        
        fun getString(index: Int): String {
            val cell = row.getCell(index) ?: return ""
            return try {
                when (cell.cellType) {
                    CellType.STRING -> cell.stringCellValue
                    CellType.NUMERIC -> {
                        val value = cell.numericCellValue
                        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
                    }
                    CellType.FORMULA -> {
                        val cellValue = evaluator.evaluate(cell)
                        when (cellValue.cellType) {
                            CellType.STRING -> cellValue.stringValue
                            CellType.NUMERIC -> cellValue.numberValue.toString()
                            else -> ""
                        }
                    }
                    else -> ""
                }
            } catch (e: Exception) { "" }
        }

        fun getLong(index: Int): Long {
            val cell = row.getCell(index) ?: return 0L
            return try {
                when (cell.cellType) {
                    CellType.NUMERIC -> cell.numericCellValue.toLong()
                    CellType.STRING -> cell.stringCellValue.replace(",", "").toDoubleOrNull()?.toLong() ?: 0L
                    CellType.FORMULA -> evaluator.evaluate(cell).numberValue.toLong()
                    else -> 0L
                }
            } catch (e: Exception) { 0L }
        }

        fun getDouble(index: Int): Double {
            val cell = row.getCell(index) ?: return 0.0
            return try {
                when (cell.cellType) {
                    CellType.NUMERIC -> cell.numericCellValue
                    CellType.STRING -> cell.stringCellValue.replace(",", "").toDoubleOrNull() ?: 0.0
                    CellType.FORMULA -> evaluator.evaluate(cell).numberValue
                    else -> 0.0
                }
            } catch (e: Exception) { 0.0 }
        }
    }
}
