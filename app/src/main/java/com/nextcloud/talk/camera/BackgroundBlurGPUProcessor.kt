/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.camera

import android.content.Context
import android.opengl.GLES20
import com.nextcloud.talk.R
import org.webrtc.EglBase
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.cos
import kotlin.math.sin

/**
 * OpenGL has a lot of boilerplate, because you have to deal with two different memory spaces, CPU and GPU
 * Therefore it operates a lot like a state machine under the hood, hence why we need to explicitly define
 * variables, bind those variables to memory in the gpu, activate/deactivate that memory, send/read data from
 * the GPU, and release memory after we're done using it. In addition, a lot of these functions have terrible
 * documentation, with vague naming, and unclear parameters. I would recommend just copying the function name
 * and Ctrl + F [here](https://developer.android.com/reference/android/opengl/GLES20.html)
 */
@Suppress("TooManyFunctions", "TooGenericExceptionCaught", "TooGenericExceptionThrown")
class BackgroundBlurGPUProcessor(val context: Context) {

    companion object {
        // Quad Coordinates (Full Screen)
        private val QUAD_COORDS = floatArrayOf(
            -1.0f,
            1.0f, // Top Left
            -1.0f,
            -1.0f, // Bottom Left
            1.0f,
            1.0f, // Top Right
            1.0f,
            -1.0f // Bottom Right
        )

        // Texture Coordinates
        private val TEX_COORDS = floatArrayOf(
            0.0f,
            0.0f, // Top Left
            0.0f,
            1.0f, // Bottom Left
            1.0f,
            0.0f, // Top Right
            1.0f,
            1.0f // Bottom Right
        )

        const val INT_QUAD_SIZE = 4
        const val INT_TEX_SIZE = 4
        const val INT_TRI_SIZE = 4
        const val INT_RGBA_SIZE = 4
        const val FLOAT_ASPECT_RATIO = 4 / 3.0f
        const val FLOAT_0_P5 = 0.5f
    }

    private var eglBase: EglBase? = null

    // Programs (Gpu Objects) for running gaussian blur
    private var gaussianBlurProgramId: Int = 0
    private var maskingOperationProgramId: Int = 0

    // Mask and original video frame identifiers to their objects on the gpu
    private var maskInputTextureId: Int = 0
    private var frameInputTextureId: Int = 0

    // FBO (Frame buffer object) these hold the temporary state of the blurring and masking between operations
    // Each FBO is an identifier that is linked to an object in gpu memory, that can be read from the texture
    // (another object in gpu memory, it's like a 2d Array) which is linked to each FBO
    private var blurFBOAId: Int = 0
    private var blurFBOTextureAId: Int = 0

    private var blurFBOBId: Int = 0
    private var blurFBOTextureBId: Int = 0

    private var blurFBOCId: Int = 0
    private var blurFBOTextureCId: Int = 0

    // These are linked to variables in the shader code
    private var positionHandle: Int = 0
    private var texCoordHandle: Int = 0
    private var maskTextureUniformHandle: Int = 0
    private var frameTextureUniformHandle: Int = 0
    private var blurredTextureUniformHandle: Int = 0
    private var blurDirectionUniformHandle: Int = 0

    // Shaders identifiers, reference their location in gpu memory
    private var gaussianBlurShaderId: Int = 0
    private var maskingOpShaderId: Int = 0

    // Geometry Buffers, tell the program how to orient the textures
    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var texCoordBuffer: FloatBuffer

    // Output Buffer, this is what is returned from process()
    private var cachedWidth = -1
    private var cachedHeight = -1
    private var pixelBuffer: ByteBuffer? = null

    fun init() {
        // OpenGL needs some surface to draw onto, even if it's just a 1x1 pixel
        eglBase = EglBase.create()
        try {
            eglBase?.createDummyPbufferSurface()
        } catch (_: Exception) {
            eglBase?.createPbufferSurface(1, 1)
        }
        eglBase?.makeCurrent()

        setupVertexAndTextureBuffer()

        // Compile Shaders and Link Program
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, R.raw.background_blur_vertex)
        gaussianBlurShaderId = loadShader(GLES20.GL_FRAGMENT_SHADER, R.raw.gaussian_blur_frag_shader)
        maskingOpShaderId = loadShader(GLES20.GL_FRAGMENT_SHADER, R.raw.seg_mask_frag_shader)

        setupGaussianBlurProgram(vertexShader)

        setupSegmentationMaskProgram(vertexShader)

        // Initialize handle variables to point to gpu memory
        positionHandle = GLES20.glGetAttribLocation(gaussianBlurProgramId, "a_Position")
        texCoordHandle = GLES20.glGetAttribLocation(gaussianBlurProgramId, "a_TexCoord")

        // Initialize the locations of the shader code uniform variables
        maskTextureUniformHandle = GLES20.glGetUniformLocation(maskingOperationProgramId, "u_MaskTexture")
        frameTextureUniformHandle = GLES20.glGetUniformLocation(maskingOperationProgramId, "u_FrameTexture")
        blurredTextureUniformHandle = GLES20.glGetUniformLocation(gaussianBlurProgramId, "u_BlurredTexture")
        blurDirectionUniformHandle = GLES20.glGetUniformLocation(gaussianBlurProgramId, "u_Direction")

        setupMaskInputTexture()

        setupFrameInputTexture()
    }

    private fun setupVertexAndTextureBuffer() {
        vertexBuffer = ByteBuffer
            .allocateDirect(QUAD_COORDS.size * INT_QUAD_SIZE)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

        vertexBuffer
            .put(QUAD_COORDS)
            .position(0)

        // Setup Texture Coordinate Buffer
        texCoordBuffer = ByteBuffer
            .allocateDirect(TEX_COORDS.size * INT_TEX_SIZE)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

        texCoordBuffer
            .put(TEX_COORDS)
            .position(0)
    }

    // An OpenGL Program is like a compiled executable (a .exe file)
    // glAttachShader is like adding a .c source file to a project
    // glLinkProgram is the compiler that turns those files into the executable
    // glUseProgram loads the executable
    // glDrawArrays runs the executable

    private fun setupGaussianBlurProgram(vertexShader: Int) {
        gaussianBlurProgramId = GLES20.glCreateProgram()
        GLES20.glAttachShader(gaussianBlurProgramId, vertexShader)
        GLES20.glAttachShader(gaussianBlurProgramId, gaussianBlurShaderId)
        GLES20.glLinkProgram(gaussianBlurProgramId)
    }

    private fun setupSegmentationMaskProgram(vertexShader: Int) {
        maskingOperationProgramId = GLES20.glCreateProgram()
        GLES20.glAttachShader(maskingOperationProgramId, vertexShader)
        GLES20.glAttachShader(maskingOperationProgramId, maskingOpShaderId)
        GLES20.glLinkProgram(maskingOperationProgramId)
    }

    private fun setupFrameInputTexture() {
        // Generate Frame Input Texture Holder
        val frameTextures = IntArray(1)
        GLES20.glGenTextures(1, frameTextures, 0)
        frameInputTextureId = frameTextures[0]

        // Configure Input Texture parameters
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, frameInputTextureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    private fun setupMaskInputTexture() {
        // Generate Mask Input Texture Holder
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        maskInputTextureId = textures[0]

        // Configure Input Texture parameters
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, maskInputTextureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    /**
     * This function takes in the selfie segmentation mask data, which is a matrix of int 0 or 1
     * and runs it through the GPU pipeline to process it according to the shader code.
     *
     * NOTE: The output byte array is 4x the size of the input, to represent 4 int RGBA values
     *
     * @param maskData The raw 0/1 byte array from MediaPipe
     * @param width Width of the mask
     * @param height Height of the mask
     *
     * @return RGBA ByteArray where pixels are either (0,0,0,255) or (255,255,255,255)
     */
    fun process(maskData: ByteArray, frameBuffer: ByteBuffer, width: Int, height: Int, rotation: Float): ByteArray {
        // Setup Framebuffer if dimensions changed
        prepareFramebuffers(width, height)

        uploadMaskToTexture(maskData, width, height)

        uploadVideoFrameToTexture(frameBuffer, width, height)

        setUpVertexAndTextureCoordBuffers(rotation)

        GLES20.glViewport(0, 0, width, height)

        GLES20.glUseProgram(gaussianBlurProgramId)

        horizontalPass()

        verticalPass()

        maskingOperation(rotation)

        // We rewind the buffer to ensure we read from the start
        pixelBuffer!!.rewind()

        // Read Pixels Back to CPU from FBO C Texture (since it's the last one to call bind frame buffer)
        // This is a blocking operation as it waits for drawing to be completed before reading
        // shouldn't be a problem as this code should live on the dedicated GPU Processing thread
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixelBuffer)

        cleanUpVertexAndTextureBuffers()

        return pixelBuffer!!.array()
    }

    private fun horizontalPass() {
        // glBindFrameBuffer sets dst to blurFBOA, glBindTexture sets src to original video
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, blurFBOAId)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, frameInputTextureId)
        GLES20.glUniform2f(blurDirectionUniformHandle, 1.0f, 0.0f)
        GLES20.glUniform1i(blurredTextureUniformHandle, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, INT_TRI_SIZE) // render horizontal pass -> fbo a texture
    }

    private fun verticalPass() {
        // glBindFrameBuffer sets dst to blurFBOB, glBindTexture sets src to blurFBOTextureA
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, blurFBOBId)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, blurFBOTextureAId)
        GLES20.glUniform2f(blurDirectionUniformHandle, 0.0f, 1.0f)
        GLES20.glUniform1i(blurredTextureUniformHandle, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, INT_TRI_SIZE) // render vertical pass -> fbo b texture
    }

    private fun maskingOperation(rotation: Float) {
        GLES20.glUseProgram(maskingOperationProgramId)
        blurredTextureUniformHandle = GLES20.glGetUniformLocation(maskingOperationProgramId, "u_BlurredTexture")

        positionHandle = GLES20.glGetAttribLocation(maskingOperationProgramId, "a_Position")
        texCoordHandle = GLES20.glGetAttribLocation(maskingOperationProgramId, "a_TexCoord")

        setUpVertexAndTextureCoordBuffers(rotation)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, maskInputTextureId)
        GLES20.glUniform1i(maskTextureUniformHandle, 0) // Mask to Unit 0

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, frameInputTextureId)
        GLES20.glUniform1i(frameTextureUniformHandle, 1) // Video Frame to Unit 1

        // glBindFrameBuffer sets dst to blurFBOC, glBindTexture sets src to blurFBOTextureB
        GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, blurFBOCId)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, blurFBOTextureBId)
        GLES20.glUniform1i(blurredTextureUniformHandle, 2)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, INT_TRI_SIZE) // render final image -> fbo c texture
    }

    private fun cleanUpVertexAndTextureBuffers() {
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0) // Unbind FBO to return to default display
    }

    private fun setUpVertexAndTextureCoordBuffers(rotation: Float) {
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        texCoordBuffer = getRotatedTexCoords(rotation)

        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)
    }

    // Basically first you move the coordinates to the center,
    // Multiply x by the aspect ratio for a correct scale
    // then you perform the multiplication operation,
    // Divide X by ratio to return to original scale
    // before moving them back to the origin
    private fun getRotatedTexCoords(angleDegrees: Float): FloatBuffer {
        val radians = Math.toRadians(angleDegrees.toDouble())
        val cosA = cos(radians).toFloat()
        val sinA = sin(radians).toFloat()
        val ratio = FLOAT_ASPECT_RATIO

        // Texture center points
        val cx = FLOAT_0_P5
        val cy = FLOAT_0_P5

        val rotatedCoords = FloatArray(TEX_COORDS.size)

        for (i in TEX_COORDS.indices step 2) {
            val u = TEX_COORDS[i]
            val v = TEX_COORDS[i + 1]

            var translatedU = u - cx
            val translatedV = v - cy

            translatedU *= ratio
            // 2D matrix rotation
            var rotatedU = translatedU * cosA - translatedV * sinA // [ cos(A) -sin(A) ] [ x ]   [ x*cos(A) - y*sin(A) ]
            val rotatedV = translatedU * sinA + translatedV * cosA // [ sin(A)  cos(A) ] [ y ] = [ x*sin(A) + y*cos(A) ]

            rotatedU /= ratio

            rotatedCoords[i] = rotatedU + cx
            rotatedCoords[i + 1] = rotatedV + cy
        }

        return ByteBuffer.allocateDirect(rotatedCoords.size * INT_TEX_SIZE)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(rotatedCoords)
                position(0)
            }
    }

    private fun prepareFramebuffers(width: Int, height: Int) {
        if (cachedWidth == width && cachedHeight == height && blurFBOAId != 0) {
            return // Size hasn't changed, reuse FBO
        }

        cachedWidth = width
        cachedHeight = height

        // Cleanup old FBO/Texture if they exist
        cleanUpOldState()

        // Create Frame Buffer Object A and Texture A - to hold horizontal gaussian blur pass
        setUpFboTextureA(width, height)
        setUpFboA()

        // Create Frame Buffer Object B and Texture B - to hold the final vertical + horizontal pass
        setUpFboTextureB(width, height)
        setUpFboB()

        // Create Frame Buffer Object C and Texture C - to hold the final output image after masking operation
        setUpFboTextureC(width, height)
        setUpFboC()

        // Check status
        val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            throw RuntimeException("Framebuffer invalid status: $status")
        }

        // Allocate CPU buffer for reading result (4 bytes per pixel for RGBA)
        pixelBuffer = ByteBuffer.allocate(width * height * INT_RGBA_SIZE)
    }

    private fun uploadMaskToTexture(maskData: ByteArray, width: Int, height: Int) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, maskInputTextureId)

        val buffer = ByteBuffer.wrap(maskData)
        // Note: Using GL_LUMINANCE for single channel byte input
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, width, height,
            0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, buffer
        )

        GLES20.glUniform1i(maskTextureUniformHandle, 0)
    }

    private fun uploadVideoFrameToTexture(frameBuffer: ByteBuffer, width: Int, height: Int) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, frameInputTextureId)

        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height,
            0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, frameBuffer
        )

        GLES20.glUniform1i(frameTextureUniformHandle, 1)
    }

    private fun setUpFboA() {
        val fbos = IntArray(1)
        GLES20.glGenFramebuffers(1, fbos, 0)
        blurFBOAId = fbos[0]
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, blurFBOAId)

        // Attach texture to FBO
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER,
            GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D,
            blurFBOTextureAId,
            0
        )
    }

    private fun setUpFboTextureA(width: Int, height: Int) {
        val texs = IntArray(1)
        GLES20.glGenTextures(1, texs, 0)
        blurFBOTextureAId = texs[0]

        GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, blurFBOTextureAId)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            GLES20.GL_RGBA,
            width,
            height,
            0,
            GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE,
            null
        )

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    private fun setUpFboB() {
        val fbos = IntArray(1)
        GLES20.glGenFramebuffers(1, fbos, 0)
        blurFBOBId = fbos[0]
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, blurFBOBId)

        // Attach texture to FBO
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER,
            GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D,
            blurFBOTextureBId,
            0
        )
    }

    private fun setUpFboTextureB(width: Int, height: Int) {
        val texs = IntArray(1)
        GLES20.glGenTextures(1, texs, 0)
        blurFBOTextureBId = texs[0]

        GLES20.glActiveTexture(GLES20.GL_TEXTURE3)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, blurFBOTextureBId)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            GLES20.GL_RGBA,
            width,
            height,
            0,
            GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE,
            null
        )

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    private fun setUpFboC() {
        val fbos = IntArray(1)
        GLES20.glGenFramebuffers(1, fbos, 0)
        blurFBOCId = fbos[0]
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, blurFBOCId)

        // Attach texture to FBO
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER,
            GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D,
            blurFBOTextureCId,
            0
        )
    }

    private fun setUpFboTextureC(width: Int, height: Int) {
        val texs = IntArray(1)
        GLES20.glGenTextures(1, texs, 0)
        blurFBOTextureCId = texs[0]

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, blurFBOTextureCId)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D,
            0,
            GLES20.GL_RGBA,
            width,
            height,
            0,
            GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE,
            null
        )

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    private fun cleanUpOldState() {
        if (blurFBOAId != 0) {
            val fbos = intArrayOf(blurFBOAId)
            val texs = intArrayOf(blurFBOTextureAId)
            GLES20.glDeleteFramebuffers(1, fbos, 0)
            GLES20.glDeleteTextures(1, texs, 0)
        }

        if (blurFBOBId != 0) {
            val fbos = intArrayOf(blurFBOBId)
            val texs = intArrayOf(blurFBOTextureBId)
            GLES20.glDeleteFramebuffers(1, fbos, 0)
            GLES20.glDeleteTextures(1, texs, 0)
        }

        if (blurFBOCId != 0) {
            val fbos = intArrayOf(blurFBOCId)
            val texs = intArrayOf(blurFBOTextureCId)
            GLES20.glDeleteFramebuffers(1, fbos, 0)
            GLES20.glDeleteTextures(1, texs, 0)
        }
    }

    private fun loadShader(type: Int, id: Int): Int {
        val input = context.resources.openRawResource(id)
        val name = context.resources.getResourceName(id)
        val shaderCode = input.bufferedReader().use { it.readText() } // defaults to UTF-8

        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)

        // Compilation check (optional but recommended for debugging)
        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            val error = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw RuntimeException("Shader Compilation Error($name): $error")
        }

        return shader
    }

    fun release() {
        if (eglBase != null) {
            GLES20.glDeleteProgram(gaussianBlurProgramId) // this also deletes the shaders too
            GLES20.glDeleteProgram(maskingOperationProgramId)
            val textures = intArrayOf(
                maskInputTextureId,
                frameInputTextureId,
                blurFBOTextureAId,
                blurFBOTextureBId,
                blurFBOTextureCId
            )
            GLES20.glDeleteTextures(textures.size, textures, 0)
            val fbos = intArrayOf(blurFBOAId, blurFBOBId, blurFBOCId)
            GLES20.glDeleteFramebuffers(fbos.size, fbos, 0)

            eglBase?.release()
            eglBase = null
        }
    }
}
