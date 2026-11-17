package com.nightvision.app.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * TensorFlow Lite Zero-DCE++ low-light image enhancer.
 * Loads `zero_dce_lite.tflite` from assets, attempts GPU delegate acceleration first,
 * and falls back cleanly to 4-thread CPU execution on unsupported devices.
 */
class ZeroDceEnhancer(
    context: Context,
    modelAssetName: String = DEFAULT_MODEL_ASSET
) {
    data class EnhancementResult(
        val bitmap: Bitmap,
        val inferenceTimeMs: Long
    )

    companion object {
        private const val TAG = "ZeroDceEnhancer"
        private const val DEFAULT_MODEL_ASSET = "zero_dce_lite.tflite"
        private const val CPU_THREADS = 4
    }

    private var gpuDelegate: GpuDelegate? = null
    private val interpreter: Interpreter?

    private val inputHeight: Int
    private val inputWidth: Int

    private val inputBitmap: Bitmap
    private val inputCanvas: Canvas
    private val inputPixels: IntArray
    private val outputPixels: IntArray
    private val inputBuffer: ByteBuffer
    private val outputBuffer: ByteBuffer
    private var outputBitmap: Bitmap

    val isUsingGpu: Boolean
        get() = gpuDelegate != null

    init {
        var createdInterpreter: Interpreter? = null
        var createdGpu: GpuDelegate? = null

        try {
            val modelBuffer = loadModelFile(context, modelAssetName)

            val compatList = CompatibilityList()
            if (compatList.isDelegateSupportedOnThisDevice) {
                try {
                    val delegate = GpuDelegate()
                    val options = Interpreter.Options().apply {
                        addDelegate(delegate)
                    }
                    createdInterpreter = Interpreter(modelBuffer, options)
                    createdGpu = delegate
                    Log.i(TAG, "Initialized TFLite interpreter with GPU delegate")
                } catch (t: Throwable) {
                    Log.w(TAG, "GPU delegate failed, falling back to CPU ($CPU_THREADS threads)", t)
                    createdGpu?.close()
                    createdGpu = null
                    createdInterpreter?.close()
                    createdInterpreter = null
                }
            }

            if (createdInterpreter == null) {
                val cpuOptions = Interpreter.Options().apply {
                    setNumThreads(CPU_THREADS)
                }
                createdInterpreter = Interpreter(modelBuffer, cpuOptions)
                Log.i(TAG, "Initialized TFLite interpreter on CPU ($CPU_THREADS threads)")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to initialize ZeroDceEnhancer", t)
        }

        interpreter = createdInterpreter
        gpuDelegate = createdGpu

        val shape = interpreter?.getInputTensor(0)?.shape()
        inputHeight = if (shape != null && shape.size >= 4) shape[1] else 512
        inputWidth = if (shape != null && shape.size >= 4) shape[2] else 512

        inputBitmap = Bitmap.createBitmap(inputWidth, inputHeight, Bitmap.Config.ARGB_8888)
        inputCanvas = Canvas(inputBitmap)
        outputBitmap = Bitmap.createBitmap(inputWidth, inputHeight, Bitmap.Config.ARGB_8888)

        val pixelCount = inputWidth * inputHeight
        inputPixels = IntArray(pixelCount)
        outputPixels = IntArray(pixelCount)

        val bufferBytes = pixelCount * 3 * Float.SIZE_BYTES
        inputBuffer = ByteBuffer.allocateDirect(bufferBytes).order(ByteOrder.nativeOrder())
        outputBuffer = ByteBuffer.allocateDirect(bufferBytes).order(ByteOrder.nativeOrder())
    }

    /**
     * Enhances a CameraX [ImageProxy] (expected RGBA_8888) and returns the enhanced [Bitmap]
     * along with the measured TFLite inference duration in milliseconds.
     */
    @Synchronized
    fun enhance(imageProxy: ImageProxy): EnhancementResult? {
        if (interpreter == null) return null
        val rawBitmap = imageProxy.toBitmap()
        val rotation = imageProxy.imageInfo.rotationDegrees
        return try {
            enhanceInternal(rawBitmap, rotation)
        } finally {
            if (!rawBitmap.isRecycled) {
                rawBitmap.recycle()
            }
        }
    }

    /**
     * Enhances an upright [Bitmap] and returns the enhanced [Bitmap] with inference time in ms.
     */
    @Synchronized
    fun enhance(bitmap: Bitmap): EnhancementResult? {
        if (interpreter == null) return null
        return enhanceInternal(bitmap, rotationDegrees = 0)
    }

    private fun enhanceInternal(source: Bitmap, rotationDegrees: Int): EnhancementResult? {
        val tflite = interpreter ?: return null

        drawRotatedScaledToInput(source, rotationDegrees)
        fillInputBufferFromBitmap()

        outputBuffer.rewind()
        val startMs = SystemClock.elapsedRealtime()
        tflite.run(inputBuffer, outputBuffer)
        val inferenceMs = SystemClock.elapsedRealtime() - startMs

        val resultBitmap = outputBufferToBitmap()
        return EnhancementResult(bitmap = resultBitmap, inferenceTimeMs = inferenceMs)
    }

    private fun drawRotatedScaledToInput(source: Bitmap, rotationDegrees: Int) {
        val matrix = Matrix()
        val srcW = source.width.toFloat()
        val srcH = source.height.toFloat()

        // Rotate around source center, then map rotated bounds into [0..inputWidth, 0..inputHeight]
        matrix.postTranslate(-srcW / 2f, -srcH / 2f)
        if (rotationDegrees != 0) {
            matrix.postRotate(rotationDegrees.toFloat())
        }
        val rotatedW = if (rotationDegrees == 90 || rotationDegrees == 270) srcH else srcW
        val rotatedH = if (rotationDegrees == 90 || rotationDegrees == 270) srcW else srcH
        matrix.postScale(inputWidth.toFloat() / rotatedW, inputHeight.toFloat() / rotatedH)
        matrix.postTranslate(inputWidth / 2f, inputHeight / 2f)

        val dstRect = RectF(0f, 0f, inputWidth.toFloat(), inputHeight.toFloat())
        if (dstRect.width() > 0f) {
            inputCanvas.drawBitmap(source, matrix, null)
        }
    }

    private fun fillInputBufferFromBitmap() {
        inputBuffer.rewind()
        inputBitmap.getPixels(inputPixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)
        val inv255 = 1f / 255f
        for (pixel in inputPixels) {
            val r = ((pixel ushr 16) and 0xFF) * inv255
            val g = ((pixel ushr 8) and 0xFF) * inv255
            val b = (pixel and 0xFF) * inv255
            inputBuffer.putFloat(r)
            inputBuffer.putFloat(g)
            inputBuffer.putFloat(b)
        }
        inputBuffer.rewind()
    }

    private fun outputBufferToBitmap(): Bitmap {
        outputBuffer.rewind()
        val count = outputPixels.size
        for (i in 0 until count) {
            val r = (outputBuffer.float.coerceIn(0f, 1f) * 255f).toInt()
            val g = (outputBuffer.float.coerceIn(0f, 1f) * 255f).toInt()
            val b = (outputBuffer.float.coerceIn(0f, 1f) * 255f).toInt()
            outputPixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        outputBitmap.setPixels(outputPixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)
        return outputBitmap
    }

    private fun loadModelFile(context: Context, assetName: String): MappedByteBuffer {
        val afd = context.assets.openFd(assetName)
        FileInputStream(afd.fileDescriptor).use { fis ->
            return fis.channel.map(
                FileChannel.MapMode.READ_ONLY,
                afd.startOffset,
                afd.declaredLength
            )
        }
    }

    @Synchronized
    fun close() {
        interpreter?.close()
        gpuDelegate?.close()
        gpuDelegate = null
        if (!inputBitmap.isRecycled) inputBitmap.recycle()
        if (!outputBitmap.isRecycled) outputBitmap.recycle()
    }
}
