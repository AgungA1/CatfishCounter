package com.catfish.tfvision_opencv.Utils

import org.opencv.core.Rect2d
import kotlin.math.max
import kotlin.math.min

fun calculateIoU(box1: Rect2d, box2: Rect2d): Double {
    val x1 = max(box1.x, box2.x)
    val y1 = max(box1.y, box2.y)
    val x2 = min(box1.x + box1.width, box2.x + box2.width)
    val y2 = min(box1.y + box1.height, box2.y + box2.height)

    if (x2 < x1 || y2 < y1) return 0.0

    val intersection = (x2 - x1) * (y2 - y1)
    val union = box1.width * box1.height + box2.width * box2.height - intersection
    return intersection / union
}