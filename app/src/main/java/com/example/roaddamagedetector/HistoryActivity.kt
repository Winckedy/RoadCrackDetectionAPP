package com.example.roaddamagedetector

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.roaddamagedetector.databinding.ActivityHistoryBinding
import com.google.android.material.datepicker.MaterialDatePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private val database by lazy { AppDatabase.getDatabase(this) }
    private lateinit var historyRecordDao: HistoryRecordDao
    private lateinit var historyAdapter: HistoryAdapter

    private val startCalendar: Calendar = Calendar.getInstance()
    private val endCalendar: Calendar = Calendar.getInstance()
    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.history_title)

        historyRecordDao = database.historyRecordDao()
        setupRecyclerView()

        historyRecordDao.getAllRecords().observe(this, Observer { records ->
            historyAdapter.updateRecords(records)
            binding.emptyView.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
            binding.historyRecyclerView.visibility = if (records.isEmpty()) View.GONE else View.VISIBLE
        })
    }

    private fun setupRecyclerView() {
        historyAdapter = HistoryAdapter(this, emptyList())
        binding.historyRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@HistoryActivity)
            adapter = historyAdapter
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.history_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_export -> {
                showExportOptionsDialog()
                true
            }
            R.id.action_clear_history -> {
                showClearHistoryOptionsDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showExportOptionsDialog() {
        val options = arrayOf(
            getString(R.string.export_all_records),
            getString(R.string.export_by_time_range)
        )
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.export_options_title))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showFormatSelectionDialog(null, null)
                    1 -> showTimeRangePickerDialog()
                }
            }
            .show()
    }

    private fun showTimeRangePickerDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_time_range_picker, null)
        val btnStartTime = dialogView.findViewById<Button>(R.id.btnStartTime)
        val btnEndTime = dialogView.findViewById<Button>(R.id.btnEndTime)

        // 初始化按钮文本
        updateDateTimeButton(btnStartTime, startCalendar, getString(R.string.start_time_label))
        updateDateTimeButton(btnEndTime, endCalendar, getString(R.string.end_time_label))

        // 设置点击事件
        btnStartTime.setOnClickListener {
            pickDateTime(startCalendar) {
                updateDateTimeButton(btnStartTime, startCalendar, getString(R.string.start_time_label))
            }
        }
        btnEndTime.setOnClickListener {
            pickDateTime(endCalendar) {
                updateDateTimeButton(btnEndTime, endCalendar, getString(R.string.end_time_label))
            }
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.time_range_picker_title))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.export_button)) { _, _ ->
                if (startCalendar.timeInMillis >= endCalendar.timeInMillis) {
                    Toast.makeText(this, getString(R.string.toast_end_time_before_start_time), Toast.LENGTH_SHORT).show()
                } else {
                    showFormatSelectionDialog(startCalendar.timeInMillis, endCalendar.timeInMillis)
                }
            }
            .setNegativeButton(getString(R.string.cancel_button), null)
            .show()
    }

    private fun pickDateTime(calendar: Calendar, onDateTimeSet: () -> Unit) {
        val dateSetListener = DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, month)
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)

            val timeSetListener = TimePickerDialog.OnTimeSetListener { _, hourOfDay, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                calendar.set(Calendar.MINUTE, minute)
                onDateTimeSet()
            }

            TimePickerDialog(
                this, timeSetListener,
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE), true
            ).show()
        }

        DatePickerDialog(
            this, dateSetListener,
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun updateDateTimeButton(button: Button, calendar: Calendar, prefix: String) {
        button.text = getString(R.string.time_picker_button_format, prefix, dateTimeFormat.format(calendar.time))
    }

    private fun showFormatSelectionDialog(startTime: Long?, endTime: Long?) {
        val options = arrayOf(
            getString(R.string.export_option_txt),
            getString(R.string.export_option_excel)
        )
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.export_dialog_title))
            .setItems(options) { _, which ->
                val format = if (which == 0) "TXT" else "Excel"
                performExport(format, startTime, endTime)
            }
            .show()
    }

    private fun performExport(format: String, startTime: Long? = null, endTime: Long? = null) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val records = if (startTime != null && endTime != null) {
                        historyRecordDao.getRecordsInRangeForExport(startTime, endTime)
                    } else {
                        historyRecordDao.getAllRecordsForExport()
                    }

                    if (records.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            val message = if (startTime != null) {
                                getString(R.string.toast_no_records_in_range)
                            } else {
                                getString(R.string.export_no_records)
                            }
                            Toast.makeText(this@HistoryActivity, message, Toast.LENGTH_SHORT).show()
                        }
                        return@withContext
                    }

                    val resultUri: Uri? = if (format == "TXT") {
                        DataExporter.exportToTxt(this@HistoryActivity, records)
                    } else {
                        DataExporter.exportToExcel(this@HistoryActivity, records)
                    }

                    withContext(Dispatchers.Main) {
                        if (resultUri != null) {
                            Toast.makeText(this@HistoryActivity, getString(R.string.export_success), Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this@HistoryActivity, getString(R.string.export_failed), Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@HistoryActivity, getString(R.string.export_error, e.message), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun showClearHistoryOptionsDialog() {
        val options = arrayOf(
            getString(R.string.clear_option_older_than_24h),
            getString(R.string.clear_option_all_records),
            getString(R.string.clear_option_by_date_range)
        )

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.clear_history_dialog_title))
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> { // 清理24小时前的记录
                        val calendar = Calendar.getInstance()
                        calendar.add(Calendar.DAY_OF_YEAR, -1)
                        showConfirmationDialog(message = getString(R.string.confirm_delete_older_than_24h)) {
                            clearRecordsOlderThan(calendar.timeInMillis)
                        }
                    }
                    1 -> { // 清理所有记录
                        showConfirmationDialog(message = getString(R.string.confirm_delete_all_records)) {
                            clearAllHistory()
                        }
                    }
                    2 -> { // 按日期范围清理
                        showDeleteDateRangePicker()
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel_button), null)
            .show()
    }

    private fun showDeleteDateRangePicker() {
        val dateRangePicker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText(getString(R.string.delete_date_range_picker_title))
            .build()

        dateRangePicker.show(supportFragmentManager, "DELETE_DATE_RANGE_PICKER")

        dateRangePicker.addOnPositiveButtonClickListener { selection ->
            val startDate = selection.first
            // 将结束时间戳设置为所选日期的最后一毫秒，以确保包含当天
            val endDate = selection.second + TimeUnit.DAYS.toMillis(1) - 1

            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val formattedStart = sdf.format(Date(startDate))
            val formattedEnd = sdf.format(Date(endDate))
            val confirmationMessage = getString(R.string.confirm_delete_date_range, formattedStart, formattedEnd)

            showConfirmationDialog(message = confirmationMessage) {
                clearRecordsInRange(startDate, endDate)
            }
        }
    }

    private fun showConfirmationDialog(message: String, onConfirm: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.action_confirmation_title))
            .setMessage(message)
            .setPositiveButton(getString(R.string.confirm_delete_button)) { dialog, _ ->
                onConfirm()
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel_button), null)
            .show()
    }

    private fun clearRecordsInRange(startTime: Long, endTime: Long) {
        lifecycleScope.launch(Dispatchers.IO) {
            val deletedCount = historyRecordDao.clearInRange(startTime, endTime)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@HistoryActivity, getString(R.string.toast_records_deleted_in_range, deletedCount), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun clearAllHistory() {
        lifecycleScope.launch(Dispatchers.IO) {
            val deletedCount = historyRecordDao.clearAll()
            withContext(Dispatchers.Main) {
                Toast.makeText(this@HistoryActivity, getString(R.string.toast_all_records_cleared, deletedCount), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun clearRecordsOlderThan(timestamp: Long) {
        lifecycleScope.launch(Dispatchers.IO) {
            val deletedCount = historyRecordDao.clearOlderThan(timestamp)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@HistoryActivity, getString(R.string.toast_old_records_deleted, deletedCount), Toast.LENGTH_SHORT).show()
            }
        }
    }
}
