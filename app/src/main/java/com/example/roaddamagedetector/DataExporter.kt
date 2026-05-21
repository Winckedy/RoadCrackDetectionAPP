package com.example.roaddamagedetector

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DataExporter {

    private const val TAG = "DataExporter"

    suspend fun exportToTxt(context: Context, records: List<HistoryRecord>): Uri? {
        val content = buildTxtContent(context, records)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "HistoryData_$timestamp.txt"
        return saveFile(context, fileName, "text/plain", content.toByteArray())
    }

    suspend fun exportToExcel(context: Context, records: List<HistoryRecord>): Uri? {
        val workbook = buildExcelWorkbook(context, records)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "HistoryData_$timestamp.xlsx"

        val byteArray = withContext(Dispatchers.IO) {
            try {
                java.io.ByteArrayOutputStream().use { stream ->
                    workbook.write(stream)
                    stream.toByteArray()
                }
            } catch (e: IOException) {
                Log.e(TAG, "Failed to write workbook to byte array", e)
                null
            }
        }

        return if (byteArray != null) {
            saveFile(context, fileName, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", byteArray)
        } else {
            null
        }
    }

    private fun buildTxtContent(context: Context, records: List<HistoryRecord>): String {
        val builder = StringBuilder()
        val header = listOf(
            context.getString(R.string.export_header_timestamp),
            context.getString(R.string.export_header_class_name),
            context.getString(R.string.export_header_confidence),
            context.getString(R.string.export_header_processing_time),
            context.getString(R.string.export_header_fps),
            context.getString(R.string.export_header_location)
        ).joinToString("\t")
        builder.append(header).append("\n")

        records.forEach { record ->
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(record.timestamp))
            val confidence = String.format(Locale.US, "%.2f", record.confidence * 100)
            val fps = record.fps?.let { String.format(Locale.US, "%.2f", it) } ?: context.getString(R.string.export_data_not_available)
            val location = record.location ?: context.getString(R.string.export_data_not_available)
            builder.append("$timestamp\t${record.className}\t$confidence\t${record.processingTime}\t$fps\t$location\n")
        }
        return builder.toString()
    }


    private fun buildExcelWorkbook(context: Context, records: List<HistoryRecord>): Workbook {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet(context.getString(R.string.export_excel_sheet_name))

        val headerRow = sheet.createRow(0)
        val headers = listOf(
            context.getString(R.string.export_header_timestamp),
            context.getString(R.string.export_header_class_name),
            context.getString(R.string.export_header_confidence),
            context.getString(R.string.export_header_processing_time),
            context.getString(R.string.export_header_fps),
            context.getString(R.string.export_header_location)
        )
        headers.forEachIndexed { index, header ->
            headerRow.createCell(index).setCellValue(header)
        }

        records.forEachIndexed { index, record ->
            val dataRow = sheet.createRow(index + 1)
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(record.timestamp))
            val confidence = (record.confidence * 100).toDouble()
            val location = record.location ?: context.getString(R.string.export_data_not_available)

            dataRow.createCell(0).setCellValue(timestamp)
            dataRow.createCell(1).setCellValue(record.className)
            dataRow.createCell(2).setCellValue(confidence)
            dataRow.createCell(3).setCellValue(record.processingTime.toDouble())
            val fpsCellIndex = 4
            record.fps?.let {
                dataRow.createCell(fpsCellIndex).setCellValue(it.toDouble())
            } ?: dataRow.createCell(fpsCellIndex).setCellValue(context.getString(R.string.export_data_not_available))

            dataRow.createCell(5).setCellValue(location)
        }
        return workbook
    }

    private suspend fun saveFile(context: Context, fileName: String, mimeType: String, content: ByteArray): Uri? {
        return withContext(Dispatchers.IO) {
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Files.getContentUri("external")
            }

            val values = ContentValues().apply {
                put(MediaStore.Files.FileColumns.DISPLAY_NAME, fileName)
                put(MediaStore.Files.FileColumns.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            var uri: Uri? = null

            try {
                uri = resolver.insert(collection, values)
                uri?.let {
                    resolver.openOutputStream(it)?.use { outputStream ->
                        outputStream.write(content)
                    }
                } ?: throw IOException("Failed to create new MediaStore record.")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
                Log.d(TAG, "File saved successfully: $uri")
                uri
            } catch (e: Exception) {
                uri?.let { orphanUri -> resolver.delete(orphanUri, null, null) }
                Log.e(TAG, "Failed to save file", e)
                null
            }
        }
    }
}
