package com.example.roaddamagedetector

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.scale
import androidx.core.util.Consumer
import androidx.lifecycle.lifecycleScope
import com.example.roaddamagedetector.databinding.ActivityMainBinding
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import org.pytorch.torchvision.TensorImageUtils
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.system.measureTimeMillis

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var isRecording = false

    // 相机控制相关变量
    private var camera: Camera? = null
    private var cameraControl: CameraControl? = null
    private var cameraInfo: CameraInfo? = null

    // --- 计时器与定位相关变量 ---
    private var timerJob: Job? = null
    private var recordingTimeSeconds = 0
    private lateinit var locationManager: LocationManager
    private var locationListener: LocationListener? = null
    private val locationHistory = mutableListOf<Pair<Long, Location>>()
    private var videoStartTimestamp: Long = 0L
    private var videoStartLocation: Location? = null
    private var videoEndLocation: Location? = null

    // PyTorch 模型相关变量
    private var pytorchModule: Module? = null
    private var currentModelName: String = "MCANet-Tiny.ptl" // 默认模型名
    private val inputSize = 224

    private lateinit var classNames: Array<String>
    private val database by lazy { AppDatabase.getDatabase(this) }
    private var recordToSaveWithLocation: HistoryRecord? = null
    private var locationStatusMenuItem: MenuItem? = null
    private var isLocationReady = false

    enum class LocationStatus {
        LOADING, READY, NOT_READY, IDLE
    }

    private val locationPermissionLauncherForRecord =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            recordToSaveWithLocation?.let { record ->
                if (isGranted) {
                    fetchLocationAndSave(record)
                } else {
                    Toast.makeText(
                        this,
                        getString(R.string.location_permission_denied_save_without_location),
                        Toast.LENGTH_LONG
                    ).show()
                    saveRecordToDatabase(record)
                }
            }
            recordToSaveWithLocation = null
        }

    private var galleryImageUri: Uri? = null

    // 批量媒体选择器
    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50)) { uris ->
        if (uris.isNotEmpty()) {
            lifecycleScope.launch {
                processBatchMedia(uris)
            }
        } else {
            Log.d(TAG, getString(R.string.log_no_media_selected))
        }
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val granted = permissions.entries.all { it.value }
            if (granted) {
                startCamera()
                startLocationPreheating()
            } else {
                Toast.makeText(this, getString(R.string.permissions_required), Toast.LENGTH_LONG).show()
                updateLocationStatusIcon(LocationStatus.NOT_READY)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        classNames = arrayOf(
            getString(R.string.class_name_transverse_crack),
            getString(R.string.class_name_longitudinal_crack),
            getString(R.string.class_name_alligator_crack),
            getString(R.string.class_name_pothole),
            getString(R.string.class_name_normal)
        )
        setupClickListeners()

        // 权限检查
        binding.root.post {
            checkAndRequestPermissions()
        }

        // [修改] 初始化模型选择器 (替代之前的 loadPyTorchModel 直接调用)
        setupModelSelector()

        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
    }

    // [新增] 扫描 Assets 文件夹获取所有 .ptl 文件
    private fun getPtlModelsFromAssets(): List<String> {
        return try {
            assets.list("")?.filter { it.endsWith(".ptl") } ?: emptyList()
        } catch (e: IOException) {
            Log.e(TAG, "Failed to list assets", e)
            emptyList()
        }
    }

    // [新增] 设置下拉选择器并加载模型
    private fun setupModelSelector() {
        val models = getPtlModelsFromAssets()

        if (models.isNotEmpty()) {
            // 设置默认选中值
            if (!models.contains(currentModelName)) {
                currentModelName = models[0]
            }

            // 初始加载当前模型
            loadPyTorchModel(currentModelName)

            // 设置 Adapter
            val adapter = ArrayAdapter(
                this,
                android.R.layout.simple_dropdown_item_1line,
                models
            )

            // 绑定到 AutoCompleteTextView
            (binding.modelSelector as? AutoCompleteTextView)?.apply {
                setAdapter(adapter)
                setText(currentModelName, false) // false表示不触发过滤

                // 监听点击选择事件
                setOnItemClickListener { _, _, position, _ ->
                    val selectedModel = adapter.getItem(position)
                    if (selectedModel != null && selectedModel != currentModelName) {
                        currentModelName = selectedModel
                        Log.d(TAG, "User selected model: $currentModelName")
                        Toast.makeText(context, getString(R.string.switching_model, currentModelName), Toast.LENGTH_SHORT).show()
                        loadPyTorchModel(currentModelName) // 重新加载新模型
                    }
                }
            }
        } else {
            Log.e(TAG, "No .ptl models found in assets!")
            binding.modelSelectorLayout.visibility = View.GONE
            Toast.makeText(this, "No .ptl models found in assets!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        locationStatusMenuItem = menu.findItem(R.id.menu_location_status)
        updateLocationStatusIcon(LocationStatus.IDLE)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_history -> {
                val intent = Intent(this, HistoryActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        startLocationPreheating()
    }

    override fun onPause() {
        super.onPause()
        stopLocationPreheating()
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            startCamera()
        }
    }

    private suspend fun processBatchMedia(uris: List<Uri>) {
        showLoading(true)
        val total = uris.size
        var processedCount = 0

        try {
            for ((index, uri) in uris.withIndex()) {
                withContext(Dispatchers.Main) {
                    binding.resultText.text = getString(R.string.processing_batch_progress, index + 1, total)
                }

                val mimeType = contentResolver.getType(uri)

                if (mimeType != null && mimeType.startsWith("video/")) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, getString(R.string.analyzing_video_progress, index + 1, total), Toast.LENGTH_SHORT).show()
                    }
                    processVideo(uri, System.currentTimeMillis(), emptyList())
                } else {
                    try {
                        val bitmap: Bitmap = loadBitmapFromUri(uri)
                        val result = withContext(Dispatchers.Default) { analyzeImage(bitmap) }
                        saveBatchImageResult(uri, result)

                        if (index == total - 1) {
                            displayResult(result, bitmap)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, getString(R.string.log_error_processing_batch_item, uri), e)
                    }
                }
                processedCount++
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, getString(R.string.batch_analysis_complete_toast, processedCount), Toast.LENGTH_LONG).show()
                if (processedCount > 0) {
                    binding.resultText.text = getString(R.string.batch_analysis_complete_message)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, getString(R.string.log_batch_processing_error), e)
            withContext(Dispatchers.Main) {
                showError(getString(R.string.batch_processing_failed_error, e.message))
            }
        } finally {
            showLoading(false)
        }
    }

    private fun saveBatchImageResult(uri: Uri, result: Triple<Int, Float, Long>) {
        val (index, confidence, procTime) = result
        val className = classNames.getOrNull(index) ?: getString(R.string.unknown_class)
        val fps = if (procTime > 0) 1000f / procTime else null

        val record = HistoryRecord(
            imagePath = uri.toString(),
            className = className,
            confidence = confidence,
            timestamp = System.currentTimeMillis(),
            processingTime = procTime,
            fps = fps,
            recordType = "IMAGE",
            location = null
        )
        saveRecordToDatabase(record)
    }

    private fun toggleVideoRecording() {
        if (activeRecording != null) {
            stopVideoRecordingLocationUpdates()
            activeRecording?.stop()
            activeRecording = null

            timerJob?.cancel()
            binding.recordingTimerText.visibility = View.GONE
            binding.recordingTimerText.text = formatTime(0)

            binding.recordButton.setImageResource(R.drawable.ic_videocam)
            Toast.makeText(this, getString(R.string.recording_stopped), Toast.LENGTH_SHORT).show()
        } else {
            startVideoRecordingLocationUpdates()
            startRecording()
            isRecording = true

            recordingTimeSeconds = 0
            binding.recordingTimerText.visibility = View.VISIBLE
            timerJob = lifecycleScope.launch {
                while (isRecording) {
                    binding.recordingTimerText.text = formatTime(recordingTimeSeconds)
                    recordingTimeSeconds++
                    delay(1000)
                }
            }
            binding.recordButton.setImageResource(R.drawable.ic_stop_recording)
            Toast.makeText(this, getString(R.string.recording_started), Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateLocationStatusIcon(status: LocationStatus) {
        val menuItem = locationStatusMenuItem ?: return

        runOnUiThread {
            when (status) {
                LocationStatus.LOADING -> {
                    menuItem.isVisible = true
                    menuItem.setIcon(R.drawable.ic_location_searching)
                    menuItem.icon?.let { DrawableCompat.setTint(it, ContextCompat.getColor(this, R.color.purple_500)) }
                    isLocationReady = false
                }
                LocationStatus.READY -> {
                    menuItem.isVisible = true
                    menuItem.setIcon(R.drawable.ic_location_on)
                    menuItem.icon?.let { DrawableCompat.setTint(it, ContextCompat.getColor(this, R.color.green)) }
                    isLocationReady = true
                }
                LocationStatus.NOT_READY -> {
                    menuItem.isVisible = true
                    menuItem.setIcon(R.drawable.ic_location_off)
                    menuItem.icon?.let { DrawableCompat.setTint(it, ContextCompat.getColor(this, R.color.red_500)) }
                    isLocationReady = false
                }
                LocationStatus.IDLE -> {
                    menuItem.isVisible = false
                    isLocationReady = false
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationPreheating() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            updateLocationStatusIcon(LocationStatus.NOT_READY)
            return
        }
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            updateLocationStatusIcon(LocationStatus.NOT_READY)
            return
        }

        if (locationListener != null) return

        updateLocationStatusIcon(LocationStatus.LOADING)
        locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                Log.d(TAG, getString(R.string.log_preheating_location_received, formatLocation(location)))
                updateLocationStatusIcon(LocationStatus.READY)
            }
            @Suppress("DEPRECATION")
            override fun onProviderDisabled(provider: String) {
                if (provider == LocationManager.GPS_PROVIDER) updateLocationStatusIcon(LocationStatus.NOT_READY)
            }
            @Suppress("DEPRECATION")
            override fun onProviderEnabled(provider: String) {
                if (provider == LocationManager.GPS_PROVIDER) updateLocationStatusIcon(LocationStatus.LOADING)
            }
            @Suppress("DEPRECATION")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10000L, 5f, locationListener!!)
        Log.d(TAG, getString(R.string.log_location_preheating_started))
    }

    private fun stopLocationPreheating() {
        if (!isRecording) {
            locationListener?.let {
                locationManager.removeUpdates(it)
                locationListener = null
                Log.d(TAG, getString(R.string.log_location_preheating_stopped))
            }
            updateLocationStatusIcon(LocationStatus.IDLE)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startVideoRecordingLocationUpdates() {
        stopLocationPreheating()
        updateLocationStatusIcon(LocationStatus.LOADING)

        locationHistory.clear()
        videoStartTimestamp = System.currentTimeMillis()
        videoStartLocation = null

        locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                locationHistory.add(System.currentTimeMillis() to location)
                if (videoStartLocation == null) videoStartLocation = location
                updateLocationStatusIcon(LocationStatus.READY)
            }
            @Suppress("DEPRECATION")
            override fun onProviderDisabled(provider: String) {
                updateLocationStatusIcon(LocationStatus.NOT_READY)
            }
            @Suppress("DEPRECATION")
            override fun onProviderEnabled(provider: String) {
                updateLocationStatusIcon(LocationStatus.LOADING)
            }
            @Suppress("DEPRECATION")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 2f, locationListener!!)
    }

    private fun stopVideoRecordingLocationUpdates() {
        locationListener?.let {
            locationManager.removeUpdates(it)
            videoEndLocation = locationHistory.lastOrNull()?.second
            locationListener = null
        }
        startLocationPreheating()
    }

    private val videoRecordEventListener = Consumer<VideoRecordEvent> { event ->
        when (event) {
            is VideoRecordEvent.Start -> {
                Log.d(TAG, getString(R.string.log_recording_started))
            }
            is VideoRecordEvent.Finalize -> {
                isRecording = false
                timerJob?.cancel()
                binding.recordingTimerText.visibility = View.GONE
                binding.recordingTimerText.text = formatTime(0)
                binding.recordButton.setImageResource(R.drawable.ic_videocam)

                if (!event.hasError()) {
                    val uri = event.outputResults.outputUri
                    Log.d(TAG, getString(R.string.log_recording_success, uri))
                    Toast.makeText(this, getString(R.string.video_saved_starting_analysis), Toast.LENGTH_LONG).show()
                    val locationsForProcessing = locationHistory.toList()
                    saveVideoSummaryRecord(uri, videoStartLocation, videoEndLocation)
                    lifecycleScope.launch {
                        processVideo(uri, videoStartTimestamp, locationsForProcessing)
                    }
                } else {
                    activeRecording?.close()
                    activeRecording = null
                    Log.e(TAG, getString(R.string.log_recording_failed, event.error.toString()))
                }
            }
        }
    }

    private suspend fun processVideo(
        videoUri: Uri,
        startTimestamp: Long,
        locations: List<Pair<Long, Location>>
    ) = withContext(Dispatchers.IO) {
        withContext(Dispatchers.Main) {
            if (!binding.resultText.text.toString().startsWith(getString(R.string.processing_batch_prefix))) {
                binding.resultText.text = getString(R.string.analyzing_video_in_background)
            }
        }

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(this@MainActivity, videoUri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0
            if (durationMs == 0L) {
                Log.e(TAG, getString(R.string.error_video_has_no_duration))
                return@withContext
            }

            var processedFrames = 0
            val step = 1000L

            for (timeMs in 0 until durationMs step step) {
                val bitmap = retriever.getFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (bitmap != null) {
                    val (index, confidence, procTime) = analyzeImage(bitmap)
                    val className = classNames.getOrNull(index) ?: getString(R.string.unknown_class)

                    if (className != getString(R.string.class_name_normal)) {
                        val frameTimestamp = startTimestamp + timeMs
                        val closestLocation = findClosestLocation(frameTimestamp, locations)
                        saveFrameAsHistoryRecord(Triple(index, confidence, procTime), bitmap, closestLocation)
                        processedFrames++
                    }
                    bitmap.recycle()
                }
            }
            Log.d(TAG, getString(R.string.log_video_processed, videoUri.toString(), processedFrames))

        } catch (e: Exception) {
            Log.e(TAG, getString(R.string.error_processing_video), e)
        } finally {
            retriever.release()
        }
    }

    private fun findClosestLocation(timestamp: Long, locations: List<Pair<Long, Location>>): Location? {
        return locations.minByOrNull { abs(it.first - timestamp) }?.second
    }

    private fun formatLocation(location: Location?): String? {
        return location?.let { getString(R.string.location_format, it.longitude.toString(), it.latitude.toString()) }
    }

    private fun saveFrameAsHistoryRecord(result: Triple<Int, Float, Long>, sourceBitmap: Bitmap, location: Location?) {
        val (index, confidence, procTime) = result
        val className = classNames.getOrNull(index) ?: getString(R.string.unknown_class)
        val timestamp = System.currentTimeMillis()
        val displayName = "RoadDamage_Frame_$timestamp.jpg"
        val imageUri = saveBitmapToMediaStore(this@MainActivity, sourceBitmap, displayName)

        val fps = if (procTime > 0) 1000f / procTime else null

        if (imageUri != null) {
            val record = HistoryRecord(
                imagePath = imageUri.toString(),
                className = className,
                confidence = confidence,
                timestamp = timestamp,
                processingTime = procTime,
                fps = fps,
                recordType = "VIDEO_FRAME",
                location = formatLocation(location)
            )
            saveRecordToDatabase(record)
            Log.d(TAG, getString(R.string.log_video_frame_record_saved, record))
        }
    }

    private fun saveVideoSummaryRecord(uri: Uri, startLoc: Location?, endLoc: Location?) {
        lifecycleScope.launch(Dispatchers.IO) {
            val record = HistoryRecord(
                imagePath = uri.toString(),
                className = getString(R.string.video_summary),
                confidence = 1.0f,
                timestamp = videoStartTimestamp,
                processingTime = 0,
                fps = null,
                recordType = "VIDEO_SUMMARY",
                location = null,
                startLocation = formatLocation(startLoc),
                endLocation = formatLocation(endLoc)
            )
            saveRecordToDatabase(record)
            Log.d(TAG, getString(R.string.log_video_summary_record_saved, record))
        }
    }

    private fun displayResult(result: Triple<Int, Float, Long>, sourceBitmap: Bitmap) {
        val (index, confidence, procTime) = result
        val className = classNames.getOrNull(index) ?: getString(R.string.unknown_class)
        binding.resultText.text = getString(R.string.result_label, className)
        binding.confidenceText.text = String.format(Locale.US, getString(R.string.confidence_label), confidence * 100)

        lifecycleScope.launch(Dispatchers.IO) {
            val timestamp = System.currentTimeMillis()
            val imagePathForDb: String?

            if (galleryImageUri != null) {
                imagePathForDb = galleryImageUri.toString()
            } else {
                val displayName = "RoadDamage_$timestamp.jpg"
                val savedUri = saveBitmapToMediaStore(this@MainActivity, sourceBitmap, displayName)
                imagePathForDb = savedUri?.toString()
            }

            galleryImageUri = null
            val fps = if (procTime > 0) 1000f / procTime else null

            if (imagePathForDb != null) {
                val newRecord = HistoryRecord(
                    imagePath = imagePathForDb,
                    className = className,
                    confidence = confidence,
                    timestamp = timestamp,
                    processingTime = procTime,
                    fps = fps,
                    recordType = "IMAGE",
                    location = null
                )
                withContext(Dispatchers.Main) {
                    checkLocationPermissionAndSave(newRecord)
                }
            } else {
                Log.e(TAG, getString(R.string.image_path_processing_failed))
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, getString(R.string.image_path_processing_failed), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    @SuppressLint("MissingPermission", "NewApi")
    private fun fetchLocationAndSave(record: HistoryRecord) {
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        if (!isGpsEnabled) {
            Toast.makeText(this, getString(R.string.gps_disabled_save_without_location), Toast.LENGTH_LONG).show()
            saveRecordToDatabase(record)
            updateLocationStatusIcon(LocationStatus.NOT_READY)
            return
        }

        updateLocationStatusIcon(LocationStatus.LOADING)

        val locationDeferred = CompletableDeferred<Location?>()
        var locationListenerForSingleUpdate: LocationListener? = null

        lifecycleScope.launch {
            val timeoutJob = launch {
                delay(3000)
                if (!locationDeferred.isCompleted) {
                    locationDeferred.complete(null)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, getString(R.string.location_timeout_save_without_location), Toast.LENGTH_SHORT).show()
                        updateLocationStatusIcon(LocationStatus.NOT_READY)
                    }
                }
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    locationManager.getCurrentLocation(
                        LocationManager.GPS_PROVIDER,
                        null,
                        ContextCompat.getMainExecutor(this@MainActivity)
                    ) { location: Location? ->
                        if (!locationDeferred.isCompleted) {
                            locationDeferred.complete(location)
                            timeoutJob.cancel()
                        }
                    }
                } else {
                    locationListenerForSingleUpdate = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            if (!locationDeferred.isCompleted) {
                                locationDeferred.complete(location)
                                timeoutJob.cancel()
                            }
                            locationManager.removeUpdates(this)
                        }
                        @Suppress("DEPRECATION")
                        override fun onProviderDisabled(provider: String) {}
                        @Suppress("DEPRECATION")
                        override fun onProviderEnabled(provider: String) {}
                        @Suppress("DEPRECATION")
                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    }
                    @Suppress("DEPRECATION")
                    locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, locationListenerForSingleUpdate, mainLooper)
                }

                val receivedLocation = locationDeferred.await()
                val finalRecord = record.copy(location = formatLocation(receivedLocation))
                saveRecordToDatabase(finalRecord)
                updateLocationStatusIcon(if (receivedLocation != null) LocationStatus.READY else LocationStatus.NOT_READY)

                startLocationPreheating()

            } catch (e: Exception) {
                Log.e(TAG, getString(R.string.log_requesting_location_failed), e)
                if (!locationDeferred.isCompleted) {
                    locationDeferred.complete(null)
                    timeoutJob.cancel()
                }
                saveRecordToDatabase(record)
                updateLocationStatusIcon(LocationStatus.NOT_READY)
                startLocationPreheating()
            } finally {
                locationListenerForSingleUpdate?.let {
                    @Suppress("DEPRECATION")
                    locationManager.removeUpdates(it)
                }
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = binding.cameraPreview.surfaceProvider
                }
                imageCapture = ImageCapture.Builder().build()
                val recorder = Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.HD)).build()
                videoCapture = VideoCapture.withOutput(recorder)
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                cameraProvider.unbindAll()

                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, videoCapture)
                cameraControl = camera?.cameraControl
                cameraInfo = camera?.cameraInfo
                setupZoomAndFocus()

            } catch (e: Exception) {
                Log.e(TAG, getString(R.string.error_camera_init_failed), e)
                Toast.makeText(this, getString(R.string.error_camera_start_failed, e.message), Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupZoomAndFocus() {
        val listener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val currentZoomRatio = cameraInfo?.zoomState?.value?.zoomRatio ?: 1f
                val delta = detector.scaleFactor
                val newZoomRatio = (currentZoomRatio * delta).coerceIn(
                    cameraInfo?.zoomState?.value?.minZoomRatio ?: 1f,
                    cameraInfo?.zoomState?.value?.maxZoomRatio ?: 1f
                )
                cameraControl?.setZoomRatio(newZoomRatio)
                return true
            }
        }

        val scaleGestureDetector = ScaleGestureDetector(this, listener)

        binding.cameraPreview.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)

            if (event.action == MotionEvent.ACTION_DOWN) {
                val factory = SurfaceOrientedMeteringPointFactory(
                    binding.cameraPreview.width.toFloat(),
                    binding.cameraPreview.height.toFloat()
                )
                val point = factory.createPoint(event.x, event.y)
                val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                    .setAutoCancelDuration(5, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                cameraControl?.startFocusAndMetering(action)
            }
            true
        }
    }

    private fun setupClickListeners() {
        binding.captureButton.setOnClickListener {
            lifecycleScope.launch { captureAndAnalyze() }
        }
        binding.recordButton.setOnClickListener {
            toggleVideoRecording()
        }
        binding.selectImageButton.setOnClickListener {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
        }
    }

    private fun formatTime(seconds: Int): String {
        val minutes = seconds / 60
        val secs = seconds % 60
        return String.format(Locale.US, "%02d:%02d", minutes, secs)
    }

    @SuppressLint("MissingPermission")
    private fun startRecording() {
        val vc = videoCapture ?: return
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "RoadDamage-Video-$timestamp")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/RoadDamageDetector")
        }
        val mediaStoreOutputOptions = MediaStoreOutputOptions.Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()
        activeRecording = vc.output
            .prepareRecording(this, mediaStoreOutputOptions)
            .withAudioEnabled(false)
            .start(ContextCompat.getMainExecutor(this), videoRecordEventListener)
    }

    private suspend fun takePictureAsBitmap(): Bitmap {
        val imageCapture = this.imageCapture ?: throw IllegalStateException(getString(R.string.error_camera_not_initialized))
        return imageCapture.takePictureAsBitmap(ContextCompat.getMainExecutor(this))
    }

    private suspend fun ImageCapture.takePictureAsBitmap(executor: Executor): Bitmap =
        suspendCancellableCoroutine { continuation ->
            takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        continuation.resume(bitmap)
                    } catch (e: Exception) {
                        continuation.resumeWithException(e)
                    } finally {
                        image.close()
                    }
                }
                override fun onError(exception: ImageCaptureException) {
                    continuation.resumeWithException(exception)
                }
            })
        }

    private fun saveRecordToDatabase(record: HistoryRecord?) {
        if (record == null) return

        lifecycleScope.launch(Dispatchers.IO) {
            val existingRecord = database.historyRecordDao().getRecordByTimestamp(record.timestamp)
            if (existingRecord != null && existingRecord.location != null) {
                Log.d(TAG, getString(R.string.log_record_already_exists_with_location, record.timestamp))
                return@launch
            }

            try {
                if (existingRecord != null && record.location != null) {
                    database.historyRecordDao().update(record)
                    Log.d(TAG, getString(R.string.log_record_updated_with_location, record.toString()))
                } else if (existingRecord == null) {
                    database.historyRecordDao().insert(record)
                    Log.d(TAG, getString(R.string.log_record_saved_or_updated, record.toString()))
                } else {
                    Log.d(TAG, getString(R.string.log_record_exists_without_location_not_updating, record.timestamp))
                }
            } catch (e: Exception) {
                Log.e(TAG, getString(R.string.log_saving_record_failed), e)
            }
        }
    }

    fun saveBitmapToMediaStore(context: Context, bitmap: Bitmap, displayName: String): Uri? {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        var uri: Uri? = null
        try {
            uri = resolver.insert(collection, values)
            uri?.let {
                resolver.openOutputStream(it)?.use { outputStream ->
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)) {
                        throw IOException(context.getString(R.string.error_failed_to_save_bitmap))
                    }
                }
            } ?: throw IOException(context.getString(R.string.error_failed_to_create_mediastore_record))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
        } catch (e: Exception) {
            uri?.let { orphanUri -> resolver.delete(orphanUri, null, null) }
            uri = null
            Log.e(TAG, context.getString(R.string.log_save_bitmap_failed, e.message), e)
            lifecycleScope.launch(Dispatchers.Main) {
                Toast.makeText(context, context.getString(R.string.error_saving_image), Toast.LENGTH_SHORT).show()
            }
        }
        return uri
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap {
        val targetSize = 224

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val size = info.size
                var sampleSize = 1
                if (size.width > targetSize || size.height > targetSize) {
                    val halfHeight = size.height / 2
                    val halfWidth = size.width / 2
                    while ((halfHeight / sampleSize) >= targetSize && (halfWidth / sampleSize) >= targetSize) {
                        sampleSize *= 2
                    }
                }
                decoder.setTargetSampleSize(sampleSize)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = true
            }
        } else {
            contentResolver.openInputStream(uri)?.use { stream ->
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(stream, null, options)

                options.inSampleSize = calculateInSampleSize(options, targetSize, targetSize)
                options.inJustDecodeBounds = false
                options.inPreferredConfig = Bitmap.Config.ARGB_8888

                contentResolver.openInputStream(uri)?.use { validStream ->
                    BitmapFactory.decodeStream(validStream, null, options)
                }
            } ?: throw IOException(getString(R.string.error_failed_to_load_bitmap_from_uri))
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    companion object {
        private const val TAG = "RoadDamageDetector"
        // MODEL_NAME 已经移除
    }

    // [修改] 接受文件名参数的模型加载方法
    private fun loadPyTorchModel(modelName: String) {
        runOnUiThread { showLoading(true) }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val filePath = assetFilePath(modelName)
                pytorchModule = LiteModuleLoader.load(filePath)

                val dummyTensor = org.pytorch.Tensor.fromBlob(
                    FloatArray(1 * 3 * inputSize * inputSize) { 0.0f },
                    longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong())
                )
                pytorchModule?.forward(IValue.from(dummyTensor))

                Log.d(TAG, getString(R.string.log_model_loaded_successfully) + ": " + modelName)

                withContext(Dispatchers.Main) {
                    showLoading(false)
                    // 仅提示加载成功，不频繁打断用户
                }
            } catch (e: Exception) {
                Log.e(TAG, getString(R.string.log_model_load_failed) + ": " + modelName, e)
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    showError("Failed to load $modelName")
                }
            }
        }
    }

    // [修改] 接受文件名参数的路径获取方法
    @Throws(IOException::class)
    private fun assetFilePath(modelName: String): String {
        val file = File(cacheDir, modelName)
        // 检查文件是否存在且大小不为0，否则覆盖
        if (!file.exists() || file.length() == 0L) {
            assets.open(modelName).use { inputStream ->
                file.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }
        return file.absolutePath
    }

    private suspend fun captureAndAnalyze() {
        if (imageCapture == null) { showError(getString(R.string.error_camera_not_initialized)); return }
        if (pytorchModule == null) { showError(getString(R.string.error_model_not_loaded)); return }
        try {
            showLoading(true)
            galleryImageUri = null
            val bitmap = takePictureAsBitmap()
            val result = withContext(Dispatchers.Default) { analyzeImage(bitmap) }
            displayResult(result, bitmap)
        } catch (e: Exception) {
            Log.e(TAG, getString(R.string.error_processing_failed), e)
            showError(getString(R.string.error_processing_failed_with_message, e.message))
        }  finally {
            showLoading(false)
        }
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val maxLogit = logits.maxOrNull() ?: 0.0f
        val expValues = logits.map { kotlin.math.exp(it - maxLogit) }
        val sumExpValues = expValues.sum()
        if (sumExpValues == 0.0f) {
            return FloatArray(logits.size) { 1.0f / logits.size }
        }
        return expValues.map { it / sumExpValues }.toFloatArray()
    }

    private fun analyzeImage(bitmap: Bitmap): Triple<Int, Float, Long> {
        try {
            val module = pytorchModule ?: run {
                Log.e(TAG, getString(R.string.error_model_not_loaded_for_analysis))
                return Triple(0, 0.0f, 0L)
            }

            val resizedBitmap = bitmap.scale(inputSize, inputSize, true)
            val finalBitmap = if (resizedBitmap.config != Bitmap.Config.ARGB_8888) {
                resizedBitmap.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                resizedBitmap
            }

            val inputTensor = TensorImageUtils.bitmapToFloat32Tensor(
                finalBitmap,
                TensorImageUtils.TORCHVISION_NORM_MEAN_RGB,
                TensorImageUtils.TORCHVISION_NORM_STD_RGB
            )

            val outputTensor: org.pytorch.Tensor
            val inferenceTime = measureTimeMillis {
                outputTensor = module.forward(IValue.from(inputTensor)).toTensor()
            }

            val logits = outputTensor.dataAsFloatArray
            val probabilities = softmax(logits)

            val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: -1
            val maxProb = if (maxIndex != -1) probabilities[maxIndex] else 0.0f

            return Triple(maxIndex, maxProb, inferenceTime)

        } catch (e: Exception) {
            Log.e(TAG, getString(R.string.log_error_analyzing_image), e)
            return Triple(0, 0.0f, 0L)
        }
    }

    private fun showError(message: String) {
        binding.resultText.text = getString(R.string.error_label, message)
        binding.confidenceText.text = ""
        binding.loadingIndicator.visibility = View.GONE
    }

    private fun showLoading(isLoading: Boolean) {
        binding.loadingIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
        if (isLoading) {
            if (!binding.resultText.text.toString().contains("/")) {
                binding.resultText.text = getString(R.string.processing)
            }
            binding.confidenceText.text = ""
        }
    }

    private fun checkLocationPermissionAndSave(record: HistoryRecord) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fetchLocationAndSave(record)
        } else {
            recordToSaveWithLocation = record
            locationPermissionLauncherForRecord.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
}