package com.catfish.tfvision_opencv

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageProxy
import com.catfish.tfvision_opencv.Utils.calculateIoU
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Rect2d
import org.opencv.imgproc.Imgproc
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.vision.detector.Detection as TFLiteDetection
import org.tensorflow.lite.task.vision.detector.ObjectDetector

enum class AccelerationType {
    CPU, GPU, NNAPI
}

class ObjectDetection(private val context: Context) {
    private var objectDetector: ObjectDetector? = null
    private var currentAcceleration = AccelerationType.CPU

    private val DETECTION_THRESHOLD = 0.7f
    private val MAX_DETECTIONS = 35
    var nmsIoUThreshold = 0.4

    init {
        loadModel(AccelerationType.CPU)
    }

    private fun loadModel(accelerationType: AccelerationType) {
        try {
            // Close previous detector if exists
            objectDetector?.close()

            // Setup base options with selected acceleration
            val baseOptionsBuilder = BaseOptions.builder()

            when (accelerationType) {
                AccelerationType.CPU -> {
                    baseOptionsBuilder.setNumThreads(4)
                }
                AccelerationType.GPU -> {
                    if (CompatibilityList().isDelegateSupportedOnThisDevice){
                        baseOptionsBuilder.useGpu()
                    } else {
                        Log.e(TAG, "GPU acceleration not supported on this device")
                        return
                    }
                }
                AccelerationType.NNAPI -> {
                    baseOptionsBuilder.useNnapi()
                }
            }

            // Build detector options
            val options = ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(baseOptionsBuilder.build())
                .setMaxResults(MAX_DETECTIONS)
                .setScoreThreshold(DETECTION_THRESHOLD)
                .build()

            // Create the detector
            objectDetector = ObjectDetector.createFromFileAndOptions(
                context,
                "detect_metadata.tflite",
                options
            )

            // Update current acceleration type
            currentAcceleration = accelerationType

            Log.d(TAG, "Model loaded with acceleration: $accelerationType")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading model", e)
        }
    }

    fun detectObjects(imageProxy: ImageProxy): List<Detection> {
        val mat = imageProxyToMat(imageProxy)
        val bitmap = matToBitmap(mat)
        mat.release()

        // Run detection using Task Library with proper TensorImage
        val tensorImage = TensorImage.fromBitmap(bitmap)
        val tfLiteDetections = objectDetector?.detect(tensorImage) ?: emptyList()

        // Convert TFLite detections to our app's Detection model
        return processDetections(tfLiteDetections)
    }

    private fun processDetections(tfLiteDetections: List<TFLiteDetection>): List<Detection> {
        val rawDetections = mutableListOf<Detection>()

        for (i in tfLiteDetections.indices) {
            val detection = tfLiteDetections[i]
            val category = detection.categories.firstOrNull() ?: continue

            val boundingBox = detection.boundingBox
            val rect2d = Rect2d(
                boundingBox.left.toDouble(),
                boundingBox.top.toDouble(),
                boundingBox.width().toDouble(),
                boundingBox.height().toDouble()
            )

            rawDetections.add(Detection(i, rect2d, category.label, category.score, false))
        }

        // Apply Non-Maximum Suppression
        return applyNMS(rawDetections, nmsIoUThreshold)
    }

    private fun applyNMS(detections: List<Detection>, nmsIoUThreshold: Double): List<Detection> {
        val finalDetections = mutableListOf<Detection>()
        val sortedDetections = detections.sortedByDescending { it.confidence }.toMutableList()

        while (sortedDetections.isNotEmpty()) {
            val best = sortedDetections.removeAt(0)
            finalDetections.add(best)

            val iterator = sortedDetections.iterator()
            while (iterator.hasNext()) {
                val other = iterator.next()
                val iou = calculateIoU(best.boundingBox, other.boundingBox)
                if (iou > nmsIoUThreshold && best.label == other.label) {
                    iterator.remove()
                }
            }
        }

        return finalDetections
    }

    fun imageProxyToMat(imageProxy: ImageProxy): Mat {
        val yBuffer = imageProxy.planes[0].buffer
        val uBuffer = imageProxy.planes[1].buffer
        val vBuffer = imageProxy.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuv = Mat(imageProxy.height + imageProxy.height / 2, imageProxy.width, CvType.CV_8UC1)
        yuv.put(0, 0, nv21)

        val rgb = Mat()
        Imgproc.cvtColor(yuv, rgb, Imgproc.COLOR_YUV2RGB_NV21)

        yuv.release()
        return rgb
    }

    private fun matToBitmap(mat: Mat): Bitmap {
        val bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(mat, bitmap)
        return bitmap
    }

    fun changeAcceleration(accelerationType: AccelerationType) {
        if (currentAcceleration != accelerationType) {
            loadModel(accelerationType)
        }
    }

    fun close() {
        objectDetector?.close()
    }

    companion object {
        private const val TAG = "ObjectDetection"
    }
}

data class Detection(
    var id: Int = -1,
    val boundingBox: Rect2d,
    val label: String,
    val confidence: Float,
    val counted: Boolean = false
)