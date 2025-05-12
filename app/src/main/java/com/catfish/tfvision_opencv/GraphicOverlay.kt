package com.catfish.tfvision_opencv

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import org.opencv.core.Rect2d

class GraphicOverlay(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private var detections: List<Detection> = emptyList()
    private var countingLineY = 0.0f

    // Unified frame and view dimensions
    private var frame = FrameInfo(0, 0)
    private var view = FrameInfo(0, 0)
    private var rotation = 0

    // Scaling and positioning
    private var scale = PointF(1.0f, 1.0f)
    private var offset = PointF(0.0f, 0.0f)

    // Accelerator status
    private var currentAccelerator = AccelerationType.CPU

    // Data class to hold width and height
    data class FrameInfo(var width: Int, var height: Int)

    fun updateAcceleratorStatus(accelerator: AccelerationType) {
        currentAccelerator = accelerator
        invalidate()
    }

    fun getCountingLineY(): Float = countingLineY

    fun setCountingLine(y: Float) {
        countingLineY = y
        invalidate()
    }

    fun setFrameSize(width: Int, height: Int) {
        frame = FrameInfo(width, height)
        updateScaleFactors()
    }

    fun setViewSize(width: Int, height: Int) {
        view = FrameInfo(width, height)
        updateScaleFactors()
    }

    fun setCameraRotation(degrees: Int) {
        rotation = degrees
        updateScaleFactors()
    }

    private fun updateScaleFactors() {
        if (frame.width <= 0 || frame.height <= 0 || view.width <= 0 || view.height <= 0) {
            return
        }

        // Focus on 90-degree rotation handling
        val (adjustedWidth, adjustedHeight) = if (rotation == 90 || rotation == 270) {
            Pair(frame.height, frame.width)
        } else {
            Pair(frame.width, frame.height)
        }

        val viewRatio = view.width.toFloat() / view.height
        val frameRatio = adjustedWidth.toFloat() / adjustedHeight

        if (viewRatio > frameRatio) {
            // View is wider than frame - fit to height
            scale.y = view.height.toFloat() / adjustedHeight
            scale.x = scale.y
            offset.y = 0f
            offset.x = (view.width - adjustedWidth * scale.x) / 2
        } else {
            // View is taller than frame - fit to width
            scale.x = view.width.toFloat() / adjustedWidth
            scale.y = scale.x
            offset.x = 0f
            offset.y = (view.height - adjustedHeight * scale.y) / 2
        }

        Log.d(TAG, "Scale factors updated: scaleX=${scale.x}, scaleY=${scale.y}, offsetX=${offset.x}, offsetY=${offset.y}")
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        view = FrameInfo(w, h)
        updateScaleFactors()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Debug logging stays the same
        Log.d(TAG, "==== GRAPHIC OVERLAY INFO ====")
        Log.d(TAG, "View Size: ${view.width}x${view.height}")
        Log.d(TAG, "Frame Size: ${frame.width}x${frame.height}")
        Log.d(TAG, "Rotation: $rotation")
        Log.d(TAG, "Scale Factor: scaleX=${scale.x}, scaleY=${scale.y}")
        Log.d(TAG, "Offset: offsetX=${offset.x}, offsetY=${offset.y}")
        Log.d(TAG, "Accelerator: $currentAccelerator")

        // Drawing logic for counting line
        val line = Paint().apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }

        val yPos = height * countingLineY
        canvas.drawLine(0f, yPos, width.toFloat(), yPos, line)

//        // Debug Dead zone di atas counting line
//        val deadZonePaint = Paint().apply {
//            color = Color.argb(50, 255, 0, 0) // Merah transparan
//            style = Paint.Style.FILL
//        }
//        canvas.drawRect(0f, 0f, canvas.width.toFloat(), yPos, deadZonePaint)

        // Draw accelerator indicator if it's not CPU
        if (currentAccelerator != AccelerationType.CPU) {
            val acceleratorPaint = Paint().apply {
                color = when (currentAccelerator) {
                    AccelerationType.GPU -> Color.GREEN
                    AccelerationType.NNAPI -> Color.CYAN
                    else -> Color.WHITE
                }
                textSize = 25f
                typeface = Typeface.DEFAULT_BOLD
                setShadowLayer(4f, 0f, 0f, Color.BLACK)
            }

            val text = when (currentAccelerator) {
                AccelerationType.GPU -> "GPU ON"
                AccelerationType.NNAPI -> "NNAPI ON"
                else -> ""
            }

            canvas.drawText(text, 20f, 30f, acceleratorPaint)
        }

        // Draw bounding boxes for all detections
        detections.forEach { detection ->
            drawBoundingBox(canvas, detection)
        }
    }

    private fun drawBoundingBox(canvas: Canvas, detection: Detection) {
        val rectPaint = Paint().apply {
            color = getColorForLabel(detection.label)
            style = Paint.Style.STROKE
            strokeWidth = 4f
            strokeCap = Paint.Cap.ROUND
        }

        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(4f, 0f, 0f, Color.BLACK)
        }

        // Transform coordinates based on rotation
        val transformedRect = transformRect(detection.boundingBox)

        val left = transformedRect.left.coerceIn(0f, view.width.toFloat())
        val top = transformedRect.top.coerceIn(0f, view.height.toFloat())
        val right = transformedRect.right.coerceIn(0f, view.width.toFloat())
        val bottom = transformedRect.bottom.coerceIn(0f, view.height.toFloat())

        val rect = RectF(left, top, right, bottom)

        Log.d(TAG, "Drawing bbox for ${detection.label}: (left=$left, top=$top, right=$right, bottom=$bottom)")

        canvas.drawRect(rect, rectPaint)

        canvas.drawText(
            "${detection.label} #${detection.id} (${(detection.confidence * 100).toInt()}%)",
            rect.left,
            rect.top - 10,
            textPaint
        )
    }

    private fun transformRect(rect: Rect2d): RectF {
        return when (rotation) {
            90 -> {
                RectF(
                    ((frame.height - rect.y - rect.height) * scale.x + offset.x).toFloat(),
                    (rect.x * scale.y + offset.y).toFloat(),
                    ((frame.height - rect.y) * scale.x + offset.x).toFloat(),
                    ((rect.x + rect.width) * scale.y + offset.y).toFloat()
                )
            }
            else -> RectF(
                (rect.x * scale.x + offset.x).toFloat(),
                (rect.y * scale.y + offset.y).toFloat(),
                ((rect.x + rect.width) * scale.x + offset.x).toFloat(),
                ((rect.y + rect.height) * scale.y + offset.y).toFloat()
            )
        }
    }

    fun updateDetections(newDetections: List<Detection>) {
        Log.d(TAG, "Updating detections: ${newDetections.size}")
        detections = newDetections
        invalidate()
    }

    private fun getColorForLabel(label: String): Int {
        return when (label.lowercase()) {
            "grade_a" -> ContextCompat.getColor(context, android.R.color.holo_red_light)
            "grade_b" -> ContextCompat.getColor(context, android.R.color.holo_green_light)
            "grade_c" -> ContextCompat.getColor(context, android.R.color.holo_blue_light)
            else -> Color.YELLOW
        }
    }

    companion object {
        private const val TAG = "GraphicOverlay"
    }
}