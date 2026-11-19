package com.origin.flip

import android.content.Context
import android.graphics.*
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.floor

class FlipWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = FlipEngine()

    inner class FlipEngine : Engine(), SensorEventListener {

        private val sensorManager by lazy {
            getSystemService(Context.SENSOR_SERVICE) as SensorManager
        }
        private val rotationSensor by lazy {
            sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
                ?: sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        }
        private val gravitySensor by lazy {
            sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        }
        private val accelSensor by lazy {
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        }

        private val handler = Handler(Looper.getMainLooper())
        private var isVisible = false
        private val bitmaps = mutableListOf<Bitmap>()

        private var centerRoll: Float? = null
        private var targetProgress = 0f
        private var smoothProgress = 0f
        private var lastSensorTimeMs = 0L

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val glossPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val srcRect = Rect()
        private val dstRect = RectF()
        private val fullScreenRect = RectF()

        private val rotationMatrix = FloatArray(9)
        private val orientationAngles = FloatArray(3)

        private val drawRunnable = object : Runnable {
            override fun run() {
                if (isVisible) {
                    drawFrame()
                    handler.postDelayed(this, 16)
                }
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            isVisible = visible
            if (visible) {
                centerRoll = null
                loadImages()
                registerBestSensor()
                handler.post(drawRunnable)
            } else {
                sensorManager.unregisterListener(this)
                handler.removeCallbacks(drawRunnable)
            }
        }

        private fun registerBestSensor() {
            val activeSensor = rotationSensor ?: gravitySensor ?: accelSensor
            activeSensor?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
        }

        override fun onOffsetsChanged(
            xOffset: Float,
            yOffset: Float,
            xOffsetStep: Float,
            yOffsetStep: Float,
            xPixelOffset: Int,
            yPixelOffset: Int
        ) {
            super.onOffsetsChanged(xOffset, yOffset, xOffsetStep, yOffsetStep, xPixelOffset, yPixelOffset)
            if (bitmaps.size < 2) return
            // If sensor hasn't fired recently (e.g., restricted/missing sensor), allow home screen swipe offset to drive transition
            val now = System.currentTimeMillis()
            if (now - lastSensorTimeMs > 800L && xOffset in 0f..1f) {
                val maxIdx = (bitmaps.size - 1).toFloat()
                targetProgress = (xOffset * maxIdx).coerceIn(0f, maxIdx)
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            sensorManager.unregisterListener(this)
            handler.removeCallbacks(drawRunnable)
            bitmaps.forEach { it.recycle() }
            bitmaps.clear()
        }

        private fun loadImages() {
            bitmaps.forEach { it.recycle() }
            bitmaps.clear()

            val files = filesDir.listFiles()
                ?.filter { it.name.startsWith("wp_") && it.name.endsWith(".jpg") }
                ?.sortedBy { it.name } ?: emptyList()

            for (file in files) {
                BitmapFactory.decodeFile(file.absolutePath)?.let { bitmaps.add(it) }
            }
        }

        override fun onSensorChanged(event: SensorEvent) {
            if (bitmaps.size < 2) return
            lastSensorTimeMs = System.currentTimeMillis()

            val rollSignal: Float
            val range: Float

            when (event.sensor.type) {
                Sensor.TYPE_GAME_ROTATION_VECTOR,
                Sensor.TYPE_ROTATION_VECTOR -> {
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    SensorManager.getOrientation(rotationMatrix, orientationAngles)
                    rollSignal = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()
                    range = 14f
                }
                Sensor.TYPE_GRAVITY -> {
                    // X-axis gravity gives smooth left/right tilt even without gyroscope
                    rollSignal = event.values[0]
                    range = 3.5f
                }
                else -> {
                    // TYPE_ACCELEROMETER fallback for phones like Redmi 10 without gyroscope
                    rollSignal = event.values[0]
                    range = 3.5f
                }
            }

            if (centerRoll == null) {
                centerRoll = rollSignal
            }

            val diff = rollSignal - (centerRoll ?: rollSignal)
            if (diff > range + 6f) centerRoll = rollSignal - range
            if (diff < -range - 6f) centerRoll = rollSignal + range

            val normalized = ((rollSignal - (centerRoll ?: 0f)) + range) / (2f * range)
            val maxIdx = (bitmaps.size - 1).toFloat()
            targetProgress = (normalized.coerceIn(0f, 1f)) * maxIdx
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

        private fun drawFrame() {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas() ?: return
                val w = canvas.width
                val h = canvas.height
                fullScreenRect.set(0f, 0f, w.toFloat(), h.toFloat())

                if (bitmaps.isEmpty()) {
                    canvas.drawColor(Color.BLACK)
                    return
                }
                if (bitmaps.size == 1) {
                    paint.alpha = 255
                    canvas.drawBitmap(bitmaps[0], null, fullScreenRect, paint)
                } else {
                    smoothProgress += 0.20f * (targetProgress - smoothProgress)

                    val maxIndex = bitmaps.size - 1
                    val clamped = smoothProgress.coerceIn(0f, maxIndex.toFloat())
                    val index = floor(clamped).toInt().coerceAtMost(maxIndex - 1)
                    val fraction = clamped - index

                    val bmpA = bitmaps[index]
                    val bmpB = bitmaps[index + 1]

                    drawOriginSoftBlend(canvas, bmpA, bmpB, fraction, w, h)
                    drawLightSheen(canvas, fraction)
                }

                val prefs = getSharedPreferences("origin_prefs", Context.MODE_PRIVATE)
                if (prefs.getBoolean("clock_enabled", false)) {
                    ClockDrawer.drawClock(canvas, prefs, w.toFloat(), h.toFloat())
                }

            } finally {
                if (canvas != null) {
                    holder.unlockCanvasAndPost(canvas)
                }
            }
        }

        private fun drawOriginSoftBlend(
            canvas: Canvas, bmpA: Bitmap, bmpB: Bitmap, t: Float, w: Int, h: Int
        ) {
            paint.alpha = 255
            canvas.drawBitmap(bmpA, null, fullScreenRect, paint)

            if (t <= 0.005f) return
            if (t >= 0.995f) {
                canvas.drawBitmap(bmpB, null, fullScreenRect, paint)
                return
            }

            val slices = 48
            val dstSliceW = w.toFloat() / slices
            val srcSliceW = bmpB.width.toFloat() / slices
            val feather = 0.48f
            val sweep = t * (1f + feather)

            for (i in 0 until slices) {
                val nx = i.toFloat() / (slices - 1)
                val alphaRatio = ((sweep - (1f - nx)) / feather).coerceIn(0f, 1f)

                if (alphaRatio > 0.01f) {
                    val srcL = (i * srcSliceW).toInt()
                    val srcR = ((i + 1) * srcSliceW + 1).toInt().coerceAtMost(bmpB.width)
                    val dstL = i * dstSliceW
                    val dstR = ((i + 1) * dstSliceW + 1f).coerceAtMost(w.toFloat())

                    srcRect.set(srcL, 0, srcR, bmpB.height)
                    dstRect.set(dstL, 0f, dstR, h.toFloat())

                    val smoothAlpha = alphaRatio * alphaRatio * (3f - 2f * alphaRatio)
                    paint.alpha = (smoothAlpha * 255f).toInt().coerceIn(0, 255)
                    canvas.drawBitmap(bmpB, srcRect, dstRect, paint)
                }
            }
            paint.alpha = 255
        }

        private fun drawLightSheen(canvas: Canvas, t: Float) {
            val w = canvas.width.toFloat()
            val h = canvas.height.toFloat()
            val sheenX = w * (1.3f - t * 1.6f)

            val shader = LinearGradient(
                sheenX - w * 0.25f, 0f,
                sheenX + w * 0.25f, h * 0.4f,
                intArrayOf(
                    Color.TRANSPARENT,
                    Color.argb(30, 255, 255, 255),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            glossPaint.shader = shader
            canvas.drawRect(0f, 0f, w, h, glossPaint)
        }
    }
}

object ClockDrawer {

    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun drawClock(
        canvas: Canvas,
        prefs: android.content.SharedPreferences,
        w: Float,
        h: Float
    ) {
        val mode = prefs.getInt("clock_mode", 0)
        val style = prefs.getInt("clock_style", 0)
        val font = prefs.getInt("clock_font", 0)
        val sizeScale = prefs.getFloat("clock_size", 1.0f)
        val colorVal = prefs.getInt("clock_color", Color.parseColor("#5C76B8"))

        val now = Calendar.getInstance().time
        val hh = SimpleDateFormat("h", Locale.getDefault()).format(now)
        val mm = SimpleDateFormat("mm", Locale.getDefault()).format(now)
        val timeStr = "$hh:$mm"
        val dateStr = SimpleDateFormat("EEE, MMM dd", Locale.getDefault()).format(now)
        val shortDate = SimpleDateFormat("MM/dd", Locale.getDefault()).format(now)
        val dayStr = SimpleDateFormat("EEE", Locale.getDefault()).format(now)

        timePaint.reset()
        timePaint.isAntiAlias = true
        timePaint.setShadowLayer(6f, 0f, 2f, Color.argb(85, 0, 0, 0))

        datePaint.reset()
        datePaint.isAntiAlias = true
        datePaint.setShadowLayer(5f, 0f, 2f, Color.argb(85, 0, 0, 0))

        if (mode == 0) {
            timePaint.textScaleX = if (font == 0) 0.72f else 1.0f
            when (font) {
                0 -> timePaint.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
                1 -> timePaint.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                2 -> timePaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                3 -> timePaint.typeface = Typeface.SERIF
                4 -> {
                    timePaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                    timePaint.style = Paint.Style.STROKE
                    timePaint.strokeWidth = w * 0.008f
                }
            }
        } else {
            timePaint.textScaleX = 0.62f
            when (font) {
                0 -> timePaint.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
                else -> timePaint.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            }
        }
        datePaint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

        if (colorVal == -1) {
            val grad = LinearGradient(
                w * 0.15f, h * 0.1f, w * 0.85f, h * 0.35f,
                intArrayOf(
                    Color.parseColor("#FF6B6B"),
                    Color.parseColor("#FFD93D"),
                    Color.parseColor("#6BCB77"),
                    Color.parseColor("#4D96FF"),
                    Color.parseColor("#C780FA")
                ),
                null,
                Shader.TileMode.CLAMP
            )
            timePaint.shader = grad
            datePaint.color = Color.WHITE
        } else {
            timePaint.shader = null
            timePaint.color = colorVal
            datePaint.color = colorVal
        }

        val cx = w * 0.5f
        val topY = h * 0.165f

        if (mode == 0) {
            datePaint.textSize = w * 0.042f
            val baseTimeSize = w * 0.185f * sizeScale
            timePaint.textSize = baseTimeSize

            when (style) {
                0 -> {
                    datePaint.textAlign = Paint.Align.CENTER
                    timePaint.textAlign = Paint.Align.CENTER
                    canvas.drawText(dateStr, cx, topY, datePaint)
                    canvas.drawText(timeStr, cx, topY + baseTimeSize * 0.94f, timePaint)
                }
                1 -> {
                    datePaint.textAlign = Paint.Align.CENTER
                    timePaint.textAlign = Paint.Align.CENTER
                    canvas.drawText(dateStr, cx, topY, datePaint)
                    canvas.drawText(hh, cx, topY + baseTimeSize * 0.92f, timePaint)
                    canvas.drawText(mm, cx, topY + baseTimeSize * 1.78f, timePaint)
                }
                2 -> {
                    val leftX = w * 0.10f
                    datePaint.textAlign = Paint.Align.LEFT
                    timePaint.textAlign = Paint.Align.LEFT
                    canvas.drawText(dateStr, leftX, topY, datePaint)
                    canvas.drawText(timeStr, leftX, topY + baseTimeSize * 0.94f, timePaint)
                }
                else -> {
                    val smallTime = w * 0.135f * sizeScale
                    timePaint.textSize = smallTime
                    timePaint.textAlign = Paint.Align.LEFT
                    datePaint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(timeStr, w * 0.10f, topY + smallTime * 0.8f, timePaint)
                    canvas.drawText("$dayStr  $shortDate", w * 0.90f, topY + smallTime * 0.72f, datePaint)
                }
            }
        } else {
            datePaint.textSize = w * 0.045f
            datePaint.textAlign = Paint.Align.CENTER

            when (style) {
                0 -> {
                    val bigSize = w * 0.34f
                    timePaint.textSize = bigSize
                    timePaint.textAlign = Paint.Align.CENTER
                    canvas.drawText(dateStr, cx, topY, datePaint)
                    canvas.drawText(timeStr, cx, topY + bigSize * 0.92f, timePaint)
                }
                1 -> {
                    val bigSize = w * 0.28f
                    timePaint.textSize = bigSize
                    timePaint.textAlign = Paint.Align.CENTER
                    canvas.drawText(dateStr, cx, topY, datePaint)
                    canvas.drawText(hh, cx, topY + bigSize * 0.90f, timePaint)
                    canvas.drawText(mm, cx, topY + bigSize * 1.72f, timePaint)
                }
                else -> {
                    val bigSize = w * 0.27f
                    timePaint.textSize = bigSize
                    timePaint.textAlign = Paint.Align.CENTER
                    datePaint.textAlign = Paint.Align.LEFT
                    canvas.drawText("$shortDate $dayStr", w * 0.28f, topY, datePaint)
                    canvas.drawText(hh[0].toString(), cx - w * 0.11f, topY + bigSize * 0.95f, timePaint)
                    canvas.drawText(hh[1].toString(), cx + w * 0.11f, topY + bigSize * 0.78f, timePaint)
                    canvas.drawText(mm[0].toString(), cx - w * 0.11f, topY + bigSize * 1.78f, timePaint)
                    canvas.drawText(mm[1].toString(), cx + w * 0.11f, topY + bigSize * 1.60f, timePaint)
                }
            }
        }
    }
}