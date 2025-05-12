package com.catfish.tfvision_opencv

import android.util.Log

/**
 * Class untuk menstabilkan label grade objek berdasarkan histori deteksi
 * dengan prioritas grade_c > grade_b > grade_a
 * Setelah mencapai prioritas tertentu, grade akan terkunci dan tidak turun lagi
 */
class GradeStabilizer {

    // Prioritas grade: grade_c > grade_b > grade_a
    private val gradePriority = mapOf(
        "grade_c" to 3,
        "grade_b" to 2,
        "grade_a" to 1,
        "unknown" to 0
    )

    // Menyimpan histori label untuk setiap trackerId
    private val trackingHistory = mutableMapOf<Int, MutableList<Pair<String, Float>>>()

    // Menyimpan grade tertinggi yang pernah dicapai untuk setiap trackerId
    private val lockedHighestGrade = mutableMapOf<Int, String>()

    // Window size untuk histori
    private val MAX_HISTORY_SIZE = 9

    // Minimum deteksi dengan grade yang sama untuk mengunci grade
    private val MIN_DETECTIONS_TO_LOCK = 3

    // Tambahkan deteksi ke histori
    fun addDetection(id: Int, label: String, confidence: Float) {

        if (!trackingHistory.containsKey(id)) {
            trackingHistory[id] = mutableListOf()
        }

        // Tambahkan deteksi baru
        trackingHistory[id]?.add(Pair(label, confidence))

        // Batasi ukuran histori
        if (trackingHistory[id]?.size ?: 0 > MAX_HISTORY_SIZE) {
            trackingHistory[id]?.removeAt(0)
        }

        // Update locked highest grade jika diperlukan
        updateLockedHighestGrade(id)

        Log.d(TAG, "Added detection for ID $id: $label (conf: $confidence), history size: ${trackingHistory[id]?.size}")
    }

    /**
     * Update locked highest grade berdasarkan histori
     */
    private fun updateLockedHighestGrade(id: Int) {
        val history = trackingHistory[id] ?: return

        // Hitung frekuensi setiap label
        val labelFrequency = history.groupBy { it.first }
            .mapValues { it.value.size }

        // Cari label yang memenuhi batas minimum deteksi
        val candidateLabels = labelFrequency.filter { it.value >= MIN_DETECTIONS_TO_LOCK }
            .keys
            .toList()

        if (candidateLabels.isEmpty()) return

        // Ambil label dengan prioritas tertinggi dari kandidat
        val highestPriorityLabel = candidateLabels.maxByOrNull { gradePriority[it] ?: 0 }
            ?: return

        // Bandingkan dengan prioritas grade yang sudah terkunci sebelumnya
        val currentLockedGrade = lockedHighestGrade[id]
        if (currentLockedGrade == null) {
            // Belum ada grade yang terkunci, langsung kunci
            lockedHighestGrade[id] = highestPriorityLabel
            Log.d(TAG, "Locking grade for ID $id to $highestPriorityLabel (first lock)")
        } else {
            // Bandingkan prioritas current dan new
            val currentPriority = gradePriority[currentLockedGrade] ?: 0
            val newPriority = gradePriority[highestPriorityLabel] ?: 0

            // Update hanya jika prioritas baru lebih tinggi
            if (newPriority > currentPriority) {
                lockedHighestGrade[id] = highestPriorityLabel
                Log.d(TAG, "Upgrading locked grade for ID $id from $currentLockedGrade to $highestPriorityLabel")
            }
        }
    }

    // Mendapatkan label stabil berdasarkan histori dan prioritas grade
    fun getStableLabel(id: Int): String {
        // Prioritaskan grade yang sudah terkunci jika ada
        val lockedGrade = lockedHighestGrade[id]
        if (lockedGrade != null) {
            Log.d(TAG, "Using locked grade for ID $id: $lockedGrade")
            return lockedGrade
        }

        val history = trackingHistory[id] ?: return "unknown"

        if (history.isEmpty()) return "unknown"

        // Jika hanya ada satu deteksi, langsung gunakan
        if (history.size == 1) return history.first().first

        // Cari grade dengan frekuensi tertinggi dalam histori
        val mostFrequentLabel = history
            .groupBy { it.first }
            .maxByOrNull { it.value.size }
            ?.key

        // Cari grade dengan prioritas tertinggi dalam histori
        val highestPriorityLabel = history
            .map { it.first }
            .maxByOrNull { gradePriority[it] ?: 0 }

        // Prioritaskan grade dengan prioritas tertinggi
        val result = highestPriorityLabel ?: "unknown"
        Log.d(TAG, "Using highest priority label for ID $id: $result")
        return result
    }

    // Hapus histori untuk ID tertentu
    fun removeHistory(id: Int) {
        trackingHistory.remove(id)
        lockedHighestGrade.remove(id)
        Log.d(TAG, "Removed history for ID $id")
    }

    // Reset seluruh histori
    fun reset() {
        trackingHistory.clear()
        lockedHighestGrade.clear()
        Log.d(TAG, "Reset all tracking history")
    }

    companion object {
        private const val TAG = "GradeStabilizer"
    }
}