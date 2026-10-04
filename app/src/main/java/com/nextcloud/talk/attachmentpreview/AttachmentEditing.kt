/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.attachmentpreview

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.exifinterface.media.ExifInterface
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.utils.FileUtils
import com.yalantis.ucrop.UCrop
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private const val TAG = "AttachmentEditing"
private const val EDIT_FILE_STAMP_PATTERN = "yyyyMMdd-HHmmss-SSS"
private const val EDIT_JPEG_QUALITY = 95
private const val EDIT_PNG_QUALITY = 100

// uCrop crops the bitmap it decoded for display, not the original file, and decodes it no larger
// than this on either side (by default roughly the screen diagonal, ~2600 px) - so without an
// explicit limit an HD photo would silently come out downscaled. 4096 keeps a typical 12 MP photo
// at full size, stays inside every GPU's texture limit and bounds the decoded bitmap to ~64 MB.
private const val CROP_MAX_BITMAP_PX = 4096

/**
 * Creates the (not yet existing) output file of an edit in the cache dir shared through the app's
 * FileProvider; null when that directory is unavailable (anything else would be unshareable).
 */
internal fun createEditOutputFile(context: Context, sourceName: String, sourceMimeType: String?): File? {
    val directory = FileUtils.getSharedAttachmentsDirectory(context.cacheDir) ?: return null
    val stamp = SimpleDateFormat(EDIT_FILE_STAMP_PATTERN, Locale.ROOT).format(Date())
    val extension = if (editOutputIsPng(sourceMimeType)) "png" else "jpg"
    return File(directory, editedFileName(sourceName, stamp, extension))
}

/** The same URI form the camera capture uses for files in the shared attachments cache; null if not shareable. */
internal fun editedFileUri(context: Context, file: File): Uri? =
    try {
        FileProvider.getUriForFile(context, context.packageName, file)
    } catch (e: IllegalArgumentException) {
        NextcloudTalkApplication.sharedApplication?.logger?.w(TAG, "Edited file is outside the shared paths", e)
        null
    }

/**
 * The file behind [uriString] if it is an intermediate produced by an earlier edit (our FileProvider,
 * shared attachments cache, edit-suffixed name) - safe to delete once superseded. Null for anything
 * else, e.g. the user's original photo or a camera shot.
 */
internal fun ownEditedFile(context: Context, uriString: String): File? {
    val uri = Uri.parse(uriString)
    val name = ownEditedName(uri.authority, context.packageName, uri.pathSegments)
    return name?.let { FileUtils.resolveSharedAttachmentFile(context.cacheDir, it) }
}

/** Intent of uCrop's crop-and-rotate screen, writing the result to [destination]. */
internal fun createCropIntent(context: Context, source: Uri, destination: File, sourceMimeType: String?): Intent {
    val png = editOutputIsPng(sourceMimeType)
    val options = UCrop.Options().apply {
        setCompressionFormat(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG)
        setCompressionQuality(if (png) EDIT_PNG_QUALITY else EDIT_JPEG_QUALITY)
        setFreeStyleCropEnabled(true)
        setMaxBitmapSize(CROP_MAX_BITMAP_PX)
    }
    return UCrop.of(source, Uri.fromFile(destination)).withOptions(options).getIntent(context)
}

/**
 * Burns [strokes] into a copy of [source] at (at most ~20 MP of) its own resolution and writes it
 * to [destination]. Only one bitmap is ever held: the strokes are drawn straight onto the decoded,
 * un-rotated pixels through the inverse of the EXIF transform, and the orientation tag is carried
 * over to the output instead of rotating the pixels. Returns false on any failure.
 */
internal fun renderDrawing(
    context: Context,
    source: Uri,
    strokes: List<DrawStroke>,
    destination: File,
    png: Boolean
): Boolean =
    try {
        drawOnDecodedCopy(context, source, strokes, destination, png)
    } catch (e: IOException) {
        logRenderFailure(e)
    } catch (e: SecurityException) {
        // e.g. a Photo Picker uri whose read grant was revoked
        logRenderFailure(e)
    } catch (e: OutOfMemoryError) {
        logRenderFailure(e)
    }.also { if (!it) destination.delete() }

private fun logRenderFailure(error: Throwable): Boolean {
    NextcloudTalkApplication.sharedApplication?.logger?.w(TAG, "Failed to save drawing", error)
    return false
}

private fun drawOnDecodedCopy(
    context: Context,
    source: Uri,
    strokes: List<DrawStroke>,
    destination: File,
    png: Boolean
): Boolean {
    val resolver = context.contentResolver
    val exif = resolver.openInputStream(source)?.use { ExifInterface(it) }
    val bitmap = decodeRaw(context, source)
    val written = bitmap != null && paintAndWrite(bitmap, exif, strokes, destination, png)
    bitmap?.recycle()
    return written
}

private fun decodeRaw(context: Context, source: Uri): Bitmap? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val options = BitmapFactory.Options().apply {
        inSampleSize = decodeSampleSize(bounds.outWidth, bounds.outHeight)
        inMutable = true
    }
    return resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, options) }
}

private fun paintAndWrite(
    bitmap: Bitmap,
    exif: ExifInterface?,
    strokes: List<DrawStroke>,
    destination: File,
    png: Boolean
): Boolean {
    val degrees = exif?.rotationDegrees ?: 0
    val flipped = exif?.isFlipped == true
    val toUpright = uprightMatrix(degrees, flipped, bitmap.width, bitmap.height)
    val bounds = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()).also { toUpright.mapRect(it) }
    drawStrokes(bitmap, strokes, toUpright, bounds.width().roundToInt(), bounds.height().roundToInt())

    val turnPixels = rotatesPixels(png, degrees, flipped)
    val output = if (turnPixels) uprightCopy(bitmap, toUpright, bounds) else bitmap
    val format = if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
    val quality = if (png) EDIT_PNG_QUALITY else EDIT_JPEG_QUALITY
    val written = FileOutputStream(destination).use { output.compress(format, quality, it) }
    if (output !== bitmap) output.recycle()
    if (written && !turnPixels && degrees != 0) {
        ExifInterface(destination.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, exif?.getAttribute(ExifInterface.TAG_ORIENTATION))
            saveAttributes()
        }
    }
    return written
}

/** The pixels turned/mirrored upright; the raw [bitmap] is released first to keep the memory peak low. */
private fun uprightCopy(bitmap: Bitmap, toUpright: Matrix, bounds: RectF): Bitmap {
    val upright = createBitmap(bounds.width().roundToInt(), bounds.height().roundToInt())
    Canvas(upright).drawBitmap(bitmap, toUpright, Paint(Paint.FILTER_BITMAP_FLAG))
    bitmap.recycle()
    return upright
}

/** Raw-pixel to upright-display transform for an EXIF orientation, translated to start at 0,0. */
internal fun uprightMatrix(rotationDegrees: Int, flipped: Boolean, width: Int, height: Int): Matrix {
    val matrix = Matrix().apply {
        if (flipped) postScale(-1f, 1f)
        postRotate(rotationDegrees.toFloat())
    }
    val mapped = RectF(0f, 0f, width.toFloat(), height.toFloat()).also { matrix.mapRect(it) }
    matrix.postTranslate(-mapped.left, -mapped.top)
    return matrix
}

/** Draws [strokes] (positioned on the upright [uprightWidth]x[uprightHeight] picture) onto the raw [bitmap]. */
private fun drawStrokes(
    bitmap: Bitmap,
    strokes: List<DrawStroke>,
    toUpright: Matrix,
    uprightWidth: Int,
    uprightHeight: Int
) {
    val canvas = Canvas(bitmap)
    canvas.concat(Matrix().also { toUpright.invert(it) })
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    strokes.filter { it.points.isNotEmpty() }.forEach { stroke ->
        paint.color = stroke.colorArgb
        paint.strokeWidth = strokeWidthPixels(stroke, uprightWidth)
        val pixels = stroke.points.map { toPixels(it, uprightWidth, uprightHeight) }
        if (pixels.size == 1) {
            canvas.drawPoint(pixels.first().first, pixels.first().second, paint)
        } else {
            canvas.drawPath(strokePath(pixels), paint)
        }
    }
}

private fun strokePath(pixels: List<Pair<Float, Float>>): Path =
    Path().apply {
        moveTo(pixels.first().first, pixels.first().second)
        pixels.drop(1).forEach { lineTo(it.first, it.second) }
    }
