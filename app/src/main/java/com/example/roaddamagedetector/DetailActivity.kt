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

        // 1. 设置 Toolbar
        setSupportActionBar(binding.detailToolbar)

        // 2. 在 Toolbar 上显示返回箭头
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = getString(R.string.detail_title)

        // 获取传递过来的数据
        val record = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("EXTRA_RECORD", HistoryRecord::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("EXTRA_RECORD") as? HistoryRecord
        }

        if (record != null) {
            // 使用 Glide 加载图片
            Glide.with(this)
                .load(record.imagePath)
                .into(binding.detailImageView)

            // 填充文本数据
            binding.detailClassName.text = record.className
            binding.detailConfidence.text = getString(R.string.detail_confidence_label, record.confidence * 100)
            binding.detailProcessingTime.text = getString(R.string.detail_processing_time_label, record.processingTime)
            // --- 新增：显示 FPS ---
            // 仅当 FPS 值存在时才显示
            record.fps?.let {
                binding.detailFps.text = getString(R.string.detail_fps_label, it)
                binding.detailFps.visibility = View.VISIBLE // 设为可见
            } ?: run {
                binding.detailFps.visibility = View.GONE // 确保在没有值时隐藏
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
