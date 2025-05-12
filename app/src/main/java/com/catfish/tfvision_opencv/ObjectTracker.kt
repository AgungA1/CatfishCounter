package com.catfish.tfvision_opencv

import android.util.Log
import com.catfish.tfvision_opencv.Utils.calculateIoU
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Rect2d
import org.opencv.core.Scalar
import org.opencv.tracking.Tracker
import org.opencv.tracking.TrackerMedianFlow
import org.opencv.video.KalmanFilter
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Advanced object tracker that combines multiple tracking approaches:
 * - OpenCV Trackers
 * - Kalman Filter for predictive tracking
 * - Hungarian algorithm for optimal detection assignment
 *
 * @param countingLineY The Y-coordinate of the counting line as a fraction of frame height (0.0-1.0)
 * @param objectDetection Reference to ObjectDetection instance for memory of counted objects
 * @param onCrossingCallback Callback function triggered when an object crosses the counting line
 */
class ObjectTracker(
    private val countingLineY: Float,
    private val objectDetection: ObjectDetection? = null,
    private val onCrossingCallback: (String) -> Unit
) {
    // Tracking collections
    private val trackers = mutableMapOf<Int, Tracker>()
    private val trackedObjects = mutableMapOf<Int, TrackedObject>()
    private val kalmanFilters = mutableMapOf<Int, KalmanFilter>()
    private val centroids = mutableMapOf<Int, Point>()
    private val previousCentroids = mutableMapOf<Int, Point>()
    private val unmatchedFrameCounters = mutableMapOf<Int, Int>()
    private val detectionIdCache = mutableMapOf<String, Int>()

    // Line crossing detection - normalized relative to frame height
    private val lastRelativeYPositions = mutableMapOf<Int, Float>()
    private val objectsToRemoveAfterCounting = mutableSetOf<Int>()

    // Tracking configuration parameters
    private var nextId = 1
    private val MAX_UNMATCHED_FRAMES = 8
    private val MAX_TRACKING_DISTANCE = 260.0
    private val MIN_IOU_THRESHOLD = 0.2
    private val OCCLUSION_IOU_THRESHOLD = 0.5

    // Kalman Filter parameters
    private val KALMAN_PROCESS_NOISE = 1e-3
    private val KALMAN_MEASUREMENT_NOISE = 1e-2
    private val KALMAN_ERROR_COV_POST = 2.0

    // Track frame dimensions for proper normalization
    private var frameHeight = 1
    private var frameWidth = 1

    // Grade stabilizer for label consistency
    private val gradeStabilizer = GradeStabilizer()

    /**
     * Represents a tracked object with its state information
     */
    data class TrackedObject(
        val id: Int,
        val label: String,
        var boundingBox: Rect2d,
        var counted: Boolean = false,
        var velocity: Point = Point(0.0, 0.0),
        var occluded: Boolean = false,
        var crossedLine: Boolean = false
    )

    /**
     * Initialize or update trackers based on new detection results
     *
     * @param frame Current video frame
     * @param detections List of detections from the detector
     */
    fun initializeTrackers(frame: Mat, detections: List<Detection>) {
        // Update frame dimensions for proper coordinate normalization
        frameHeight = frame.rows()
        frameWidth = frame.cols()

        // First process removals from previous frame
        processRemovalsAfterCounting()

        // Update centroid history
        previousCentroids.clear()
        previousCentroids.putAll(centroids)

        // Refresh centroids from current tracked objects
        centroids.clear()
        trackedObjects.forEach { (id, obj) ->
            if (!obj.crossedLine) {
                centroids[id] = calculateCentroid(obj.boundingBox)
            }
        }

        // Skip processing if no objects are being tracked or no new detections
        if (trackedObjects.isEmpty() && detections.isEmpty()) {
            return
        }

        // Extract centroids from new detections
        val detectionCentroids = detections.map {
            calculateCentroid(it.boundingBox) to it
        }

        // Create the cost matrix for assignment
        val costMatrix = buildCostMatrix(detections, detectionCentroids)

        // Apply Hungarian algorithm for optimal assignment if we have both trackers and detections
        val assignments = if (costMatrix.isNotEmpty() && costMatrix[0].isNotEmpty()) {
            hungarianAlgorithm(costMatrix)
        } else {
            emptyList()
        }

        // Process assignments
        processAssignments(frame, detections, assignments, costMatrix)

        // Handle unmatched trackers
        updateUnmatchedTrackers()

        // Create new trackers for unmatched detections
        createNewTrackers(frame, detections, assignments)
    }

    /**
     * Process removals for objects that have crossed the counting line
     */
    private fun processRemovalsAfterCounting() {
        if (objectsToRemoveAfterCounting.isNotEmpty()) {
            Log.d(
                TAG,
                "Removing ${objectsToRemoveAfterCounting.size} objects after counting: $objectsToRemoveAfterCounting"
            )

            // Remove all objects marked for removal
            objectsToRemoveAfterCounting.forEach { id ->
                removeTracker(id)
            }

            // Clear the removal list
            objectsToRemoveAfterCounting.clear()
        }
    }

    /**
     * Build cost matrix for the Hungarian assignment algorithm
     */
    private fun buildCostMatrix(
        detections: List<Detection>,
        detectionCentroids: List<Pair<Point, Detection>>
    ): Array<Array<Double>> {
        // Filter out objects that have crossed the line
        val activeTrackerIds = trackedObjects.filter { !it.value.crossedLine }.keys.toList()

        if (activeTrackerIds.isEmpty() || detections.isEmpty()) {
            return emptyArray()
        }

        return Array(activeTrackerIds.size) { i ->
            val trackerId = activeTrackerIds[i]
            val trackerCentroid = centroids[trackerId] ?: Point(0.0, 0.0)
            val trackedBox = trackedObjects[trackerId]?.boundingBox ?: Rect2d()

            Array(detections.size) { j ->
                val detection = detections[j]
                val detectionCentroid = detectionCentroids[j].first

                // Calculate spatial distance
                val spatialDistance = calculateDistance(trackerCentroid, detectionCentroid)

                // Calculate IoU score
                val iou = calculateIoU(trackedBox, detection.boundingBox)

                // Combined cost: prioritize IoU when there's overlap
                if (iou > 0) {
                    spatialDistance * (1.0 - iou * 0.75) // IoU reduces distance cost
                } else {
                    spatialDistance
                }
            }
        }
    }

    /**
     * Process the assignments from the Hungarian algorithm
     */
    private fun processAssignments(
        frame: Mat,
        detections: List<Detection>,
        assignments: List<Pair<Int, Int>>,
        costMatrix: Array<Array<Double>>
    ) {
        val assignedDetectionIndices = mutableSetOf<Int>()
        val assignedTrackerIndices = mutableSetOf<Int>()
        val activeTrackerIds = trackedObjects.filter { !it.value.crossedLine }.keys.toList()

        for ((trackerIndex, detectionIndex) in assignments) {
            if (trackerIndex >= activeTrackerIds.size) continue

            val distance = costMatrix[trackerIndex][detectionIndex]
            if (distance <= MAX_TRACKING_DISTANCE) {
                val trackerId = activeTrackerIds[trackerIndex]
                val detection = detections[detectionIndex]

                // ✅ Tambahkan deteksi ke stabilizer
                gradeStabilizer.addDetection(trackerId, detection.label, detection.confidence)

                // ✅ Dapatkan label stabil
                val stableLabel = gradeStabilizer.getStableLabel(trackerId)

                // ✅ Set ID ke detection untuk visual konsisten
                detection.id = trackerId

                // ✅ Buat key cache
                val center = calculateCentroid(detection.boundingBox)
                val key = "${stableLabel}_${center.x.toInt()}_${center.y.toInt()}_${detection.boundingBox.width.toInt()}_${detection.boundingBox.height.toInt()}"
                detectionIdCache[key] = trackerId

                // ✅ Buat copy dari detection dengan label stabil
                val stableDetection = Detection(
                    detection.id,
                    detection.boundingBox.clone(),
                    stableLabel,
                    detection.confidence,
                    detection.counted
                )

                // ✅ Gunakan detection dengan label stabil untuk update tracker
                if (updateTrackerWithNewDetection(frame, trackerId, stableDetection)) {
                    assignedDetectionIndices.add(detectionIndex)
                    assignedTrackerIndices.add(trackerIndex)
                    unmatchedFrameCounters.remove(trackerId)
                }
            }
        }
    }

    /**
     * Update a specific tracker with a new detection
     */
    private fun updateTrackerWithNewDetection(
        frame: Mat,
        trackerId: Int,
        detection: Detection
    ): Boolean {
        // Skip objects that have already crossed the line
        val previousObj = trackedObjects[trackerId] ?: return false
        if (previousObj.crossedLine) {
            return false
        }

        // Clear the old tracker
        trackers[trackerId]?.clear()

        // Create and initialize new OpenCV tracker
        val newTracker = TrackerMedianFlow.create()
        if (!newTracker.init(frame, detection.boundingBox)) {
            return false
        }

        // Update tracker in collection
        trackers[trackerId] = newTracker

        // Get centroids
        val oldCentroid = previousCentroids[trackerId]
            ?: calculateCentroid(previousObj.boundingBox)
        val newCentroid = calculateCentroid(detection.boundingBox)

        // Update Kalman filter with new measurement
        val kalman = kalmanFilters[trackerId]
        if (kalman != null) {
            // Convert centroid to measurement Mat
            val measurement = Mat(4, 1, CvType.CV_32F)
            measurement.put(0, 0,
                floatArrayOf(
                    newCentroid.x.toFloat(),
                    newCentroid.y.toFloat(),
                    (newCentroid.x - oldCentroid.x).toFloat(),
                    (newCentroid.y - oldCentroid.y).toFloat()
                )
            )

            // Correct Kalman filter with new measurement
            val corrected = kalman.correct(measurement)

            // Extract velocity from Kalman state
            val velocity = Point(
                corrected.get(2, 0)[0].toDouble(),
                corrected.get(3, 0)[0].toDouble()
            )

            measurement.release()
            corrected.release()

            trackedObjects[trackerId] = previousObj.copy(
                boundingBox = detection.boundingBox.clone(),
                velocity = velocity
            )
        } else {
            // Initialize a new Kalman filter for this object
            initializeKalmanFilter(trackerId, newCentroid, oldCentroid)

            // Calculate basic velocity for initial state
            val velocity = Point(
                newCentroid.x - oldCentroid.x,
                newCentroid.y - oldCentroid.y
            )

            trackedObjects[trackerId] = previousObj.copy(
                boundingBox = detection.boundingBox.clone(),
                velocity = velocity
            )
        }

        // Update normalized Y position for line crossing detection
        updateNormalizedYPosition(trackerId, detection.boundingBox)

        // Update centroid
        centroids[trackerId] = newCentroid

        return true
    }

    /**
     * Initialize a Kalman filter for a tracked object
     */
    private fun initializeKalmanFilter(
        trackerId: Int,
        currentCentroid: Point,
        previousCentroid: Point
    ) {
        // Create Kalman filter with:
        // - 4 dynamic parameters (x, y, vx, vy)
        // - 4 measurement parameters (measured x, y, vx, vy)
        // - 0 control parameters
        val kalman = KalmanFilter(4, 4, 0, CvType.CV_32F)

        // State transition matrix (A) - constant velocity model
        val transitionMatrix = Mat(4, 4, CvType.CV_32F, Scalar(0.0))
        transitionMatrix.put(0, 0, floatArrayOf(1f, 0f, 1f, 0f))
        transitionMatrix.put(1, 0, floatArrayOf(0f, 1f, 0f, 1f))
        transitionMatrix.put(2, 0, floatArrayOf(0f, 0f, 1f, 0f))
        transitionMatrix.put(3, 0, floatArrayOf(0f, 0f, 0f, 1f))
        kalman._transitionMatrix = transitionMatrix

        // Measurement matrix (H)
        val measurementMatrix = Mat.eye(4, 4, CvType.CV_32F)
        kalman._measurementMatrix = measurementMatrix

        // Process noise covariance matrix (Q)
        val processNoiseCov = Mat.eye(4, 4, CvType.CV_32F)
        processNoiseCov.convertTo(processNoiseCov, CvType.CV_32F, KALMAN_PROCESS_NOISE)
        kalman._processNoiseCov = processNoiseCov

        // Measurement noise covariance matrix (R)
        val measurementNoiseCov = Mat.eye(4, 4, CvType.CV_32F)
        measurementNoiseCov.convertTo(measurementNoiseCov, CvType.CV_32F, KALMAN_MEASUREMENT_NOISE)
        kalman._measurementNoiseCov = measurementNoiseCov

        // Error covariance matrix (P)
        val errorCovPost = Mat.eye(4, 4, CvType.CV_32F)
        errorCovPost.convertTo(errorCovPost, CvType.CV_32F, KALMAN_ERROR_COV_POST)
        kalman._errorCovPost = errorCovPost

        // Initial state (x, y, vx, vy)
        val initialVelocityX = currentCentroid.x - previousCentroid.x
        val initialVelocityY = currentCentroid.y - previousCentroid.y

        val statePost = Mat(4, 1, CvType.CV_32F)
        statePost.put(0, 0, floatArrayOf(
            currentCentroid.x.toFloat(),
            currentCentroid.y.toFloat(),
            initialVelocityX.toFloat(),
            initialVelocityY.toFloat()
        ))
        kalman._statePost = statePost

        // Store the Kalman filter
        kalmanFilters[trackerId] = kalman

        // Clean up matrices
        transitionMatrix.release()
        measurementMatrix.release()
        processNoiseCov.release()
        measurementNoiseCov.release()
        errorCovPost.release()
        statePost.release()
    }

    /**
     * Update counters for unmatched trackers and remove stale ones
     */
    private fun updateUnmatchedTrackers() {
        // Increment counters for unmatched trackers (only for active ones)
        val activeTrackerIds = trackedObjects.filter { !it.value.crossedLine }.keys.toList()

        activeTrackerIds.forEach { trackerId ->
            val counter = unmatchedFrameCounters.getOrDefault(trackerId, 0) + 1
            unmatchedFrameCounters[trackerId] = counter
        }

        // Remove trackers that haven't been matched for too long
        val trackersToRemove = unmatchedFrameCounters.filter {
            it.value > MAX_UNMATCHED_FRAMES &&
                    !trackedObjects[it.key]?.crossedLine!! ?: true
        }.keys.toList()

        for (id in trackersToRemove) {
            removeTracker(id)
        }
    }

    /**
     * Create new trackers for unmatched detections
     */
    private fun createNewTrackers(
        frame: Mat,
        detections: List<Detection>,
        assignments: List<Pair<Int, Int>>
    ) {
        val assignedDetectionIndices = assignments.map { it.second }.toSet()

        for (i in detections.indices) {
            if (i !in assignedDetectionIndices) {
                val detection = detections[i]

                val center = calculateCentroid(detection.boundingBox)
                val key = "${detection.label}_${center.x.toInt()}_${center.y.toInt()}_${detection.boundingBox.width.toInt()}_${detection.boundingBox.height.toInt()}"

                // Gunakan ID dari cache jika ada
                val objectId = detectionIdCache[key] ?: nextId++

                // Tambahkan deteksi awal ke stabilizer
                gradeStabilizer.addDetection(objectId, detection.label, detection.confidence)

                // Normalize Y position
                val relativeY = normalizeYCoordinate(center)

                // Skip objects that are already above the counting line
                if (relativeY <= countingLineY) {
                    Log.d(TAG, "Skipping detection that's already above the counting line: relativeY=$relativeY, lineY=$countingLineY")
                    continue
                }

                // ⚠️ PERUBAHAN 1: Deteksi duplikasi yang lebih agresif
                // - Turunkan threshold IoU menjadi 0.25 (sebelumnya 0.3)
                // - Tambahkan preferensi untuk objek dengan label yang sama
                var isDuplicate = false
                var duplicateTrackerId = -1
                var bestIoU = 0.0
                var bestMatchingLabelId = -1

                trackedObjects.forEach { (id, tracked) ->
                    val iou = calculateIoU(tracked.boundingBox, detection.boundingBox)

                    // Prioritaskan duplikasi dengan label yang sama
                    if (iou > 0.2) {  // Lebih agresif (dari 0.3)
                        isDuplicate = true

                        // Prioritaskan objek dengan label yang sama
                        if (tracked.label == detection.label && iou > bestIoU) {
                            bestIoU = iou
                            bestMatchingLabelId = id
                        }

                        // Jika belum ada match dengan label yang sama, gunakan IoU tertinggi
                        if (bestMatchingLabelId == -1 && iou > bestIoU) {
                            bestIoU = iou
                            duplicateTrackerId = id
                        }

                        Log.d(TAG, "⚠️ POTENTIAL DUPLICATE: New detection (${detection.label}) overlaps with tracker #$id (${tracked.label}) with IoU=$iou")
                    }
                }

                // Prioritaskan tracker dengan label yang sama jika ada
                if (bestMatchingLabelId != -1) {
                    duplicateTrackerId = bestMatchingLabelId
                    Log.d(TAG, "✅ PREFERRED DUPLICATE: Using tracker #$duplicateTrackerId with matching label")
                }

                // ⚠️ PERUBAHAN 2: Tambahkan pemeriksaan jarak antara centroid
                // Jika IoU kecil tapi centroid dekat, dapat dianggap duplikat
                if (!isDuplicate) {
                    val maxCentroidDistance = min(detection.boundingBox.width, detection.boundingBox.height) * 0.5

                    trackedObjects.forEach { (id, tracked) ->
                        val trackedCenter = calculateCentroid(tracked.boundingBox)
                        val distance = calculateDistance(center, trackedCenter)

                        if (distance < maxCentroidDistance && tracked.label == detection.label) {
                            isDuplicate = true
                            duplicateTrackerId = id
                            Log.d(TAG, "⚠️ CENTROID PROXIMITY DUPLICATE: New detection (${detection.label}) close to tracker #$id, distance=$distance")
                        }
                    }
                }

                // ⚠️ PERUBAHAN 3: Jika duplikat, update label di objek yang sudah ada
                // daripada membuat tracker baru
                if (isDuplicate && duplicateTrackerId >= 0) {
                    // Tambahkan label baru ke histori tracker yang sudah ada
                    gradeStabilizer.addDetection(duplicateTrackerId, detection.label, detection.confidence)

                    // Ambil label stabil berdasarkan prioritas
                    val stableLabel = gradeStabilizer.getStableLabel(duplicateTrackerId)

                    // Update label objek yang sudah ada jika perlu
                    val existingObj = trackedObjects[duplicateTrackerId]
                    if (existingObj != null && existingObj.label != stableLabel) {
                        trackedObjects[duplicateTrackerId] = existingObj.copy(label = stableLabel)
                        Log.d(TAG, "Updated duplicate tracker #$duplicateTrackerId label to stable: $stableLabel")
                    }

                    // ⚠️ PERUBAHAN 4: Update centroid cache untuk tracker yang sudah ada
                    centroids[duplicateTrackerId] = center

                    // ⚠️ PERUBAHAN 5: Reinisialisasi tracker untuk posisi yang diperbarui
                    // untuk menghindari drift tracking
                    trackers[duplicateTrackerId]?.clear()
                    val newTracker = TrackerMedianFlow.create()
                    if (newTracker.init(frame, detection.boundingBox)) {
                        trackers[duplicateTrackerId] = newTracker
                        Log.d(TAG, "Reinitialized tracker #$duplicateTrackerId with updated position")
                    }

                    continue  // Skip pembuatan tracker baru
                }

                val tracker = TrackerMedianFlow.create()
                if (tracker.init(frame, detection.boundingBox)) {
                    trackers[objectId] = tracker
                    val syntheticPrevPoint = Point(center.x - 1.0, center.y - 1.0)
                    initializeKalmanFilter(objectId, center, syntheticPrevPoint)
                    trackedObjects[objectId] = TrackedObject(
                        objectId, detection.label, detection.boundingBox.clone(),
                        velocity = Point(1.0, 1.0)
                    )
                    centroids[objectId] = center

                    // Store normalized Y position
                    lastRelativeYPositions[objectId] = relativeY

                    detectionIdCache[key] = objectId // cache update
                    Log.d(TAG, "Created new tracker $objectId with relativeY=$relativeY (lineY=$countingLineY)")
                }
            }
        }
    }

    /**
     * Update all trackers for the current frame
     *
     * @param frame Current video frame
     * @param imgWidth Width of the frame
     * @param imgHeight Height of the frame
     * @return Map of updated tracked objects
     */
    fun updateTrackers(frame: Mat, imgWidth: Int, imgHeight: Int): Map<Int, TrackedObject> {
        // Update frame dimensions
        frameWidth = imgWidth
        frameHeight = imgHeight

        // Process removals from previous frame first
        processRemovalsAfterCounting()

        // Store previous centroids
        previousCentroids.clear()
        previousCentroids.putAll(centroids)

        val updatedObjects = mutableMapOf<Int, TrackedObject>()
        val objectsToRemove = mutableListOf<Int>()

        // Check for occlusions to adjust tracking strategy
        detectOcclusions()

        // Update all trackers
        trackers.forEach { (id, tracker) ->
            val trackedObj = trackedObjects[id] ?: return@forEach

            // Skip objects that have already crossed the line - they should be removed, not updated
            if (trackedObj.crossedLine) {
                // Already in removal list, skip processing
                if (id !in objectsToRemoveAfterCounting) {
                    objectsToRemoveAfterCounting.add(id)
                }
                return@forEach
            }

            if (updateSingleTracker(frame, id, tracker, trackedObj, imgWidth, imgHeight)) {
                updatedObjects[id] = trackedObj
            } else {
                // If prediction-based recovery fails, mark for removal
                objectsToRemove.add(id)
            }

            // ⚠️ PENTING: Tambahkan di sini, setelah tracker diupdate
            // Pastikan label stabil selalu digunakan di semua frame
            val updatedObj = trackedObjects[id]
            if (updatedObj != null && !updatedObj.crossedLine) {
                // Ambil label stabil
                val stableLabel = gradeStabilizer.getStableLabel(id)

                // Update label jika berbeda
                if (updatedObj.label != stableLabel) {
                    trackedObjects[id] = updatedObj.copy(label = stableLabel)
                    Log.d(TAG, "⚠️ Updated tracker #$id label to stable: $stableLabel (was: ${updatedObj.label})")
                }
            }
        }

        // Clean up failed trackers
        removeFailedTrackers(objectsToRemove)

        return updatedObjects
    }

    /**
     * Update a single tracker and handle potential failures
     */
    private fun updateSingleTracker(
        frame: Mat,
        id: Int,
        tracker: Tracker,
        trackedObj: TrackedObject,
        imgWidth: Int,
        imgHeight: Int
    ): Boolean {
        // Skip objects that have already crossed the line
        if (trackedObj.crossedLine) {
            return false
        }

        val box = Rect2d()
        val tracked = tracker.update(frame, box)

        if (tracked) {
            // Validate tracker update
            val validUpdate = validateTrackerUpdate(id, box, trackedObj)

            // Save previous bounding box for Kalman update
            val previousBox = trackedObj.boundingBox.clone()

            // Update bounding box with tracker result
            trackedObj.boundingBox = box

            // Get current centroid
            val currentCentroid = calculateCentroid(box)

            // Update centroid
            centroids[id] = currentCentroid

            // Update Kalman filter
            updateKalmanFilter(id, currentCentroid)

            // Update normalized Y position and check for line crossing
            updateNormalizedYPosition(id, box)
            checkLineCrossing(trackedObj, id)

            return !trackedObj.crossedLine
        } else {
            // If tracker fails, try prediction-based recovery with Kalman filter
            return recoverWithKalmanPrediction(frame, id, trackedObj, imgWidth, imgHeight)
        }
    }

    /**
     * Update normalized Y position for an object
     */
    private fun updateNormalizedYPosition(id: Int, box: Rect2d) {
        val centroid = calculateCentroid(box)
        val relativeY = normalizeYCoordinate(centroid)

        // Get previous Y position for logging
        val prevY = lastRelativeYPositions[id]

        // Update stored position
        lastRelativeYPositions[id] = relativeY

        Log.d(TAG, "Updated Y position for obj $id: prevY=$prevY → currY=$relativeY (lineY=$countingLineY)")
    }

    /**
     * Normalize Y coordinate based on frame dimensions
     */
    private fun normalizeYCoordinate(centroid: Point): Float {
        // Transformasi koordinat untuk rotasi 90 derajat
        return (centroid.x / frameWidth).toFloat()
    }

    /**
     * Update Kalman filter with new centroid position
     */
    private fun updateKalmanFilter(id: Int, currentCentroid: Point) {
        val kalman = kalmanFilters[id] ?: return

        // First predict
        val prediction = kalman.predict()

        // Get previous centroid
        val previousCentroid = previousCentroids[id] ?: return

        // Create measurement Mat (x, y, vx, vy)
        val measurement = Mat(4, 1, CvType.CV_32F)
        measurement.put(0, 0, floatArrayOf(
            currentCentroid.x.toFloat(),
            currentCentroid.y.toFloat(),
            (currentCentroid.x - previousCentroid.x).toFloat(),
            (currentCentroid.y - previousCentroid.y).toFloat()
        ))

        // Correct using measurement
        val corrected = kalman.correct(measurement)

        // Update velocity in tracked object
        trackedObjects[id]?.velocity = Point(
            corrected.get(2, 0)[0].toDouble(),
            corrected.get(3, 0)[0].toDouble()
        )

        // Clean up
        prediction.release()
        measurement.release()
        corrected.release()
    }

    /**
     * Validate tracker update to detect potential tracking failures
     */
    private fun validateTrackerUpdate(id: Int, box: Rect2d, trackedObj: TrackedObject): Boolean {
        val previousBox = trackedObj.boundingBox.clone()
        val iou = calculateIoU(previousBox, box)

        // Check if the new box is reasonable compared to the previous box
        if (iou < MIN_IOU_THRESHOLD &&
            (box.width < previousBox.width * 0.5 || box.width > previousBox.width * 2.0 ||
                    box.height < previousBox.height * 0.5 || box.height > previousBox.height * 2.0)
        ) {
            Log.d(TAG, "Suspicious tracker update detected for object ${trackedObj.id}, IoU=$iou")
            return false
        }

        return true
    }

    /**
     * Try to recover a failed tracker using Kalman filter prediction
     */
    private fun recoverWithKalmanPrediction(
        frame: Mat,
        id: Int,
        trackedObj: TrackedObject,
        imgWidth: Int,
        imgHeight: Int
    ): Boolean {
        // Skip objects that have already crossed the line
        if (trackedObj.crossedLine) {
            return false
        }

        val kalman = kalmanFilters[id]
        if (kalman != null) {
            // Predict next state
            val prediction = kalman.predict()

            // Extract predicted position and velocity
            val predictedX = prediction.get(0, 0)[0].toDouble()
            val predictedY = prediction.get(1, 0)[0].toDouble()
            val predictedVx = prediction.get(2, 0)[0].toDouble()
            val predictedVy = prediction.get(3, 0)[0].toDouble()

            // Create predicted bounding box at Kalman-predicted position
            val predictedBox = Rect2d(
                predictedX - trackedObj.boundingBox.width / 2,
                predictedY - trackedObj.boundingBox.height / 2,
                trackedObj.boundingBox.width,
                trackedObj.boundingBox.height
            )

            // Ensure box stays within frame bounds
            constrainBoxToFrameBounds(predictedBox, imgWidth, imgHeight)

            // Create a predicted centroid for normalization
            val predictedCentroid = Point(predictedX, predictedY)

            // Update normalized Y position with prediction
            val relativeY = normalizeYCoordinate(predictedCentroid)
            lastRelativeYPositions[id] = relativeY

            // Check if predicted position crosses the line
            checkLineCrossing(trackedObj, id)

            // If object has crossed the line after this check, don't try to recover
            if (trackedObj.crossedLine) {
                prediction.release()
                return false
            }

            // Update object properties
            trackedObj.boundingBox = predictedBox
            trackedObj.velocity = Point(predictedVx, predictedVy)

            // Update centroid
            centroids[id] = Point(predictedX, predictedY)

            // Try to re-initialize tracker with predicted position
            val newTracker = TrackerMedianFlow.create()
            if (newTracker.init(frame, predictedBox)) {
                trackers[id] = newTracker
                Log.d(TAG, "Successfully recovered tracker $id using Kalman prediction")
                prediction.release()
                return true
            }

            prediction.release()
        }

        return false
    }

    /**
     * Ensure box coordinates stay within frame boundaries
     */
    private fun constrainBoxToFrameBounds(box: Rect2d, imgWidth: Int, imgHeight: Int) {
        box.x = box.x.coerceIn(0.0, imgWidth - 1.0)
        box.y = box.y.coerceIn(0.0, imgHeight - 1.0)

        // Adjust width and height if box extends beyond frame
        box.width = if (box.x + box.width >= imgWidth) imgWidth - box.x else box.width
        box.height = if (box.y + box.height >= imgHeight) imgHeight - box.y else box.height
    }

    /**
     * Remove trackers that failed during updates
     */
    private fun removeFailedTrackers(objectsToRemove: List<Int>) {
        objectsToRemove.forEach { id ->
            // Don't remove twice
            if (id !in objectsToRemoveAfterCounting) {
                removeTracker(id)
            }
        }
    }

    /**
     * Safely removes a tracker and all associated data
     */
    private fun removeTracker(id: Int) {
        trackers.remove(id)?.clear()
        trackedObjects.remove(id)

        // Clean up Kalman filter resources
        kalmanFilters.remove(id)?.apply {
            // Clean up internal Kalman matrices - not directly accessible
            // but will be garbage collected
        }

        // Grade stabilizer cleanup
        gradeStabilizer.removeHistory(id)

        unmatchedFrameCounters.remove(id)
        centroids.remove(id)
        previousCentroids.remove(id)
        lastRelativeYPositions.remove(id)
        Log.d(TAG, "Removed tracker $id")
    }

    /**
     * Check if an object has crossed the counting line
     * Detects crossing from BELOW to ABOVE (larger Y to smaller Y)
     */
    private fun checkLineCrossing(
        trackedObj: TrackedObject,
        objectId: Int
    ) {
        // Skip if already counted or crossed
        if (trackedObj.counted || trackedObj.crossedLine) return

        // Get current normalized Y position
        val currY = lastRelativeYPositions[objectId] ?: return

        // Get previous centroid and normalize
        val prevCentroid = previousCentroids[objectId] ?: return
        val prevY = normalizeYCoordinate(prevCentroid)

        if (prevY < countingLineY && currY <= countingLineY) {
            // ⚠️ CRITICAL: Ambil label stabil untuk counting
            val stableLabel = gradeStabilizer.getStableLabel(objectId)

            // Perubahan: Lebih eksplisit dalam pembaruan objek dan status
            // Ini akan memastikan label benar-benar diupdate sebelum counting

            // ⚠️ CRITICAL: Buat objek baru dengan label stabil
            val stableTrackedObj = TrackedObject(
                id = objectId,
                label = stableLabel,  // Gunakan label stabil
                boundingBox = trackedObj.boundingBox.clone(),
                counted = true,
                crossedLine = true,
                velocity = trackedObj.velocity,
                occluded = trackedObj.occluded
            )

            // ⚠️ CRITICAL: Update tracked object dengan yang stabil
            trackedObjects[objectId] = stableTrackedObj

            // ⚠️ CRITICAL: Pastikan callback menggunakan label stabil
            Log.d(
                TAG,
                "⚠️ COUNTING with STABLE LABEL: Object $objectId, Label: $stableLabel, Original: ${trackedObj.label}"
            )

            // ⚠️ CRITICAL: Panggil callback dengan label stabil
            onCrossingCallback(stableLabel)

            // Mark for cleanup
            objectsToRemoveAfterCounting.add(objectId)

            Log.d(
                TAG,
                "✅ COUNTED: Object ${stableTrackedObj.id} (${stableLabel}) crossed line: " +
                        "$prevY → $currY (lineY=$countingLineY)"
            )
        }
    }

    /**
     * Detect possible occlusions between tracked objects
     */
    private fun detectOcclusions() {
        // Reset occlusion status for all objects
        trackedObjects.values.forEach { it.occluded = false }

        // Only process active objects (not crossed the line)
        val activeObjects = trackedObjects.filter { !it.value.crossedLine }

        // Check pairs of objects for significant overlap
        for (id1 in activeObjects.keys) {
            val box1 = activeObjects[id1]?.boundingBox ?: continue

            for (id2 in activeObjects.keys) {
                if (id1 == id2) continue

                val box2 = activeObjects[id2]?.boundingBox ?: continue
                val iou = calculateIoU(box1, box2)

                // Mark as occluded if significant overlap detected
                if (iou > OCCLUSION_IOU_THRESHOLD) {
                    Log.d(TAG, "Occlusion detected between objects $id1 and $id2, IoU=$iou")
                    trackedObjects[id1]?.occluded = true
                    trackedObjects[id2]?.occluded = true

                    // Kalman filtering becomes more important during occlusions
                    // We could increase the weight of Kalman predictions here
                }
            }
        }
    }

    /**
     * Calculate the centroid of a bounding box
     */
    private fun calculateCentroid(box: Rect2d): Point {
        return Point(box.x + box.width / 2, box.y + box.height / 2)
    }

    /**
     * Calculate Euclidean distance between two points
     */
    private fun calculateDistance(p1: Point, p2: Point): Double {
        return sqrt((p1.x - p2.x).pow(2) + (p1.y - p2.y).pow(2))
    }

    /**
     * Implementation of the Hungarian algorithm for optimal assignment
     */
    private fun hungarianAlgorithm(costMatrix: Array<Array<Double>>): List<Pair<Int, Int>> {
        if (costMatrix.isEmpty() || costMatrix[0].isEmpty()) return emptyList()

        val rows = costMatrix.size
        val cols = costMatrix[0].size

        val assignments = mutableListOf<Pair<Int, Int>>()

        // Step 1: Reduce rows
        val reducedCost = Array(rows) { i ->
            val rowMin = costMatrix[i].minOrNull() ?: 0.0
            Array(cols) { j -> costMatrix[i][j] - rowMin }
        }

        // Step 2: Reduce columns
        for (j in 0 until cols) {
            var colMin = Double.MAX_VALUE
            for (i in 0 until rows) {
                colMin = minOf(colMin, reducedCost[i][j])
            }
            for (i in 0 until rows) {
                reducedCost[i][j] -= colMin
            }
        }

        // Step 3 & 4: Cover zeros and create additional zeros
        val rowCover = BooleanArray(rows)
        val colCover = BooleanArray(cols)
        val starMatrix = Array(rows) { BooleanArray(cols) }
        val primeMatrix = Array(rows) { BooleanArray(cols) }

        // Initial star assignment
        for (i in 0 until rows) {
            for (j in 0 until cols) {
                if (reducedCost[i][j] == 0.0 && !rowCover[i] && !colCover[j]) {
                    starMatrix[i][j] = true
                    rowCover[i] = true
                    colCover[j] = true
                }
            }
        }

        // Reset covers
        for (i in 0 until rows) rowCover[i] = false
        for (j in 0 until cols) colCover[j] = false

        // Cover columns with starred zeros
        for (i in 0 until rows) {
            for (j in 0 until cols) {
                if (starMatrix[i][j]) {
                    colCover[j] = true
                }
            }
        }

        // Main loop - create additional stars
        while (colCover.count { it } < min(rows, cols)) {
            // Find an uncovered zero
            var row = -1
            var col = -1

            searchUncoveredZero@ for (i in 0 until rows) {
                if (rowCover[i]) continue
                for (j in 0 until cols) {
                    if (!colCover[j] && reducedCost[i][j] == 0.0) {
                        row = i
                        col = j
                        break@searchUncoveredZero
                    }
                }
            }

            if (row < 0) {
                // No uncovered zeros - create more by adjusting costs
                createMoreZeros(reducedCost, rowCover, colCover)
                continue
            }

            // Prime the uncovered zero
            primeMatrix[row][col] = true

            // Check if there's a starred zero in the row
            var starCol = -1
            for (j in 0 until cols) {
                if (starMatrix[row][j]) {
                    starCol = j
                    break
                }
            }

            if (starCol < 0) {
                // No starred zero in the row - augmenting path found
                // Convert path of alternating primed and starred zeros
                convertPath(starMatrix, primeMatrix, row, col)

                // Clear primes
                for (i in 0 until rows) {
                    for (j in 0 until cols) {
                        primeMatrix[i][j] = false
                    }
                }

                // Clear covers
                for (i in 0 until rows) rowCover[i] = false
                for (j in 0 until cols) colCover[j] = false

                // Cover columns with starred zeros
                for (i in 0 until rows) {
                    for (j in 0 until cols) {
                        if (starMatrix[i][j]) {
                            colCover[j] = true
                        }
                    }
                }
            } else {
                // There is a starred zero in the row
                rowCover[row] = true
                colCover[starCol] = false
            }
        }

        // Extract assignments from starred zeros
        for (i in 0 until rows) {
            for (j in 0 until cols) {
                if (starMatrix[i][j]) {
                    assignments.add(Pair(i, j))
                }
            }
        }

        return assignments
    }

    /**
     * Create more zeros in the cost matrix for Hungarian algorithm
     */
    private fun createMoreZeros(
        reducedCost: Array<Array<Double>>,
        rowCover: BooleanArray,
        colCover: BooleanArray
    ) {
        // Find the minimum uncovered value
        var minVal = Double.MAX_VALUE
        for (i in reducedCost.indices) {
            if (rowCover[i]) continue
            for (j in reducedCost[0].indices) {
                if (!colCover[j]) {
                    minVal = minOf(minVal, reducedCost[i][j])
                }
            }
        }

        // Add minimum to covered rows and subtract from uncovered columns
        for (i in reducedCost.indices) {
            for (j in reducedCost[0].indices) {
                if (rowCover[i]) reducedCost[i][j] += minVal
                if (!colCover[j]) reducedCost[i][j] -= minVal
            }
        }
    }

    /**
     * Helper function for Hungarian algorithm to convert path of zeros
     */
    private fun convertPath(
        starMatrix: Array<BooleanArray>,
        primeMatrix: Array<BooleanArray>,
        startRow: Int,
        startCol: Int
    ): Boolean {
        val rows = starMatrix.size
        val cols = starMatrix[0].size

        val path = mutableListOf<Pair<Int, Int>>()
        path.add(Pair(startRow, startCol))

        var currentRow = startRow
        var currentCol = startCol

        while (true) {
            // Find starred zero in column
            var starRow = -1
            for (i in 0 until rows) {
                if (starMatrix[i][currentCol]) {
                    starRow = i
                    break
                }
            }

            // Break if no starred zero found
            if (starRow < 0) break

            path.add(Pair(starRow, currentCol))

            // Find primed zero in row
            var primeCol = -1
            for (j in 0 until cols) {
                if (primeMatrix[starRow][j]) {
                    primeCol = j
                    break
                }
            }

            path.add(Pair(starRow, primeCol))

            currentRow = starRow
            currentCol = primeCol
        }

        // Convert path: star primed zeros and unstar starred zeros
        for ((pathRow, pathCol) in path) {
            starMatrix[pathRow][pathCol] = !starMatrix[pathRow][pathCol]
        }

        return true
    }

    /**
     * Reset all trackers and internal state
     */
    fun reset() {
        // Clean up all trackers
        trackers.forEach { (_, tracker) -> tracker.clear() }
        trackers.clear()

        // Clean up all Kalman filters
        kalmanFilters.forEach { (_, kalman) ->
            // No explicit cleanup needed, but this ensures each is processed
        }
        kalmanFilters.clear()

        // Clear all collections
        trackedObjects.clear()
        unmatchedFrameCounters.clear()
        centroids.clear()
        previousCentroids.clear()
        lastRelativeYPositions.clear()
        objectsToRemoveAfterCounting.clear()
        detectionIdCache.clear()

        // Reset the grade stabilizer
        gradeStabilizer.reset()

        // Reset ID counter
        nextId = 1

        Log.d(TAG, "All trackers and Kalman filters reset")
    }

    companion object {
        private const val TAG = "ObjectTracker"
    }
}