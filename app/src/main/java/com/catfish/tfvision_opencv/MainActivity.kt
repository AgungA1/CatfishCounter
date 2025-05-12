package com.catfish.tfvision_opencv

import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.PopupMenu
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import com.catfish.tfvision_opencv.databinding.ActivityMainBinding
import org.opencv.android.BaseLoaderCallback
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.OpenCVLoader
import java.util.concurrent.TimeUnit

@SuppressLint("SourceLockedOrientationActivity")
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService

    private var objectDetection: ObjectDetection? = null
    private var objectTracker: ObjectTracker? = null
    private var frameCount = 0
    private var isStreaming = false

    private var imageAnalyzer: ImageAnalysis? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var camera: Camera? = null

    //Camera control for auto-focus
    private var cameraControl: CameraControl? = null

    // Performance tracking
    private var lastFpsTimestamp = System.currentTimeMillis()
    private var totalProcessingTime = 0L
    private var processedFrames = 0

    private val objectCounts = mutableMapOf("grade_a" to 0, "grade_b" to 0, "grade_c" to 0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.countsText.visibility = View.VISIBLE
        updateCountDisplay()

        if (!OpenCVLoader.initDebug()) {
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, mLoaderCallback)
        } else {
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS)
        }
    }

    private val mLoaderCallback = object : BaseLoaderCallback(this) {
        override fun onManagerConnected(status: Int) {
            when (status) {
                LoaderCallbackInterface.SUCCESS -> {
                    // OpenCV loaded successfully, initialize components
                    initializeComponents()
                    if (allPermissionsGranted()) {
                        setupCamera()
                    } else {
                        requestCameraPermission()
                    }
                }
                else -> {
                    super.onManagerConnected(status)
                }
            }
        }
    }

    private fun initializeComponents() {
        binding.graphicOverlay.setCountingLine(0.2f)

        objectDetection = ObjectDetection(this)

        objectTracker = ObjectTracker(binding.graphicOverlay.getCountingLineY()) { label ->
            runOnUiThread {
                Log.d(TAG, "Object with label $label crossed the counting line!")
                objectCounts[label] = (objectCounts[label] ?: 0) + 1
                updateCountDisplay()
            }
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.btnStartStream.setOnClickListener {
            if (!isStreaming) startObjectDetection() else stopObjectDetection()
        }

        // Setup accelerator dropdown button
        binding.btnAccelerator.text = "CPU"
        binding.btnAccelerator.setOnClickListener { view ->
            if (!isStreaming) {
                showAcceleratorMenu(view)
            } else {
                Toast.makeText(this, "Stop streaming before changing accelerator", Toast.LENGTH_SHORT).show()
            }
        }

        // Add reset button listener
        binding.btnReset.setOnClickListener {
            resetAll()
            Toast.makeText(this, "Counter and trackers reset", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAcceleratorMenu(view: View) {
        val popup = PopupMenu(this, view)
        popup.menu.add("CPU")
        popup.menu.add("GPU")
        popup.menu.add("NNAPI")

        popup.setOnMenuItemClickListener { item ->
            val accelerationType = when (item.title.toString()) {
                "GPU" -> AccelerationType.GPU
                "NNAPI" -> AccelerationType.NNAPI
                else -> AccelerationType.CPU
            }

            objectDetection?.changeAcceleration(accelerationType)
            binding.graphicOverlay.updateAcceleratorStatus(accelerationType)

            // Update button text to show current selection
            binding.btnAccelerator.text = item.title

            Toast.makeText(this, "${item.title} activated", Toast.LENGTH_SHORT).show()
            true
        }

        popup.show()
    }

    private fun setupCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            initializeCameraProvider()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun initializeCameraProvider() {
        val resolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
            .build()

        preview = Preview.Builder()
            .setResolutionSelector(resolutionSelector)
            .build()

        imageAnalyzer = ImageAnalysis.Builder()
            .setResolutionSelector(resolutionSelector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            cameraProvider?.unbindAll()
            camera = cameraProvider?.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalyzer
            )
            //Camera Focus
            cameraControl = camera?.cameraControl
            setupAutoFocus()

            preview?.setSurfaceProvider(binding.viewFinder.surfaceProvider)

        } catch (e: Exception) {
            Log.e(TAG, "Use case binding failed", e)
            showError("Camera setup failed")
        }
    }

    //Camera auto-focus
    private fun setupAutoFocus() {
        binding.viewFinder.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                triggerAutoFocus()
                return@setOnTouchListener true
            }
            return@setOnTouchListener false
        }
    }

    //Camera auto-focus
    private fun triggerAutoFocus() {
        if (cameraControl == null) {
            Log.e(TAG, "Cannot auto-focus, camera not initialized")
            return
        }

        try {
            val factory = SurfaceOrientedMeteringPointFactory(1f, 1f)
            val centerPoint = factory.createPoint(0.5f, 0.5f)
            val action = FocusMeteringAction.Builder(centerPoint)
                .setAutoCancelDuration(2, TimeUnit.SECONDS)
                .build()
            cameraControl?.startFocusAndMetering(action)
            Log.d(TAG, "Auto-focus triggered")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting auto-focus", e)
        }
    }

    private fun processFrame(imageProxy: ImageProxy) {
        val startTime = System.nanoTime()

        try {
            val frame = objectDetection?.imageProxyToMat(imageProxy) ?: run {
                imageProxy.close()
                return
            }

            val displayWidth = binding.viewFinder.width
            val displayHeight = binding.viewFinder.height
            val rotation = imageProxy.imageInfo.rotationDegrees

            binding.graphicOverlay.setFrameSize(frame.cols(), frame.rows())
            binding.graphicOverlay.setViewSize(displayWidth, displayHeight)
            binding.graphicOverlay.setCameraRotation(rotation)

            if (frameCount % 3 == 0) {
                val allDetections = objectDetection?.detectObjects(imageProxy) ?: emptyList()

                objectTracker?.initializeTrackers(frame, allDetections)

                val trackedObjects = objectTracker?.updateTrackers(frame, frame.cols(), frame.rows()) ?: emptyMap()

                runOnUiThread {
                    val trackedDetections = trackedObjects.values.map {
                        Detection(it.id, it.boundingBox, it.label, 1.0f, it.counted)
                    }

                    binding.graphicOverlay.updateDetections(trackedDetections)
                }
            } else {
                val trackedObjects = objectTracker?.updateTrackers(frame, frame.cols(), frame.rows()) ?: emptyMap()

                runOnUiThread {
                    val detections = trackedObjects.values.map {
                        Detection(it.id, it.boundingBox, it.label, 1.0f, it.counted)
                    }
                    binding.graphicOverlay.updateDetections(detections)
                }
            }

            frame.release()

            val processingTime = (System.nanoTime() - startTime) / 1_000_000
            totalProcessingTime += processingTime
            processedFrames++

            if (System.currentTimeMillis() - lastFpsTimestamp > 1000) {
                val avgLatency = totalProcessingTime / processedFrames.coerceAtLeast(1)
                updatePerformanceStats(avgLatency)
                lastFpsTimestamp = System.currentTimeMillis()
                totalProcessingTime = 0
                processedFrames = 0
            }

            frameCount++
        } catch (e: Exception) {
            Log.e(TAG, "Error processing frame", e)
        } finally {
            imageProxy.close()
        }
    }


    private fun updatePerformanceStats(lastFrameLatency: Long) {
        // Hitung FPS berdasarkan jumlah frame yang diproses dalam periode waktu tertentu
        val timeElapsed = System.currentTimeMillis() - lastFpsTimestamp
        val fps = if (timeElapsed > 0) (processedFrames * 1000.0 / timeElapsed).roundToInt() else 0

        // Perbarui UI
        runOnUiThread {
            binding.fpsText.text = "FPS: $fps"
            binding.msText.text = "Latency: $lastFrameLatency ms"

            // Tambahkan log untuk debug
            Log.d(TAG, "Performance updated: FPS=$fps, Latency=$lastFrameLatency ms")
        }
    }

    private fun updateCountDisplay() {
        val totalCount = objectCounts.values.sum()
        binding.countsText.text = """
        Total: $totalCount
        Grade A: ${objectCounts["grade_a"] ?: 0}       Grade B: ${objectCounts["grade_b"] ?: 0}       Grade C: ${objectCounts["grade_c"] ?: 0}
    """.trimIndent()

        // Tambahkan log untuk debug
        Log.d(TAG, "Updated counts: A=${objectCounts["grade_a"]}, B=${objectCounts["grade_b"]}, C=${objectCounts["grade_c"]}, Total=$totalCount")
    }

    private fun startObjectDetection() {
        try {
            binding.btnAccelerator.isEnabled = false

            imageAnalyzer?.setAnalyzer(cameraExecutor, ::processFrame)

            showDetectionUI()
            updateStreamingState(true)

            lastFpsTimestamp = System.currentTimeMillis()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start streaming", e)
            showError("Failed to start streaming")
        }
    }

    private fun stopObjectDetection() {
        try {
            binding.btnAccelerator.isEnabled = true

            imageAnalyzer?.clearAnalyzer()

            hideDetectionUI()
            updateStreamingState(false)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop streaming", e)
            showError("Failed to stop streaming")
        }
    }

    private fun resetAll() {
        // Reset counter objects
        objectCounts["grade_a"] = 0
        objectCounts["grade_b"] = 0
        objectCounts["grade_c"] = 0

        // Reset frame counter
        frameCount = 0

        // Reset tracker
        objectTracker?.reset()

        // Reset stats
        processedFrames = 0
        totalProcessingTime = 0
        lastFpsTimestamp = System.currentTimeMillis()

        // Update UI
        updateCountDisplay()

        // Update performance stats display
        binding.fpsText.text = "FPS: 0"
        binding.msText.text = "Latency: 0 ms"
    }

    private fun showDetectionUI() {
        with(binding) {
            graphicOverlay.visibility = View.VISIBLE
            fpsText.visibility = View.VISIBLE
            msText.visibility = View.VISIBLE
        }
    }

    private fun hideDetectionUI() {
        with(binding) {
            graphicOverlay.visibility = View.GONE
            fpsText.visibility = View.GONE
            msText.visibility = View.GONE
            // Keep acceleratorStatus visible
        }
    }

    private fun updateStreamingState(streaming: Boolean) {
        isStreaming = streaming
        binding.apply {
            btnStartStream.text = if (streaming) "Stop" else "Start"
            streamStatus.apply {
                text = if (streaming) "Detection Active" else "Detection Inactive"
                setTextColor(
                    ContextCompat.getColor(
                        context,
                        if (streaming) android.R.color.holo_green_light
                        else android.R.color.holo_red_light
                    )
                )
            }
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        binding.streamStatus.apply {
            text = "Error: $message"
            setTextColor(ContextCompat.getColor(context, android.R.color.holo_red_light))
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                setupCamera()
            } else {
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        objectDetection?.close()
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}