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
import android.view.SurfaceHolder
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import kotlin.math.floor

class FlipWallpaperService : WallpaperService() {

    companion object {
        @Volatile var cachedBitmaps = mutableListOf<Bitmap>()
        @Volatile var cachedDepths = mutableListOf<Bitmap?>()
        @Volatile var cachedTimestamp = 0L
    }

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
        private val bgLoadExecutor = Executors.newSingleThreadExecutor()
        @Volatile private var isLoadingBackground = false

        private var isVisible = false
        private val bitmaps = mutableListOf<Bitmap>()
        private val depthCutouts = mutableListOf<Bitmap?>()
        private var fallbackBitmap: Bitmap? = null

        private var centerRoll: Float? = null
        private var targetProgress = 0f
        private var smoothProgress = 0f
        private var lastSensorTimeMs = 0L

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val depthPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
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

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            loadImagesFast()
            drawFrame() // Instant draw on creation
        }

        override fun onVisibilityChanged(visible: Boolean) {
            isVisible = visible
            if (visible) {
                centerRoll = null
                loadImagesFast()
                registerBestSensor()
                drawFrame() // Draw instantly before loop starts
                handler.removeCallbacks(drawRunnable)
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
            // Don't recycle static cache so next open is instant
            bitmaps.clear()
            depthCutouts.clear()
            fallbackBitmap?.recycle()
            fallbackBitmap = null
        }

        private fun ensureFallbackBitmap(w: Int, h: Int): Bitmap {
            val existing = fallbackBitmap
            if (existing != null && existing.width == w.coerceAtLeast(1) && existing.height == h.coerceAtLeast(1)) {
                return existing
            }
            existing?.recycle()
            val bw = w.coerceAtLeast(360)
            val bh = h.coerceAtLeast(640)
            val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)

            p.shader = LinearGradient(
                0f, 0f, bw.toFloat(), bh.toFloat(),
                intArrayOf(
                    Color.parseColor("#0B1026"),
                    Color.parseColor("#1D2B64"),
                    Color.parseColor("#1565C0"),
                    Color.parseColor("#0A1A2F")
                ),
                floatArrayOf(0f, 0.35f, 0.72f, 1f),
                Shader.TileMode.CLAMP
            )
            c.drawRect(0f, 0f, bw.toFloat(), bh.toFloat(), p)
            p.shader = null

            p.color = Color.argb(42, 255, 255, 255)
            c.drawCircle(bw * 0.75f, bh * 0.25f, bw * 0.42f, p)
            p.color = Color.argb(28, 41, 182, 246)
            c.drawCircle(bw * 0.22f, bh * 0.72f, bw * 0.48f, p)

            fallbackBitmap = bmp
            return bmp
        }

        private fun getWallpaperFiles(): List<File> {
            var files = filesDir.listFiles()
                ?.filter { it.name.startsWith("wp_") && it.name.endsWith(".jpg") }
                ?.sortedBy { it.name } ?: emptyList()

            if (files.isEmpty()) {
                val backup = File(filesDir, "cards_backup")
                files = backup.listFiles()
                    ?.filter { it.name.startsWith("card_") && it.name.endsWith(".jpg") }
                    ?.sortedBy { it.name } ?: emptyList()
            }
            return files
        }

        // ULTRA-FAST LOAD: ZERO BLACK SCREEN
        private fun loadImagesFast() {
            val files = getWallpaperFiles()
            if (files.isEmpty()) return

            val latestTime = files.maxOfOrNull { it.lastModified() } ?: 0L

            // 1. Agar pehle se memory cache me hai to 0 millisecond me use karo
            if (cachedBitmaps.isNotEmpty() && cachedTimestamp == latestTime && cachedBitmaps.size == files.size) {
                if (bitmaps.isEmpty() || bitmaps.size != cachedBitmaps.size) {
                    bitmaps.clear()
                    bitmaps.addAll(cachedBitmaps)
                    depthCutouts.clear()
                    depthCutouts.addAll(cachedDepths)
                }
                return
            }

            val rgb565Opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.RGB_565
            }

            // 2. Pehli photo turant decode karke screen par dikhao (NO BLACK SCREEN)
            if (bitmaps.isEmpty() || cachedTimestamp != latestTime) {
                bitmaps.clear()
                depthCutouts.clear()
                val bmp0 = BitmapFactory.decodeFile(files[0].absolutePath, rgb565Opts)
                if (bmp0 != null) {
                    bitmaps.add(bmp0)
                    val d0 = File(filesDir, "depth_0.png")
                    val dBmp0 = if (d0.exists()) BitmapFactory.decodeFile(d0.absolutePath) else null
                    depthCutouts.add(dBmp0)
                }
            }

            // 3. Baaki photos background thread me load hongi taaki screen freeze na ho
            if (files.size > 1 && !isLoadingBackground) {
                isLoadingBackground = true
                bgLoadExecutor.execute {
                    try {
                        val restBitmaps = mutableListOf<Bitmap>()
                        val restDepths = mutableListOf<Bitmap?>()

                        val firstBmp = bitmaps.firstOrNull() ?: BitmapFactory.decodeFile(files[0].absolutePath, rgb565Opts)
                        restBitmaps.add(firstBmp)
                        val d0 = File(filesDir, "depth_0.png")
                        val dBmp0 = if (d0.exists()) BitmapFactory.decodeFile(d0.absolutePath) else null
                        restDepths.add(dBmp0)

                        for (i in 1 until files.size) {
                            val bmp = BitmapFactory.decodeFile(files[i].absolutePath, rgb565Opts)
                            if (bmp != null) {
                                restBitmaps.add(bmp)
                                val dFile = File(filesDir, "depth_$i.png")
                                val dBmp = if (dFile.exists()) BitmapFactory.decodeFile(dFile.absolutePath) else null
                                restDepths.add(dBmp)
                            }
                        }

                        handler.post {
                            bitmaps.clear()
                            bitmaps.addAll(restBitmaps)
                            depthCutouts.clear()
                            depthCutouts.addAll(restDepths)

                            cachedBitmaps.clear()
                            cachedBitmaps.addAll(restBitmaps)
                            cachedDepths.clear()
                            cachedDepths.addAll(restDepths)
                            cachedTimestamp = latestTime

                            isLoadingBackground = false
                        }
                    } catch (_: Exception) {
                        isLoadingBackground = false
                    }
                }
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
                    rollSignal = event.values[0]
                    range = 3.5f
                }
                else -> {
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

                val prefs = getSharedPreferences("origin_prefs", Context.MODE_PRIVATE)
                val isPaused = prefs.getBoolean("wallpaper_paused", false)

                if (bitmaps.isEmpty() || isPaused) {
                    val fallback = ensureFallbackBitmap(w, h)
                    paint.alpha = 255
                    canvas.drawBitmap(fallback, null, fullScreenRect, paint)
                    if (!isPaused && prefs.getBoolean("clock_enabled", false)) {
                        val fallbackAdaptive = prefs.getInt("adaptive_clock_color", Color.WHITE)
                        ClockDrawer.drawClock(canvas, prefs, w.toFloat(), h.toFloat(), fallbackAdaptive)
                    }
                    return
                }

                val adaptiveColors = parseAdaptiveColors(prefs)
                val activeIndex: Int
                val nextIndex: Int
                val fraction: Float

                if (bitmaps.size == 1) {
                    activeIndex = 0
                    nextIndex = 0
                    fraction = 0f
                    paint.alpha = 255
                    canvas.drawBitmap(bitmaps[0], null, fullScreenRect, paint)
                } else {
                    smoothProgress += 0.20f * (targetProgress - smoothProgress)
                    val maxIndex = bitmaps.size - 1
                    val clamped = smoothProgress.coerceIn(0f, maxIndex.toFloat())
                    activeIndex = floor(clamped).toInt().coerceAtMost(maxIndex - 1)
                    nextIndex = activeIndex + 1
                    fraction = clamped - activeIndex

                    drawOriginSoftBlend(canvas, bitmaps[activeIndex], bitmaps[nextIndex], fraction, w, h)
                    drawLightSheen(canvas, fraction)
                }

                val colA = adaptiveColors.getOrElse(activeIndex) {
                    prefs.getInt("adaptive_clock_color", Color.WHITE)
                }
                val colB = adaptiveColors.getOrElse(nextIndex) { colA }
                val blendedAdaptiveColor = blendColors(colA, colB, fraction)

                if (prefs.getBoolean("clock_enabled", false)) {
                    ClockDrawer.drawClock(canvas, prefs, w.toFloat(), h.toFloat(), blendedAdaptiveColor)
                }

                if (prefs.getBoolean("depth_effect_enabled", false)) {
                    val fgA = depthCutouts.getOrNull(activeIndex)
                    val fgB = depthCutouts.getOrNull(nextIndex)
                    if (fgA != null) {
                        depthPaint.alpha = ((1f - fraction) * 255f).toInt().coerceIn(0, 255)
                        canvas.drawBitmap(fgA, null, fullScreenRect, depthPaint)
                    }
                    if (fgB != null && fraction > 0.01f) {
                        depthPaint.alpha = (fraction * 255f).toInt().coerceIn(0, 255)
                        canvas.drawBitmap(fgB, null, fullScreenRect, depthPaint)
                    }
                    depthPaint.alpha = 255
                }

            } finally {
                if (canvas != null) {
                    holder.unlockCanvasAndPost(canvas)
                }
            }
        }

        private fun parseAdaptiveColors(prefs: android.content.SharedPreferences): List<Int> {
            val raw = prefs.getString("adaptive_colors_csv", "") ?: ""
            if (raw.isBlank()) return emptyList()
            return raw.split(",").mapNotNull { it.trim().toIntOrNull() }
        }

        private fun blendColors(c1: Int, c2: Int, t: Float): Int {
            val inv = 1f - t
            val a = (Color.alpha(c1) * inv + Color.alpha(c2) * t).toInt().coerceIn(0, 255)
            val r = (Color.red(c1) * inv + Color.red(c2) * t).toInt().coerceIn(0, 255)
            val g = (Color.green(c1) * inv + Color.green(c2) * t).toInt().coerceIn(0, 255)
            val b = (Color.blue(c1) * inv + Color.blue(c2) * t).toInt().coerceIn(0, 255)
            return Color.argb(a, r, g, b)
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

    const val COLOR_ADAPTIVE = -2
    const val COLOR_CUSTOM_WHEEL = -3

    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun resolveTypeface(fontIndex: Int, weight: Float): Typeface {
        val wClamped = weight.coerceIn(0.25f, 1.0f)
        val isBold = wClamped >= 0.58f
        return when (fontIndex) {
            0 -> Typeface.create(if (isBold) "sans-serif-condensed" else "sans-serif", if (isBold) Typeface.BOLD else Typeface.NORMAL)
            1 -> Typeface.create(if (wClamped < 0.45f) "sans-serif-thin" else "sans-serif-light", Typeface.NORMAL)
            2 -> Typeface.create(if (wClamped >= 0.85f) "sans-serif-black" else "sans-serif", if (isBold) Typeface.BOLD else Typeface.NORMAL)
            3 -> Typeface.create(Typeface.SERIF, if (isBold) Typeface.BOLD else Typeface.NORMAL)
            4 -> Typeface.create("sans-serif-condensed", if (wClamped >= 0.7f) Typeface.BOLD else Typeface.NORMAL)
            else -> Typeface.create("sans-serif-medium", if (isBold) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    fun drawClock(
        canvas: Canvas,
        prefs: android.content.SharedPreferences,
        w: Float,
        h: Float,
        overrideAdaptiveColor: Int? = null
    ) {
        val mode = prefs.getInt("clock_mode", 0)
        val style = prefs.getInt("clock_style", 0).coerceIn(0, if (mode == 0) 3 else 2)
        val font = prefs.getInt("clock_font", 0).coerceIn(0, 5)
        val sizeScale = if (mode == 1) {
            prefs.getFloat("clock_custom_size", 1.35f).coerceIn(0.75f, 2.45f)
        } else {
            1.0f
        }
        val weight = prefs.getFloat("clock_weight", 0.72f).coerceIn(0.25f, 1.0f)
        val colorVal = prefs.getInt("clock_color", COLOR_ADAPTIVE)
        val adaptiveCol = overrideAdaptiveColor ?: prefs.getInt("adaptive_clock_color", Color.WHITE)
        val offsetX = if (mode == 1 && style >= 1) prefs.getFloat("clock_offset_x", 0f) else 0f
        val offsetY = if (mode == 1 && style >= 1) prefs.getFloat("clock_offset_y", 0f) else 0f

        val now = Calendar.getInstance().time
        val hhRaw = SimpleDateFormat("h", Locale.getDefault()).format(now)
        val hhPadded = hhRaw.padStart(2, '0')
        val mmPadded = SimpleDateFormat("mm", Locale.getDefault()).format(now).padStart(2, '0')
        val timeStr = "$hhRaw:$mmPadded"
        val dateStr = SimpleDateFormat("EEE, MMM dd", Locale.getDefault()).format(now)
        val shortDate = SimpleDateFormat("MM/dd", Locale.getDefault()).format(now)
        val dayStr = SimpleDateFormat("EEE", Locale.getDefault()).format(now)

        val baseColor = when (colorVal) {
            COLOR_ADAPTIVE -> adaptiveCol
            else -> colorVal
        }

        val glassAlpha = if (mode == 1) {
            (150 + (weight * 65)).toInt().coerceIn(145, 220)
        } else {
            (185 + (weight * 50)).toInt().coerceIn(180, 238)
        }
        val glassColor = Color.argb(
            glassAlpha,
            Color.red(baseColor),
            Color.green(baseColor),
            Color.blue(baseColor)
        )

        timePaint.reset()
        timePaint.isAntiAlias = true
        timePaint.color = glassColor
        timePaint.typeface = resolveTypeface(font, weight)
        timePaint.setShadowLayer(8f, 0f, 3f, Color.argb(75, 255, 255, 255))

        if (weight < 0.42f || font == 4) {
            timePaint.style = Paint.Style.STROKE
            timePaint.strokeWidth = (w * (0.003f + (1f - weight) * 0.011f)).coerceAtLeast(1.5f)
        } else {
            timePaint.style = Paint.Style.FILL
            timePaint.strokeWidth = 0f
        }

        datePaint.reset()
        datePaint.isAntiAlias = true
        datePaint.color = Color.argb(
            (glassAlpha * 0.92f).toInt().coerceIn(135, 225),
            Color.red(baseColor),
            Color.green(baseColor),
            Color.blue(baseColor)
        )
        datePaint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        datePaint.setShadowLayer(5f, 0f, 2f, Color.argb(65, 0, 0, 0))

        val cx = (w * (0.5f + offsetX)).coerceIn(w * 0.15f, w * 0.85f)
        val topY = (h * (0.155f + offsetY)).coerceIn(h * 0.08f, h * 0.65f)

        if (mode == 0) {
            datePaint.textSize = w * 0.042f
            val baseTimeSize = w * 0.185f
            timePaint.textSize = baseTimeSize
            timePaint.textScaleX = if (font == 0) 0.76f else 0.96f

            when (style) {
                0 -> {
                    datePaint.textAlign = Paint.Align.CENTER
                    timePaint.textAlign = Paint.Align.CENTER
                    canvas.drawText(dateStr, cx, topY, datePaint)
                    canvas.drawText(timeStr, cx, topY + baseTimeSize * 0.92f, timePaint)
                }
                1 -> {
                    datePaint.textAlign = Paint.Align.CENTER
                    timePaint.textAlign = Paint.Align.CENTER
                    canvas.drawText(dateStr, cx, topY, datePaint)
                    canvas.drawText(hhPadded, cx, topY + baseTimeSize * 0.86f, timePaint)
                    canvas.drawText(mmPadded, cx, topY + baseTimeSize * 1.56f, timePaint)
                }
                2 -> {
                    val leftX = w * 0.10f
                    datePaint.textAlign = Paint.Align.LEFT
                    timePaint.textAlign = Paint.Align.LEFT
                    canvas.drawText(dateStr, leftX, topY, datePaint)
                    canvas.drawText(timeStr, leftX, topY + baseTimeSize * 0.92f, timePaint)
                }
                else -> {
                    val smallTime = w * 0.135f
                    timePaint.textSize = smallTime
                    timePaint.textAlign = Paint.Align.LEFT
                    datePaint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(timeStr, w * 0.10f, topY + smallTime * 0.8f, timePaint)
                    canvas.drawText("$dayStr  $shortDate", w * 0.90f, topY + smallTime * 0.72f, datePaint)
                }
            }
        } else {
            datePaint.textSize = (w * 0.044f * (0.85f + sizeScale * 0.12f)).coerceAtMost(w * 0.075f)
            timePaint.textScaleX = if (font == 0 || font == 4) 0.60f else 0.74f

            when (style) {
                0 -> {
                    val bigSize = w * 0.30f * sizeScale
                    timePaint.textSize = bigSize
                    timePaint.textAlign = Paint.Align.CENTER
                    datePaint.textAlign = Paint.Align.CENTER
                    canvas.drawText(dateStr, cx, topY, datePaint)
                    canvas.drawText(timeStr, cx, topY + bigSize * 0.88f, timePaint)
                }
                1 -> {
                    val bigSize = w * 0.24f * sizeScale
                    timePaint.textSize = bigSize
                    timePaint.textAlign = Paint.Align.CENTER
                    datePaint.textAlign = Paint.Align.CENTER
                    canvas.drawText(dateStr, cx, topY, datePaint)
                    canvas.drawText(hhPadded, cx, topY + bigSize * 0.82f, timePaint)
                    canvas.drawText(mmPadded, cx, topY + bigSize * 1.50f, timePaint)
                }
                else -> {
                    val bigSize = w * 0.23f * sizeScale
                    timePaint.textSize = bigSize
                    timePaint.textAlign = Paint.Align.CENTER
                    datePaint.textAlign = Paint.Align.LEFT
                    canvas.drawText("$shortDate $dayStr", (cx - w * 0.22f).coerceAtLeast(w * 0.06f), topY, datePaint)
                    canvas.drawText(hhPadded[0].toString(), cx - w * 0.10f * sizeScale.coerceAtMost(1.5f), topY + bigSize * 0.84f, timePaint)
                    canvas.drawText(hhPadded[1].toString(), cx + w * 0.10f * sizeScale.coerceAtMost(1.5f), topY + bigSize * 0.70f, timePaint)
                    canvas.drawText(mmPadded[0].toString(), cx - w * 0.10f * sizeScale.coerceAtMost(1.5f), topY + bigSize * 1.52f, timePaint)
                    canvas.drawText(mmPadded[1].toString(), cx + w * 0.10f * sizeScale.coerceAtMost(1.5f), topY + bigSize * 1.38f, timePaint)
                }
            }
        }
    }
}