/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.attachmentpreview

import kotlin.math.ceil

/*
 * Pure, Android-free helpers behind the attachment preview editing features, kept apart from the
 * UI so they can be covered by plain JVM unit tests.
 */

private const val EDITED_SUFFIX = "_edited_"
private const val SHARED_URI_SEGMENT = "shared_attachments"
private val editedSuffixRegex = Regex("${EDITED_SUFFIX}[0-9-]+$")
private const val MAX_DECODE_PIXELS = 20_000_000L

/** A point on the displayed image, in 0..1 of the image's width/height (resolution independent). */
internal data class NormalizedPoint(val x: Float, val y: Float)

/** One finger-down..finger-up brush stroke. [widthFraction] is the brush width relative to the image width. */
internal data class DrawStroke(val colorArgb: Int, val widthFraction: Float, val points: List<NormalizedPoint>)

/** Where an image of a given aspect ratio lands when fitted (letterboxed) into a view, in view pixels. */
internal data class FitRect(val left: Float, val top: Float, val width: Float, val height: Float)

/** Replaces [oldUri] by [newUri] keeping its position; returns [files] unchanged when [oldUri] is absent. */
internal fun replaceUri(files: List<String>, oldUri: String, newUri: String): List<String> {
    val index = files.indexOf(oldUri)
    return if (index < 0 || oldUri == newUri) {
        files
    } else {
        files.toMutableList().also { it[index] = newUri }
    }
}

/**
 * Toggles [uri] in the set of files excluded from sending. At least one file always stays selected,
 * so excluding the last selected file is refused (the set is returned unchanged).
 */
internal fun toggleSelection(files: List<String>, unselected: Set<String>, uri: String): Set<String> =
    when {
        uri !in files -> unselected
        uri in unselected -> unselected - uri
        files.count { it !in unselected } <= 1 -> unselected
        else -> unselected + uri
    }

/** The files that will actually be sent, in list order. */
internal fun selectedFiles(files: List<String>, unselected: Set<String>): List<String> =
    files.filter { it !in unselected }

/**
 * Name for the file an edit produces: the original base name without extension and without an
 * earlier edit suffix (so repeated edits don't pile suffixes up), plus a unique [stamp].
 */
internal fun editedFileName(originalName: String, stamp: String, extension: String): String {
    val base = originalName.substringBeforeLast('.', originalName).replace(editedSuffixRegex, "")
    return "${base.ifBlank { "image" }}$EDITED_SUFFIX$stamp.$extension"
}

/** True for names produced by [editedFileName], i.e. intermediates of an earlier edit. */
internal fun isEditedFileName(fileName: String): Boolean =
    editedSuffixRegex.containsMatchIn(fileName.substringBeforeLast('.', fileName))

/**
 * The edited file's name when [pathSegments] of a URI under [authority] address exactly
 * `shared_attachments/<edit-suffixed name>` in our own FileProvider; null for anything else
 * (foreign authority, an original/camera file, a nested path). Only those are ours to delete.
 */
internal fun ownEditedName(authority: String?, ownAuthority: String, pathSegments: List<String>): String? {
    val name = pathSegments.getOrNull(1)
    val matches = authority == ownAuthority &&
        pathSegments.size == 2 &&
        pathSegments.first() == SHARED_URI_SEGMENT &&
        name != null &&
        isEditedFileName(name)
    return if (matches) name else null
}

/**
 * Whether drawing must turn the pixels upright instead of carrying the EXIF orientation tag over.
 * Mirrored orientations (2/4/5/7) are not understood downstream, and PNG is not rotated by EXIF at
 * all; only JPEG with the plain rotations 3/6/8 keeps the tag.
 */
internal fun rotatesPixels(png: Boolean, rotationDegrees: Int, flipped: Boolean): Boolean =
    flipped || (png && rotationDegrees != 0)

/** Output extension and whether it is PNG (lossless, keeps transparency) for a source MIME type. */
internal fun editOutputIsPng(sourceMimeType: String?): Boolean = sourceMimeType == "image/png"

/** Fits an image of [aspectRatio] (width / height) into the view like `ContentScale.Fit`, centered. */
internal fun fitRect(viewWidth: Float, viewHeight: Float, aspectRatio: Float): FitRect {
    val viewRatio = viewWidth / viewHeight
    val (width, height) = if (viewRatio > aspectRatio) {
        (viewHeight * aspectRatio) to viewHeight
    } else {
        viewWidth to (viewWidth / aspectRatio)
    }
    return FitRect((viewWidth - width) / 2f, (viewHeight - height) / 2f, width, height)
}

/** Maps a view-space touch position to the image, clamped so strokes can't leave the picture. */
internal fun toNormalized(x: Float, y: Float, rect: FitRect): NormalizedPoint =
    NormalizedPoint(
        ((x - rect.left) / rect.width).coerceIn(0f, 1f),
        ((y - rect.top) / rect.height).coerceIn(0f, 1f)
    )

/** Maps a normalized point to pixels of a bitmap of the given size. */
internal fun toPixels(point: NormalizedPoint, bitmapWidth: Int, bitmapHeight: Int): Pair<Float, Float> =
    (point.x * bitmapWidth) to (point.y * bitmapHeight)

/** Brush width in bitmap pixels; never thinner than one pixel. */
internal fun strokeWidthPixels(stroke: DrawStroke, bitmapWidth: Int): Float =
    (stroke.widthFraction * bitmapWidth).coerceAtLeast(1f)

/** Drops the most recent stroke ("undo"). */
internal fun undoLast(strokes: List<DrawStroke>): List<DrawStroke> = strokes.dropLast(1)

/**
 * Power-of-two `inSampleSize` that keeps a decoded bitmap under [maxPixels]; 1 means full resolution.
 * Only very large sources (beyond ~20 MP) are downsampled, to avoid running out of memory.
 */
internal fun decodeSampleSize(width: Int, height: Int, maxPixels: Long = MAX_DECODE_PIXELS): Int {
    var sample = 1
    var pixels = width.toLong() * height.toLong()
    while (pixels > maxPixels) {
        sample *= 2
        pixels = ceil(width / sample.toDouble()).toLong() * ceil(height / sample.toDouble()).toLong()
    }
    return sample
}
