package com.example.roaddamagedetector

import android.os.Build
import android.os.Bundle
import android.view.View // 新增导入
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.roaddamagedetector.databinding.ActivityDetailBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.detailToolbar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = getString(R.string.detail_title)

        val record = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("EXTRA_RECORD", HistoryRecord::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("EXTRA_RECORD") as? HistoryRecord
        }

        if (record != null) {
            Glide.with(this)
                .load(record.imagePath)
                .into(binding.detailImageView)

            binding.detailClassName.text = record.className
            binding.detailConfidence.text = getString(R.string.detail_confidence_label, record.confidence * 100)
            binding.detailProcessingTime.text = getString(R.string.detail_processing_time_label, record.processingTime)
            record.fps?.let {
                binding.detailFps.text = getString(R.string.detail_fps_label, it)
                binding.detailFps.visibility = View.VISIBLE
            } ?: run {
                binding.detailFps.visibility = View.GONE
            }
            binding.detailTimestamp.text = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(record.timestamp))
            val locationText = record.location ?: getString(R.string.detail_location_not_recorded)
            binding.detailLocation.text = getString(R.string.detail_location_label, locationText)
        }
    }
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
