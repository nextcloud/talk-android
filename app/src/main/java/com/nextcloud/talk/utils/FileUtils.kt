/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2021 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2021 Stefan Niedermann <info@niedermann.it>
 * SPDX-FileCopyrightText: 2020 Chris Narkiewicz <hello@ezaquarii.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import com.nextcloud.talk.models.ImageCompressionLevel
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.math.BigInteger
import java.security.MessageDigest
import kotlin.math.min

object FileUtils {
    private val TAG = FileUtils::class.java.simpleName
    private const val RADIX: Int = 16
    private const val MD5_LENGTH: Int = 32

    /**
     * Creates a new [File]
     */
    @Suppress("ThrowsCount")
    @JvmStatic
    fun getTempCacheFile(context: Context, fileName: String): File {
        val cacheFile = File(context.applicationContext.filesDir.absolutePath + "/" + fileName)
        Log.v(TAG, "Full path for new cache file:" + cacheFile.absolutePath)
        val tempDir = cacheFile.parentFile ?: throw FileNotFoundException("could not cacheFile.getParentFile()")
        if (!tempDir.exists()) {
            Log.v(
                TAG,
                "The folder in which the new file should be created does not exist yet. Trying to create it…"
            )
            if (tempDir.mkdirs()) {
                Log.v(TAG, "Creation successful")
            } else {
                throw IOException("Directory for temporary file does not exist and could not be created.")
            }
        }
        Log.v(TAG, "- Try to create actual cache file")
        if (cacheFile.createNewFile()) {
            Log.v(TAG, "Successfully created cache file")
        } else {
            throw IOException("Failed to create cacheFile")
        }
        return cacheFile
    }

    /**
     * Creates a new [File]
     */
    fun removeTempCacheFile(context: Context, fileName: String) {
        val cacheFile = File(context.applicationContext.filesDir.absolutePath + "/" + fileName)
        Log.v(TAG, "Full path for new cache file:" + cacheFile.absolutePath)
        if (cacheFile.exists()) {
            if (cacheFile.delete()) {
                Log.v(TAG, "Deletion successful")
            } else {
                throw IOException("Directory for temporary file does not exist and could not be created.")
            }
        }
    }

    @Suppress("ThrowsCount")
    fun getFileFromUri(context: Context, sourceFileUri: Uri): File? {
        val fileName = getFileName(sourceFileUri, context)
        val scheme = sourceFileUri.scheme

        val file = if (scheme == null) {
            Log.d(TAG, "relative uri: " + sourceFileUri.path)
            throw IllegalArgumentException("relative paths are not supported")
        } else if (ContentResolver.SCHEME_CONTENT == scheme) {
            copyFileToCache(context, sourceFileUri, fileName)
        } else if (ContentResolver.SCHEME_FILE == scheme) {
            if (sourceFileUri.path != null) {
                sourceFileUri.path?.let { File(it) }
            } else {
                throw IllegalArgumentException("uri does not contain path")
            }
        } else {
            throw IllegalArgumentException("unsupported scheme: " + sourceFileUri.path)
        }
        return file
    }

    @Suppress("NestedBlockDepth")
    fun copyFileToCache(context: Context, sourceFileUri: Uri, filename: String): File? {
        val cachedFile = File(context.cacheDir, filename)

        if (!cachedFile.toPath().normalize().startsWith(context.cacheDir.toPath())) {
            Log.w(TAG, "cachedFile was not created in cacheDir. Aborting for security reasons.")
            cachedFile.delete()
            return null
        }

        if (cachedFile.exists()) {
            Log.d(TAG, "file is already in cache")
        } else {
            val outputStream = FileOutputStream(cachedFile)
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(sourceFileUri)
                inputStream?.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
                outputStream.flush()
            } catch (e: FileNotFoundException) {
                Log.w(TAG, "failed to copy file to cache", e)
            }
        }
        return cachedFile
    }

    fun getFileName(uri: Uri, context: Context?): String {
        var filename: String? = null
        if (uri.scheme == "content" && context != null) {
            val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val displayNameColumnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (displayNameColumnIndex != -1) {
                        filename = cursor.getString(displayNameColumnIndex)
                    }
                }
            } finally {
                cursor?.close()
            }
        }
        // if it was no content uri, read filename from path
        if (filename == null) {
            filename = uri.path
        }

        val lastIndexOfSlash = filename!!.lastIndexOf('/')
        if (lastIndexOfSlash != -1) {
            filename = filename.substring(lastIndexOfSlash + 1)
        }

        return filename
    }

    @JvmStatic
    fun md5Sum(file: File): String {
        val temp = file.name + file.lastModified() + file.length()
        val messageDigest = MessageDigest.getInstance("MD5")
        messageDigest.update(temp.toByteArray())
        val digest = messageDigest.digest()
        val md5String = StringBuilder(BigInteger(1, digest).toString(RADIX))
        while (md5String.length < MD5_LENGTH) {
            md5String.insert(0, "0")
        }
        return md5String.toString()
    }

    /**
     * Determines if the given file is an image based on its MIME type
     */
    @JvmStatic
    fun isImageFile(file: File): Boolean {
        val mimeType = getMimeType(file)
        return mimeType?.startsWith("image/") == true
    }

    /**
     * Determines if the given URI is an image based on its MIME type
     */
    @JvmStatic
    fun isImageFile(context: Context, uri: Uri): Boolean {
        val mimeType = context.contentResolver.getType(uri)
        return mimeType?.startsWith("image/") == true
    }

    /**
     * Gets the MIME type of a file
     */
    @JvmStatic
    fun getMimeType(file: File): String? {
        val extension = file.extension.lowercase()
        return if (extension.isNotEmpty()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        } else {
            null
        }
    }

    /**
     * Compresses an image file with specified quality and maximum dimensions
     * @param inputFile The original image file
     * @param outputFile The file where the compressed image will be saved
     * @param quality JPEG compression quality (0-100)
     * @param maxWidth Maximum width in pixels
     * @param maxHeight Maximum height in pixels
     * @return true if compression was successful, false otherwise
     */
    @JvmStatic
    fun compressImageFile(
        inputFile: File,
        outputFile: File,
        quality: Int = 80,
        maxWidth: Int = 1280,
        maxHeight: Int = 1280
    ): Boolean = compressImageFileInternal(inputFile, outputFile, quality, maxWidth, maxHeight)

    /**
     * Compresses an image file using the specified compression level
     * @param inputFile The original image file
     * @param outputFile The file where the compressed image will be saved
     * @param compressionLevel The compression level to apply
     * @return true if compression was successful, false otherwise
     */
    @JvmStatic
    fun compressImageFile(inputFile: File, outputFile: File, compressionLevel: ImageCompressionLevel): Boolean =
        if (compressionLevel == ImageCompressionLevel.NONE) {
            // No compression - just copy the file
            try {
                inputFile.copyTo(outputFile, overwrite = true)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy file for no compression", e)
                false
            }
        } else {
            compressImageFileInternal(
                inputFile,
                outputFile,
                compressionLevel.quality,
                compressionLevel.maxWidth,
                compressionLevel.maxHeight
            )
        }

    /**
     * Internal implementation of image compression
     */
    private fun compressImageFileInternal(
        inputFile: File,
        outputFile: File,
        quality: Int,
        maxWidth: Int,
        maxHeight: Int
    ): Boolean {
        return try {
            Log.d(TAG, "Starting image compression:")
            Log.d(TAG, "  Input file: ${inputFile.name} (${inputFile.length()} bytes)")
            Log.d(TAG, "  Target quality: $quality")
            Log.d(TAG, "  Max dimensions: ${maxWidth}x$maxHeight")

            // Decode the image to get its dimensions first
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(inputFile.absolutePath, options)

            Log.d(TAG, "  Original dimensions: ${options.outWidth}x${options.outHeight}")
            Log.d(TAG, "  Original MIME type: ${options.outMimeType}")

            // Calculate sample size to reduce memory usage
            val sampleSize = calculateInSampleSize(options, maxWidth, maxHeight)
            Log.d(TAG, "  Calculated sample size: $sampleSize")

            // Decode the actual bitmap with sample size
            val decodingOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inJustDecodeBounds = false
            }

            val bitmap = BitmapFactory.decodeFile(inputFile.absolutePath, decodingOptions)
            if (bitmap == null) {
                Log.e(TAG, "Failed to decode bitmap from file: ${inputFile.absolutePath}")
                return false
            }

            Log.d(TAG, "  Decoded bitmap: ${bitmap.width}x${bitmap.height}, config: ${bitmap.config}")

            // Calculate final dimensions maintaining aspect ratio
            val (finalWidth, finalHeight) = calculateFinalDimensions(
                bitmap.width,
                bitmap.height,
                maxWidth,
                maxHeight
            )

            Log.d(TAG, "  Target final dimensions: ${finalWidth}x$finalHeight")

            // Scale the bitmap if necessary
            val scaledBitmap = if (bitmap.width != finalWidth || bitmap.height != finalHeight) {
                Log.d(TAG, "  Scaling bitmap from ${bitmap.width}x${bitmap.height} to ${finalWidth}x$finalHeight")
                Bitmap.createScaledBitmap(bitmap, finalWidth, finalHeight, true)
            } else {
                Log.d(TAG, "  No scaling needed, but will still apply quality compression")
                bitmap
            }

            // EMERGENCY TEST: Force a tiny test image to verify compression works
            Log.d(TAG, "EMERGENCY TEST: Creating tiny test image for compression verification")
            try {
                val testBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.RGB_565)
                testBitmap.eraseColor(android.graphics.Color.RED)

                val testFile = File(outputFile.parent, "test_compression.jpg")
                FileOutputStream(testFile).use { fos ->
                    val testSuccess = testBitmap.compress(Bitmap.CompressFormat.JPEG, 50, fos)
                    Log.d(TAG, "Test compression success: $testSuccess, file size: ${testFile.length()}")
                }
                testBitmap.recycle()
                testFile.delete()
            } catch (e: Exception) {
                Log.e(TAG, "Test compression failed", e)
            }

            // Always determine compression format for quality reduction
            // For compression, we prefer JPEG format for better size reduction
            val format = when (inputFile.extension.lowercase()) {
                "png" -> {
                    // For PNG files, only keep PNG format if transparency is needed and quality is high
                    if (hasTransparency(scaledBitmap) && quality >= 90) {
                        Log.d(TAG, "  Keeping PNG format due to transparency")
                        Bitmap.CompressFormat.PNG
                    } else {
                        // Convert to JPEG for better compression
                        Log.d(TAG, "  Converting PNG to JPEG for better compression")
                        Bitmap.CompressFormat.JPEG
                    }
                }
                "webp" -> if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    Log.d(TAG, "  Using WEBP_LOSSY format")
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Log.d(TAG, "  Using legacy WEBP format")
                    Bitmap.CompressFormat.WEBP
                }
                else -> {
                    Log.d(TAG, "  Using JPEG format")
                    Bitmap.CompressFormat.JPEG
                }
            }

            Log.d(
                TAG,
                "Compressing image: ${inputFile.name} (${inputFile.length()} bytes) with format: $format, quality: $quality"
            )

            // Save the compressed image - FORCE JPEG for maximum compression
            FileOutputStream(outputFile).use { fos ->
                // ALWAYS use JPEG for maximum compression (except PNG with transparency)
                val finalFormat = if (format == Bitmap.CompressFormat.PNG && hasTransparency(scaledBitmap)) {
                    Log.d(TAG, "  Keeping PNG due to transparency")
                    Bitmap.CompressFormat.PNG
                } else {
                    Log.d(TAG, "  FORCING JPEG compression for maximum size reduction")
                    Bitmap.CompressFormat.JPEG
                }

                val compressionQuality = if (finalFormat == Bitmap.CompressFormat.PNG) 100 else quality
                Log.d(TAG, "  Applying compression: format=$finalFormat, quality=$compressionQuality")

                val success = scaledBitmap.compress(finalFormat, compressionQuality, fos)
                fos.flush()

                if (!success) {
                    Log.e(TAG, "Failed to compress bitmap to output stream")
                    return false
                }

                Log.d(TAG, "  Compression to stream successful")
            }

            // Verify the output file was created and has content
            if (!outputFile.exists()) {
                Log.e(TAG, "Output file was not created")
                return false
            }

            if (outputFile.length() == 0L) {
                Log.e(TAG, "Output file is empty")
                return false
            }

            val originalSize = inputFile.length()
            val compressedSize = outputFile.length()

            // Force verification: If file sizes are identical, something went wrong
            if (originalSize == compressedSize && quality < 95) {
                Log.w(TAG, "WARNING: Compressed file has identical size to original despite quality < 95%")
                Log.w(TAG, "This suggests compression didn't work properly")

                // Force a VERY aggressive re-compression attempt
                Log.d(TAG, "Attempting VERY aggressive forced re-compression...")

                try {
                    FileOutputStream(outputFile).use { fos ->
                        val forcedQuality = 15 // Very low quality
                        val recompressSuccess = scaledBitmap.compress(Bitmap.CompressFormat.JPEG, forcedQuality, fos)
                        fos.flush()

                        if (recompressSuccess) {
                            Log.d(TAG, "Forced re-compression completed with quality: $forcedQuality")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed forced re-compression", e)
                }
            }

            val finalCompressedSize = outputFile.length()
            val compressionRatio = if (originalSize > 0) {
                ((originalSize - finalCompressedSize) * 100 / originalSize).toInt()
            } else {
                0
            }
            val compressedWidth = scaledBitmap.width
            val compressedHeight = scaledBitmap.height

            // Clean up bitmaps
            if (scaledBitmap != bitmap) {
                scaledBitmap.recycle()
            }
            bitmap.recycle()

            Log.d(TAG, "Image compression completed successfully:")
            Log.d(TAG, "  Original: ${originalSize / 1024}KB ($originalSize bytes)")
            Log.d(TAG, "  Compressed: ${finalCompressedSize / 1024}KB ($finalCompressedSize bytes)")
            Log.d(TAG, "  Compression ratio: $compressionRatio%")
            Log.d(TAG, "  Final dimensions: ${compressedWidth}x$compressedHeight")

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to compress image", e)
            false
        }
    }

    /**
     * Calculates the appropriate sample size for bitmap loading to reduce memory usage
     */
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /**
     * Calculates final dimensions while maintaining aspect ratio
     */
    private fun calculateFinalDimensions(
        originalWidth: Int,
        originalHeight: Int,
        maxWidth: Int,
        maxHeight: Int
    ): Pair<Int, Int> {
        // Always apply some reduction for compression, even if within limits
        val targetMaxWidth = kotlin.math.min(maxWidth, (originalWidth * 0.9).toInt())
        val targetMaxHeight = kotlin.math.min(maxHeight, (originalHeight * 0.9).toInt())

        if (originalWidth <= targetMaxWidth && originalHeight <= targetMaxHeight) {
            return Pair(originalWidth, originalHeight)
        }

        val aspectRatio = originalWidth.toFloat() / originalHeight.toFloat()

        return if (originalWidth > originalHeight) {
            val width = kotlin.math.min(originalWidth, targetMaxWidth)
            val height = (width / aspectRatio).toInt()
            Pair(width, height)
        } else {
            val height = kotlin.math.min(originalHeight, targetMaxHeight)
            val width = (height * aspectRatio).toInt()
            Pair(width, height)
        }
    }

    /**
     * Checks if a bitmap has transparency
     */
    private fun hasTransparency(bitmap: Bitmap): Boolean = bitmap.hasAlpha() && bitmap.config == Bitmap.Config.ARGB_8888
}
