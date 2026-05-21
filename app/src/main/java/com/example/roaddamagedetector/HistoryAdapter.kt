package com.example.roaddamagedetector

import android.annotation.SuppressLint
import android.content.Context // 1. 导入 Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.net.toUri
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 2. 在构造函数中接收 Context
class HistoryAdapter(private val context: Context, private var records: List<HistoryRecord>) :
    RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    class HistoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.itemImageView)
        val classNameView: TextView = view.findViewById(R.id.itemClassName)
        val confidenceView: TextView = view.findViewById(R.id.itemConfidence)
        val timestampView: TextView = view.findViewById(R.id.itemTimestamp)
        val processingTimeView: TextView = view.findViewById(R.id.itemProcessingTime)
        val fpsView: TextView = view.findViewById(R.id.itemFps)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val record = records[position]

        holder.classNameView.text = record.className

        holder.confidenceView.text = context.getString(R.string.history_item_confidence, record.confidence * 100)
        holder.processingTimeView.text = context.getString(R.string.history_item_processing_time, record.processingTime)

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        holder.timestampView.text = sdf.format(Date(record.timestamp))
        record.fps?.let { fpsValue ->
            holder.fpsView.text = context.getString(R.string.history_item_fps, fpsValue)
            holder.fpsView.visibility = View.VISIBLE
        } ?: run {
            holder.fpsView.visibility = View.GONE
        }
        Glide.with(context)
            .load(record.imagePath.toUri())
            .placeholder(R.drawable.ic_placeholder)
            .error(R.drawable.ic_broken_image)
            .into(holder.imageView)

        holder.itemView.setOnClickListener {
            val intent = Intent(context, DetailActivity::class.java).apply {
                putExtra("EXTRA_RECORD", record)
            }
            context.startActivity(intent)
        }
    }

    override fun getItemCount() = records.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateRecords(newRecords: List<HistoryRecord>) {
        this.records = newRecords
        notifyDataSetChanged()
    }
}
