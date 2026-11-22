package com.origin.flip

import android.app.Dialog
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {

    private lateinit var footerStatus: TextView
    private lateinit var cardsRow: LinearLayout

    private lateinit var applyTogglePill: FrameLayout
    private lateinit var applyToggleText: TextView
    private lateinit var applyToggleKnob: View

    private lateinit var togglePill: FrameLayout
    private lateinit var toggleText: TextView
    private lateinit var toggleKnob: View
    private lateinit var customizeSubtitle: TextView

    private val bgExecutor = Executors.newSingleThreadExecutor()
    @Volatile private var isPrecomputing = false

    private val pickImages = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@registerForActivityResult
        appendSelectedImages(uris)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun dpF(v: Float): Float = v * resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        val prefs = getSharedPreferences("origin_prefs", Context.MODE_PRIVATE)

        val root = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.parseColor("#0B1026"),
                    Color.parseColor("#181338"),
                    Color.parseColor("#0A1A2F")
                )
            )
        }

        val glowLayer = object : View(this) {
            private val p1 = Paint(Paint.ANTI_ALIAS_FLAG)
            private val p2 = Paint(Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val w = width.toFloat()
                val h = height.toFloat()
                p1.shader = RadialGradient(
                    w * 0.25f, h * 0.42f, w * 0.55f,
                    Color.argb(95, 79, 134, 247), Color.TRANSPARENT, Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, w, h, p1)

                p2.shader = RadialGradient(
                    w * 0.78f, h * 0.52f, w * 0.5f,
                    Color.argb(75, 138, 79, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, w, h, p2)
            }
        }
        root.addView(glowLayer)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(58), dp(22), dp(28))
        }

        val badge = TextView(this).apply {
            text = "ORIGIN OS 6 ENGINE"
            textSize = 12f
            letterSpacing = 0.12f
            setTextColor(Color.WHITE)
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            gravity = Gravity.CENTER
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#00B0FF"), Color.parseColor("#1E88E5"))
            ).apply {
                cornerRadius = dp(24).toFloat()
            }
            setPadding(dp(22), dp(9), dp(22), dp(9))
        }

        val title = TextView(this).apply {
            text = "Flip Cards Wallpaper"
            textSize = 30f
            setTextColor(Color.WHITE)
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(6))
        }

        val subtitle = TextView(this).apply {
            text = "Dynamic flip-effect wallpapers • Curated & ready"
            textSize = 14f
            setTextColor(Color.parseColor("#C5CAE9"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(24))
        }

        val hScrollCards = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(192)
            ).apply { setMargins(0, 0, 0, dp(28)) }
        }

        cardsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
        hScrollCards.addView(cardsRow)

        val pillW = dp(78)
        val pillH = dp(38)
        val knobSize = dp(30)

        // 1. APPLY ORIGIN CARD (CARD AND SWITCH WORK SEPARATELY)
        val btnApplyCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#29B6F6"), Color.parseColor("#1976D2"))
            ).apply {
                cornerRadius = dp(24).toFloat()
            }
            setPadding(dp(20), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(74)
            ).apply { setMargins(0, 0, 0, dp(16)) }
            // Card background click opens chooser superfast
            setOnClickListener {
                launchLiveWallpaperChooser()
            }
        }

        val applyTextCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            gravity = Gravity.CENTER_VERTICAL
            // Text click opens chooser superfast
            setOnClickListener {
                launchLiveWallpaperChooser()
            }
        }

        val applyTitle = TextView(this).apply {
            text = "Apply Origin Flip Wallpaper"
            textSize = 16.5f
            setTextColor(Color.WHITE)
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
        }

        applyTextCol.addView(applyTitle)

        // ON/OFF PILL SWITCH (ONLY TOGGLES ON/OFF, DOES NOT OPEN CHOOSER)
        applyTogglePill = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(pillW, pillH)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            isClickable = true
            isFocusable = true
        }

        applyToggleText = TextView(this).apply {
            textSize = 12f
            setTypeface(Typeface.create("sans-serif-bold", Typeface.BOLD))
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        applyToggleKnob = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
            }
            elevation = dp(3).toFloat()
            layoutParams = FrameLayout.LayoutParams(knobSize, knobSize, Gravity.CENTER_VERTICAL)
        }

        applyTogglePill.addView(applyToggleText)
        applyTogglePill.addView(applyToggleKnob)

        // SUPERFAST INDEPENDENT TOGGLE
        applyTogglePill.setOnClickListener {
            val isCurrentlyOn = prefs.getBoolean("flip_toggle_active", false)
            if (isCurrentlyOn) {
                prefs.edit()
                    .putBoolean("flip_toggle_active", false)
                    .putBoolean("wallpaper_paused", true)
                    .apply()
                updateApplyToggleUI(false)
                updateFooterText()
            } else {
                val cardPhotos = getCardPhotos()
                if (cardPhotos.size < 2) {
                    Toast.makeText(this@MainActivity, "select minimum two photo", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                prefs.edit()
                    .putBoolean("flip_toggle_active", true)
                    .putBoolean("wallpaper_paused", false)
                    .apply()
                updateApplyToggleUI(true)
                updateFooterText()
            }
        }

        btnApplyCard.addView(applyTextCol)
        btnApplyCard.addView(applyTogglePill)

        // 2. CUSTOMIZATION CARD
        val btnCustomizeCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(24).toFloat()
            }
            setPadding(dp(22), dp(14), dp(18), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(74)
            ).apply { setMargins(0, 0, 0, dp(22)) }
            setOnClickListener {
                openCustomizationDialog()
            }
        }

        val customizeTextCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val customizeTitle = TextView(this).apply {
            text = "CUSTOMIZATION"
            textSize = 17f
            setTextColor(Color.parseColor("#0D1117"))
            setTypeface(Typeface.create("sans-serif-bold", Typeface.BOLD))
            letterSpacing = 0.04f
        }

        customizeSubtitle = TextView(this).apply {
            text = "Clock style, weight, depth & color"
            textSize = 12.5f
            setTextColor(Color.parseColor("#5F6368"))
            setPadding(0, dp(2), 0, 0)
        }

        customizeTextCol.addView(customizeTitle)
        customizeTextCol.addView(customizeSubtitle)

        togglePill = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(pillW, pillH)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }

        toggleText = TextView(this).apply {
            textSize = 12f
            setTypeface(Typeface.create("sans-serif-bold", Typeface.BOLD))
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        toggleKnob = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
            }
            elevation = dp(3).toFloat()
            layoutParams = FrameLayout.LayoutParams(knobSize, knobSize, Gravity.CENTER_VERTICAL)
        }

        togglePill.addView(toggleText)
        togglePill.addView(toggleKnob)

        togglePill.setOnClickListener {
            val currentlyEnabled = prefs.getBoolean("clock_enabled", false)
            val newState = !currentlyEnabled
            prefs.edit().putBoolean("clock_enabled", newState).apply()
            updateToggleUI(newState)
        }

        btnCustomizeCard.addView(customizeTextCol)
        btnCustomizeCard.addView(togglePill)

        updateApplyToggleUI(prefs.getBoolean("flip_toggle_active", false))
        updateToggleUI(prefs.getBoolean("clock_enabled", false))

        footerStatus = TextView(this).apply {
            text = "Active  •  OriginOS Seamless Flip  •  Ready to apply"
            textSize = 13f
            setTextColor(Color.parseColor("#4FC3F7"))
            gravity = Gravity.CENTER
        }

        refreshCardsStrip()

        // Clean UI - No update buttons
        content.addView(badge)
        content.addView(title)
        content.addView(subtitle)
        content.addView(hScrollCards)
        content.addView(btnApplyCard)
        content.addView(btnCustomizeCard)
        content.addView(footerStatus)

        root.addView(content)
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("origin_prefs", Context.MODE_PRIVATE)
        if (::togglePill.isInitialized) {
            updateToggleUI(prefs.getBoolean("clock_enabled", false))
        }
        if (::applyTogglePill.isInitialized) {
            updateApplyToggleUI(prefs.getBoolean("flip_toggle_active", false))
        }
        updateFooterText()
    }

    // DIRECT & SUPERFAST - OPENS SYSTEM LIVE WALLPAPER SCREEN INSTANTLY
    private fun launchLiveWallpaperChooser() {
        val cardPhotos = getCardPhotos()
        if (cardPhotos.size < 2) {
            Toast.makeText(this@MainActivity, "select minimum two photo", Toast.LENGTH_SHORT).show()
            return
        }

        val prefs = getSharedPreferences("origin_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("flip_toggle_active", true)
            .putBoolean("wallpaper_paused", false)
            .apply()
        updateApplyToggleUI(true)
        updateFooterText()

        try {
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(this@MainActivity, FlipWallpaperService::class.java)
                )
            }
            startActivity(intent)
        } catch (_: Throwable) {
            try {
                startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
            } catch (_: Throwable) {
                startActivity(Intent(Intent.ACTION_SET_WALLPAPER))
            }
        }
    }

    private fun updateApplyToggleUI(isActive: Boolean) {
        val knobParams = applyToggleKnob.layoutParams as FrameLayout.LayoutParams
        if (isActive) {
            applyTogglePill.background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#16A34A"), Color.parseColor("#22C55E"))
            ).apply {
                cornerRadius = dp(20).toFloat()
            }
            applyToggleText.text = "ON"
            applyToggleText.setTextColor(Color.WHITE)
            applyToggleText.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            applyToggleText.setPadding(dp(10), 0, 0, 0)
            knobParams.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        } else {
            applyTogglePill.background = GradientDrawable().apply {
                setColor(Color.parseColor("#DCE3EC"))
                cornerRadius = dp(20).toFloat()
            }
            applyToggleText.text = "OFF"
            applyToggleText.setTextColor(Color.parseColor("#475569"))
            applyToggleText.gravity = Gravity.END or Gravity.CENTER_VERTICAL
            applyToggleText.setPadding(0, 0, dp(9), 0)
            knobParams.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }
        applyToggleKnob.layoutParams = knobParams
    }

    private fun updateToggleUI(isEnabled: Boolean) {
        val knobParams = toggleKnob.layoutParams as FrameLayout.LayoutParams
        if (isEnabled) {
            togglePill.background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#00B0FF"), Color.parseColor("#2979FF"))
            ).apply {
                cornerRadius = dp(20).toFloat()
            }
            toggleText.text = "ON"
            toggleText.setTextColor(Color.WHITE)
            toggleText.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            toggleText.setPadding(dp(10), 0, 0, 0)
            knobParams.gravity = Gravity.END or Gravity.CENTER_VERTICAL
            customizeSubtitle.text = "Clock enabled • Tap to edit"
        } else {
            togglePill.background = GradientDrawable().apply {
                setColor(Color.parseColor("#D5DAE2"))
                cornerRadius = dp(20).toFloat()
            }
            toggleText.text = "OFF"
            toggleText.setTextColor(Color.parseColor("#5F6368"))
            toggleText.gravity = Gravity.END or Gravity.CENTER_VERTICAL
            toggleText.setPadding(0, 0, dp(9), 0)
            knobParams.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            customizeSubtitle.text = "Clock disabled • Tap to customize"
        }
        toggleKnob.layoutParams = knobParams
    }

    private fun updateFooterText() {
        if (!::footerStatus.isInitialized) return
        val active = getSharedPreferences("origin_prefs", Context.MODE_PRIVATE)
            .getBoolean("flip_toggle_active", false)
        val count = getCardPhotos().size
        footerStatus.text = when {
            !active -> "OFF  •  System Wallpaper Mode"
            count < 2 -> "Select minimum two photo"
            else -> "Active  •  $count Photos Selected"
        }
    }

    private fun getCardPhotos(): List<File> {
        val files = filesDir.listFiles()
            ?.filter { it.name.startsWith("wp_") && it.name.endsWith(".jpg") }
            ?.sortedBy { it.name } ?: emptyList()
        if (files.isNotEmpty()) return files

        val backupDir = File(filesDir, "cards_backup")
        val backupFiles = backupDir.listFiles()
            ?.filter { it.name.startsWith("card_") && it.name.endsWith(".jpg") }
            ?.sortedBy { it.name } ?: emptyList()

        if (backupFiles.isNotEmpty()) {
            backupFiles.forEachIndexed { idx, file ->
                val dest = File(filesDir, "wp_$idx.jpg")
                file.copyTo(dest, overwrite = true)
            }
            return filesDir.listFiles()
                ?.filter { it.name.startsWith("wp_") && it.name.endsWith(".jpg") }
                ?.sortedBy { it.name } ?: emptyList()
        }
        return emptyList()
    }

    private fun reindexCardPhotos() {
        val currentFiles = getCardPhotos()
        currentFiles.forEachIndexed { idx, file ->
            val temp = File(filesDir, "tmp_wp_$idx.jpg")
            file.renameTo(temp)
            val depthFile = File(filesDir, "depth_$idx.png")
            if (depthFile.exists()) {
                depthFile.renameTo(File(filesDir, "tmp_depth_$idx.png"))
            }
        }
        filesDir.listFiles()
            ?.filter { it.name.startsWith("tmp_wp_") && it.name.endsWith(".jpg") }
            ?.sortedBy { it.name }
            ?.forEachIndexed { idx, tempFile ->
                tempFile.renameTo(File(filesDir, "wp_$idx.jpg"))
            }
        filesDir.listFiles()
            ?.filter { it.name.startsWith("tmp_depth_") && it.name.endsWith(".png") }
            ?.sortedBy { it.name }
            ?.forEachIndexed { idx, tempDepth ->
                tempDepth.renameTo(File(filesDir, "depth_$idx.png"))
            }
        bgExecutor.execute {
            precomputeWallpaperCaches()
        }
    }

    private fun removePhotoAt(index: Int) {
        val files = getCardPhotos()
        if (index in files.indices) {
            files[index].delete()
            File(filesDir, "depth_$index.png").delete()
            reindexCardPhotos()
            refreshCardsStrip()
        }
    }

    private fun isLightColor(color: Int): Boolean {
        val lum = (0.299f * Color.red(color) + 0.587f * Color.green(color) + 0.114f * Color.blue(color)) / 255f
        return lum > 0.55f
    }

    private fun extractAdaptiveColorFromBitmap(bmp: Bitmap?): Int {
        if (bmp == null) return Color.parseColor("#DCEBFF")
        val small = Bitmap.createScaledBitmap(bmp, 40, 72, true)
        val w = small.width
        val h = small.height
        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)
        small.recycle()

        val hsv = FloatArray(3)
        var bestScore = -1f
        var bestColor = Color.WHITE
        var rSum = 0L
        var gSum = 0L
        var bSum = 0L
        val count = w * h

        for (i in 0 until count) {
            val px = pixels[i]
            val r = (px shr 16) and 0xFF
            val g = (px shr 8) and 0xFF
            val b = px and 0xFF
            rSum += r
            gSum += g
            bSum += b

            Color.colorToHSV(px, hsv)
            val sat = hsv[1]
            val valB = hsv[2]
            if (valB in 0.30f..0.98f && sat > 0.22f) {
                val score = sat * 1.45f + (1f - abs(valB - 0.75f))
                if (score > bestScore) {
                    bestScore = score
                    bestColor = px
                }
            }
        }

        if (bestScore < 0f && count > 0) {
            val avgR = (rSum / count).toInt()
            val avgG = (gSum / count).toInt()
            val avgB = (bSum / count).toInt()
            val lum = (0.299 * avgR + 0.587 * avgG + 0.114 * avgB) / 255.0
            return if (lum > 0.62) Color.parseColor("#1A2232") else Color.parseColor("#F5F8FF")
        }

        Color.colorToHSV(bestColor, hsv)
        hsv[1] = (hsv[1] * 0.72f).coerceIn(0.18f, 0.85f)
        hsv[2] = hsv[2].coerceIn(0.78f, 0.99f)
        return Color.HSVToColor(hsv)
    }

    private fun buildSubjectCutoutBitmap(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val sw = (w / 4).coerceAtLeast(64)
        val sh = (h / 4).coerceAtLeast(112)
        val smallSrc = Bitmap.createScaledBitmap(src, sw, sh, true)

        val pixels = IntArray(sw * sh)
        smallSrc.getPixels(pixels, 0, sw, 0, 0, sw, sh)
        smallSrc.recycle()

        val maskPixels = IntArray(sw * sh)
        val cx = sw * 0.5f
        val cy = sh * 0.48f
        val rxInv = 1f / (sw * 0.34f)
        val ryInv = 1f / (sh * 0.38f)

        for (y in 0 until sh) {
            val dy = (y - cy) * ryInv
            val dy2 = dy * dy
            val yOffset = y * sw
            for (x in 0 until sw) {
                val dx = (x - cx) * rxInv
                val dist = sqrt(dx * dx + dy2)
                val px = pixels[yOffset + x]
                val r = (px shr 16) and 0xFF
                val g = (px shr 8) and 0xFF
                val b = px and 0xFF
                val lum = (0.299f * r + 0.587f * g + 0.114f * b) * (1f / 255f)
                val detailBoost = if (lum in 0.16f..0.88f) 0.15f else 0f
                val alphaVal = ((1.22f - dist + detailBoost) * 255f).toInt().coerceIn(0, 255)
                maskPixels[yOffset + x] = (alphaVal shl 24)
            }
        }

        val maskSmall = Bitmap.createBitmap(maskPixels, sw, sh, Bitmap.Config.ARGB_8888)
        val fullMask = Bitmap.createScaledBitmap(maskSmall, w, h, true)
        maskSmall.recycle()

        val cutout = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(cutout)
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        c.drawBitmap(src, 0f, 0f, null)
        c.drawBitmap(fullMask, 0f, 0f, maskPaint)
        fullMask.recycle()

        return cutout
    }

    private fun precomputeWallpaperCaches() {
        if (isPrecomputing) return
        isPrecomputing = true
        bgExecutor.execute {
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                val prefs = getSharedPreferences("origin_prefs", Context.MODE_PRIVATE)
                val files = filesDir.listFiles()
                    ?.filter { it.name.startsWith("wp_") && it.name.endsWith(".jpg") }
                    ?.sortedBy { it.name } ?: emptyList()
                val colorList = mutableListOf<Int>()

                files.forEachIndexed { idx, file ->
                    val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                    val bmp = BitmapFactory.decodeFile(file.absolutePath, opts)
                    if (bmp != null) {
                        colorList.add(extractAdaptiveColorFromBitmap(bmp))
                        val depthFile = File(filesDir, "depth_$idx.png")
                        if (!depthFile.exists()) {
                            val cutout = buildSubjectCutoutBitmap(bmp)
                            FileOutputStream(depthFile).use { out ->
                                cutout.compress(Bitmap.CompressFormat.PNG, 80, out)
                            }
                            cutout.recycle()
                        }
                        bmp.recycle()
                    }
                }
                prefs.edit().putString("adaptive_colors_csv", colorList.joinToString(",")).apply()
            } catch (_: Exception) {
            } finally {
                isPrecomputing = false
            }
        }
    }

    private fun refreshCardsStrip() {
        cardsRow.removeAllViews()
        val cardW = dp(104)
        val cardH = dp(192)
        val gap = dp(12)
        updateFooterText()

        bgExecutor.execute {
            val savedFiles = getCardPhotos()
            val thumbOpts = BitmapFactory.Options().apply { inSampleSize = 4 }

            val loadedCards = if (savedFiles.isNotEmpty()) {
                savedFiles.mapIndexedNotNull { index, file ->
                    BitmapFactory.decodeFile(file.absolutePath, thumbOpts)?.let { Pair(it, index) }
                }
            } else emptyList()

            val defaultCards = if (loadedCards.isEmpty()) {
                (0 until 3).map { makeDefaultCardBitmap(it) }
            } else emptyList()

            runOnUiThread {
                cardsRow.removeAllViews()
                if (loadedCards.isNotEmpty()) {
                    loadedCards.forEach { (bmp, index) ->
                        cardsRow.addView(buildPhotoCard(bmp, cardW, cardH, gap, showClose = true) {
                            removePhotoAt(index)
                        })
                    }
                } else {
                    defaultCards.forEach { bmp ->
                        cardsRow.addView(buildPhotoCard(bmp, cardW, cardH, gap, showClose = true) {
                            pickImages.launch(arrayOf("image/*"))
                        })
                    }
                }

                val addCard = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(cardW, cardH)
                    background = GradientDrawable().apply {
                        setColor(Color.argb(48, 120, 144, 180))
                        cornerRadius = dp(20).toFloat()
                        setStroke(dp(2), Color.argb(145, 210, 222, 245))
                    }

                    val plusCircle = TextView(this@MainActivity).apply {
                        text = "+"
                        textSize = 24f
                        setTextColor(Color.WHITE)
                        gravity = Gravity.CENTER
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(Color.argb(120, 255, 255, 255))
                        }
                        layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                            setMargins(0, 0, 0, dp(8))
                        }
                    }

                    val addLabel = TextView(this@MainActivity).apply {
                        text = "Add"
                        textSize = 13f
                        setTextColor(Color.WHITE)
                        setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL))
                        gravity = Gravity.CENTER
                    }

                    addView(plusCircle)
                    addView(addLabel)

                    setOnClickListener {
                        pickImages.launch(arrayOf("image/*"))
                    }
                }

                cardsRow.addView(addCard)
                updateFooterText()
            }
        }
    }

    private fun buildPhotoCard(
        bitmap: Bitmap,
        cardW: Int,
        cardH: Int,
        rightMargin: Int,
        showClose: Boolean,
        onRemoveClick: () -> Unit
    ): FrameLayout {
        return FrameLayout(this@MainActivity).apply {
            layoutParams = LinearLayout.LayoutParams(cardW, cardH).apply {
                setMargins(0, 0, rightMargin, 0)
            }

            val imgView = ImageView(this@MainActivity).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                clipToOutline = true
                outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, dp(20).toFloat())
                    }
                }
                setImageBitmap(bitmap)
                setOnClickListener {
                    pickImages.launch(arrayOf("image/*"))
                }
            }

            val borderOverlay = View(this@MainActivity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                background = GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                    cornerRadius = dp(20).toFloat()
                    setStroke(dp(1), Color.argb(65, 255, 255, 255))
                }
                isClickable = false
                isFocusable = false
            }

            addView(imgView)
            addView(borderOverlay)

            if (showClose) {
                val closeBtn = TextView(this@MainActivity).apply {
                    text = "✕"
                    textSize = 12f
                    setTextColor(Color.parseColor("#1A1D24"))
                    setTypeface(null, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.argb(235, 255, 255, 255))
                    }
                    layoutParams = FrameLayout.LayoutParams(dp(24), dp(24), Gravity.TOP or Gravity.END).apply {
                        setMargins(0, dp(8), dp(8), 0)
                    }
                    setOnClickListener {
                        onRemoveClick()
                    }
                }
                addView(closeBtn)
            }
        }
    }

    private fun makeDefaultCardBitmap(index: Int): Bitmap {
        val w = 240
        val h = 420
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        when (index) {
            0 -> {
                c.drawColor(Color.parseColor("#8FA4F3"))
                p.color = Color.parseColor("#B6C5F8")
                c.drawPath(Path().apply {
                    moveTo(0f, 0f); lineTo(w * 0.75f, 0f); lineTo(w * 0.45f, h * 0.52f); lineTo(0f, h * 0.35f); close()
                }, p)
                p.color = Color.parseColor("#6D82DC")
                c.drawPath(Path().apply {
                    moveTo(w * 0.75f, 0f); lineTo(w.toFloat(), 0f); lineTo(w.toFloat(), h * 0.45f); lineTo(w * 0.45f, h * 0.52f); close()
                }, p)
            }
            1 -> {
                p.shader = LinearGradient(
                    0f, 0f, w.toFloat(), h.toFloat(),
                    intArrayOf(Color.parseColor("#4A6FA5"), Color.parseColor("#38B6FF"), Color.parseColor("#1D3557")),
                    null, Shader.TileMode.CLAMP
                )
                c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
            }
            else -> {
                c.drawColor(Color.parseColor("#080D1A"))
                p.color = Color.parseColor("#29B6F6")
                p.strokeWidth = 3f
                c.drawLine(w * 0.65f, 0f, w * 0.65f, h.toFloat(), p)
                c.drawLine(0f, h * 0.42f, w.toFloat(), h * 0.42f, p)
            }
        }
        return bmp
    }

    private fun openCustomizationDialog() {
        val prefs = getSharedPreferences("origin_prefs", Context.MODE_PRIVATE)
        val dialog = Dialog(this@MainActivity, android.R.style.Theme_Black_NoTitleBar)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.apply {
            statusBarColor = Color.BLACK
            navigationBarColor = Color.WHITE
        }

        val previewOpts = BitmapFactory.Options().apply { inSampleSize = 2 }
        val cardPhotos = getCardPhotos()
        val previewBmp: Bitmap? = if (cardPhotos.isNotEmpty()) {
            BitmapFactory.decodeFile(cardPhotos[0].absolutePath, previewOpts)
        } else null

        val depth0File = File(filesDir, "depth_0.png")
        val previewDepthBmp: Bitmap? = if (depth0File.exists()) {
            BitmapFactory.decodeFile(depth0File.absolutePath, previewOpts)
        } else null

        val cachedColor = prefs.getInt("adaptive_clock_color", -1)
        val adaptiveColor = if (cachedColor != -1) {
            cachedColor
        } else {
            val c = extractAdaptiveColorFromBitmap(previewBmp)
            prefs.edit().putInt("adaptive_clock_color", c).apply()
            c
        }

        var isPanelOpen = false
        var currentMode = prefs.getInt("clock_mode", 0)
        var currentTab = 0
        var currentStyle = prefs.getInt("clock_style", 0).coerceIn(0, if (currentMode == 0) 3 else 2)
        var currentFont = prefs.getInt("clock_font", 0).coerceIn(0, 5)
        var currentCustomSize = prefs.getFloat("clock_custom_size", 1.35f).coerceIn(0.75f, 2.45f)
        var currentWeight = prefs.getFloat("clock_weight", 0.72f).coerceIn(0.25f, 1.0f)
        var currentColor = prefs.getInt("clock_color", ClockDrawer.COLOR_ADAPTIVE)
        var isDepthEnabled = prefs.getBoolean("depth_effect_enabled", false)
        var offsetX = prefs.getFloat("clock_offset_x", 0f).coerceIn(-0.28f, 0.28f)
        var offsetY = prefs.getFloat("clock_offset_y", 0f).coerceIn(-0.08f, 0.36f)
        var isHsvPickerOpen = false

        fun effectiveSize(): Float = if (currentMode == 1) currentCustomSize else 1.0f

        fun saveTempPrefs() {
            prefs.edit()
                .putInt("clock_mode", currentMode)
                .putInt("clock_style", currentStyle)
                .putInt("clock_font", currentFont)
                .putFloat("clock_custom_size", currentCustomSize)
                .putFloat("clock_size", effectiveSize())
                .putFloat("clock_weight", currentWeight)
                .putInt("clock_color", currentColor)
                .putInt("adaptive_clock_color", adaptiveColor)
                .putBoolean("depth_effect_enabled", isDepthEnabled)
                .putFloat("clock_offset_x", offsetX)
                .putFloat("clock_offset_y", offsetY)
                .apply()
        }

        val root = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, sysBars.top, 0, sysBars.bottom)
            insets
        }

        val topBarClosed = RelativeLayout(this@MainActivity).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(dp(20), dp(14), dp(20), dp(8))
        }

        val btnExitPreview = TextView(this@MainActivity).apply {
            text = "✕"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#24272E"))
            }
            layoutParams = RelativeLayout.LayoutParams(dp(42), dp(42)).apply {
                addRule(RelativeLayout.ALIGN_PARENT_START)
                addRule(RelativeLayout.CENTER_VERTICAL)
            }
            setOnClickListener { dialog.dismiss() }
        }

        val tvPreviewHint = TextView(this@MainActivity).apply {
            text = "Tap Clock to Customize"
            textSize = 14f
            setTextColor(Color.parseColor("#D0D4DC"))
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply { addRule(RelativeLayout.CENTER_IN_PARENT) }
        }

        val btnConfirmClock = TextView(this@MainActivity).apply {
            text = "✓"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#24272E"))
            }
            layoutParams = RelativeLayout.LayoutParams(dp(42), dp(42)).apply {
                addRule(RelativeLayout.ALIGN_PARENT_END)
                addRule(RelativeLayout.CENTER_VERTICAL)
            }
            setOnClickListener {
                saveTempPrefs()
                prefs.edit().putBoolean("clock_enabled", true).apply()
                updateToggleUI(true)
                bgExecutor.execute { precomputeWallpaperCaches() }
                dialog.dismiss()
            }
        }

        topBarClosed.addView(btnExitPreview)
        topBarClosed.addView(tvPreviewHint)
        topBarClosed.addView(btnConfirmClock)
        root.addView(topBarClosed)

        val previewWrapper = FrameLayout(this@MainActivity).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.32f
            )
            setPadding(dp(32), dp(6), dp(32), dp(12))
        }

        var activeHandleBoxBottom = 0f
        var isDraggingHandle = false
        var isDraggingClockPos = false
        var dragStartX = 0f
        var dragStartY = 0f
        var dragStartSize = currentCustomSize
        var dragStartOffsetX = offsetX
        var dragStartOffsetY = offsetY

        val previewView = object : View(this@MainActivity) {
            private val clipPath = Path()
            private val rectF = RectF()
            private val frameRect = RectF()
            private val drawMatrix = Matrix()
            private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            private val depthPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(225, 255, 255, 255)
                style = Paint.Style.STROKE
                strokeWidth = dp(2).toFloat()
            }
            private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            private val handleKnobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#1A73E8")
                style = Paint.Style.FILL
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val vw = width.toFloat()
                val vh = height.toFloat()
                if (vw <= 0f || vh <= 0f) return

                val targetRatio = 9f / 19.5f
                val cardW: Float
                val cardH: Float
                if (vw / vh > targetRatio) {
                    cardH = vh
                    cardW = vh * targetRatio
                } else {
                    cardW = vw
                    cardH = vw / targetRatio
                }
                val left = (vw - cardW) / 2f
                val top = (vh - cardH) / 2f

                rectF.set(left, top, left + cardW, top + cardH)
                clipPath.reset()
                clipPath.addRoundRect(rectF, dp(28).toFloat(), dp(28).toFloat(), Path.Direction.CW)

                canvas.save()
                canvas.clipPath(clipPath)

                if (previewBmp != null) {
                    val bw = previewBmp.width.toFloat()
                    val bh = previewBmp.height.toFloat()
                    val scale = maxOf(cardW / bw, cardH / bh)
                    val dx = left + (cardW - bw * scale) / 2f
                    val dy = top + (cardH - bh * scale) / 2f
                    drawMatrix.reset()
                    drawMatrix.setScale(scale, scale)
                    drawMatrix.postTranslate(dx, dy)
                    canvas.drawBitmap(previewBmp, drawMatrix, bgPaint)
                } else {
                    val grad = LinearGradient(
                        left, top, left + cardW, top + cardH,
                        intArrayOf(
                            Color.parseColor("#29B6F6"),
                            Color.parseColor("#0288D1"),
                            Color.parseColor("#43A047")
                        ),
                        floatArrayOf(0f, 0.55f, 1f),
                        Shader.TileMode.CLAMP
                    )
                    bgPaint.shader = grad
                    canvas.drawRect(rectF, bgPaint)
                    bgPaint.shader = null
                }

                canvas.save()
                canvas.translate(left, top)
                ClockDrawer.drawClock(canvas, prefs, cardW, cardH)

                if (isDepthEnabled && previewDepthBmp != null) {
                    val dw = previewDepthBmp.width.toFloat()
                    val dh = previewDepthBmp.height.toFloat()
                    val dScale = maxOf(cardW / dw, cardH / dh)
                    val ddx = (cardW - dw * dScale) / 2f
                    val ddy = (cardH - dh * dScale) / 2f
                    drawMatrix.reset()
                    drawMatrix.setScale(dScale, dScale)
                    drawMatrix.postTranslate(ddx, ddy)
                    canvas.drawBitmap(previewDepthBmp, drawMatrix, depthPaint)
                }

                if (isPanelOpen) {
                    val boxTop = (cardH * 0.08f + cardH * offsetY).coerceIn(cardH * 0.04f, cardH * 0.52f)
                    val baseSpan = if (currentMode == 1) cardH * 0.34f else cardH * 0.24f
                    val dynamicBottom = (boxTop + baseSpan * effectiveSize())
                        .coerceIn(boxTop + cardH * 0.14f, cardH * 0.88f)

                    activeHandleBoxBottom = top + dynamicBottom

                    frameRect.set(cardW * 0.06f, boxTop, cardW * 0.94f, dynamicBottom)
                    canvas.drawRoundRect(frameRect, dp(16).toFloat(), dp(16).toFloat(), boxPaint)

                    if (currentMode == 1) {
                        val handleW = cardW * 0.24f
                        val handleH = dp(6).toFloat()
                        canvas.drawRoundRect(
                            cardW * 0.5f - handleW / 2f, dynamicBottom - handleH / 2f,
                            cardW * 0.5f + handleW / 2f, dynamicBottom + handleH / 2f,
                            handleH, handleH, handlePaint
                        )
                        canvas.drawCircle(cardW * 0.5f, dynamicBottom, dp(6).toFloat(), handleKnobPaint)
                    }
                }
                canvas.restore()
                canvas.restore()
            }
        }

        val sheet = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.06f
            )
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                val r = dp(28).toFloat()
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
            setPadding(dp(18), dp(14), dp(18), dp(14))
        }

        val topBar = RelativeLayout(this@MainActivity).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val btnApplyClock = TextView(this@MainActivity).apply {
            text = "Apply"
            textSize = 13f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A73E8"))
                cornerRadius = dp(16).toFloat()
            }
            setPadding(dp(14), dp(7), dp(14), dp(7))
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.ALIGN_PARENT_START)
                addRule(RelativeLayout.CENTER_VERTICAL)
            }
            setOnClickListener {
                saveTempPrefs()
                prefs.edit().putBoolean("clock_enabled", true).apply()
                updateToggleUI(true)
                bgExecutor.execute { precomputeWallpaperCaches() }
                dialog.dismiss()
            }
        }

        val modeSwitchBox = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F1F3F5"))
                cornerRadius = dp(10).toFloat()
            }
            setPadding(dp(3), dp(3), dp(3), dp(3))
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply { addRule(RelativeLayout.CENTER_IN_PARENT) }
        }

        val btnClassic = TextView(this@MainActivity).apply { text = "Classic"; textSize = 13f; setPadding(dp(16), dp(7), dp(16), dp(7)) }
        val btnCustom = TextView(this@MainActivity).apply { text = "Custom"; textSize = 13f; setPadding(dp(16), dp(7), dp(16), dp(7)) }
        modeSwitchBox.addView(btnClassic)
        modeSwitchBox.addView(btnCustom)

        val btnClose = TextView(this@MainActivity).apply {
            text = "✕"
            textSize = 18f
            setTextColor(Color.parseColor("#333333"))
            setPadding(dp(10), dp(4), dp(6), dp(4))
            layoutParams = RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                addRule(RelativeLayout.ALIGN_PARENT_END)
                addRule(RelativeLayout.CENTER_VERTICAL)
            }
            setOnClickListener {
                isPanelOpen = false
                isHsvPickerOpen = false
                sheet.visibility = View.GONE
                topBarClosed.visibility = View.VISIBLE
                previewView.invalidate()
            }
        }

        topBar.addView(btnApplyClock)
        topBar.addView(modeSwitchBox)
        topBar.addView(btnClose)
        sheet.addView(topBar)

        val tabRow = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 4f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(12), 0, dp(10)) }
        }

        fun makeEvenTab(label: String): TextView = TextView(this@MainActivity).apply {
            text = label
            textSize = 13f
            gravity = Gravity.CENTER
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(dp(2), dp(6), dp(2), dp(6))
        }

        val tabStyles = makeEvenTab("Styles")
        val tabFonts = makeEvenTab("Fonts")
        val tabColors = makeEvenTab("Colors")
        val tabDepth = makeEvenTab("Depth Effect")
        tabRow.addView(tabStyles)
        tabRow.addView(tabFonts)
        tabRow.addView(tabColors)
        tabRow.addView(tabDepth)
        sheet.addView(tabRow)

        val contentHolder = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
        sheet.addView(contentHolder)

        fun buildStyleCard(idx: Int, isSelected: Boolean, isCustomMode: Boolean): LinearLayout {
            return LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#F5F6F8"))
                    cornerRadius = dp(16).toFloat()
                    if (isSelected) setStroke(dp(2), Color.parseColor("#1A73E8"))
                }
                layoutParams = LinearLayout.LayoutParams(dp(106), dp(116)).apply {
                    setMargins(0, 0, dp(12), 0)
                }
                setPadding(dp(8), dp(6), dp(8), dp(6))

                val dateTv = TextView(this@MainActivity).apply {
                    text = if (isCustomMode && idx == 2) "11/19\nThu" else "Thu, Nov 19"
                    textSize = 8.5f
                    setTextColor(Color.parseColor("#444444"))
                    gravity = if (!isCustomMode && idx == 2) Gravity.START else Gravity.CENTER
                }

                val timeTv = TextView(this@MainActivity).apply {
                    setTextColor(Color.BLACK)
                    setTypeface(
                        Typeface.create(if (isCustomMode) "sans-serif-condensed" else "sans-serif", Typeface.BOLD)
                    )
                    if (isCustomMode) textScaleX = 0.68f
                    gravity = Gravity.CENTER
                    when {
                        !isCustomMode && idx == 0 -> { text = "4:37"; textSize = 22f }
                        !isCustomMode && idx == 1 -> { text = "04\n37"; textSize = 20f; setLineSpacing(0f, 0.76f) }
                        !isCustomMode && idx == 2 -> { text = "4:37"; textSize = 20f; gravity = Gravity.START }
                        !isCustomMode && idx == 3 -> { text = "4:37  11/19"; textSize = 11.5f }
                        isCustomMode && idx == 0 -> { text = "4:37"; textSize = 34f }
                        isCustomMode && idx == 1 -> { text = "04\n37"; textSize = 26f; setLineSpacing(0f, 0.72f) }
                        else -> { text = "0 4\n3 7"; textSize = 25f; setLineSpacing(0f, 0.72f) }
                    }
                }

                if (!(!isCustomMode && idx == 3)) addView(dateTv)
                addView(timeTv)
            }
        }

        val mergedPresetColors = listOf(
            Color.WHITE,
            ClockDrawer.COLOR_ADAPTIVE,
            Color.parseColor("#5C76B8"),
            Color.parseColor("#4F815D"),
            Color.parseColor("#6E50A6"),
            Color.parseColor("#9C6D2E"),
            Color.parseColor("#DE7076"),
            Color.parseColor("#B59410"),
            Color.parseColor("#689F38"),
            Color.parseColor("#4285F4"),
            Color.parseColor("#9575CD"),
            Color.parseColor("#BA68C8"),
            Color.parseColor("#FFCC80"),
            Color.parseColor("#00ACC1"),
            Color.parseColor("#FF7043"),
            Color.BLACK,
            ClockDrawer.COLOR_CUSTOM_WHEEL
        )

        fun updateUI() {
            val activePill = GradientDrawable().apply {
                setColor(Color.parseColor("#DCEBFF"))
                cornerRadius = dp(8).toFloat()
            }
            btnClassic.background = if (currentMode == 0) activePill else null
            btnClassic.setTextColor(if (currentMode == 0) Color.parseColor("#1A73E8") else Color.parseColor("#757575"))
            btnClassic.setTypeface(null, if (currentMode == 0) Typeface.BOLD else Typeface.NORMAL)

            btnCustom.background = if (currentMode == 1) activePill else null
            btnCustom.setTextColor(if (currentMode == 1) Color.parseColor("#1A73E8") else Color.parseColor("#757575"))
            btnCustom.setTypeface(null, if (currentMode == 1) Typeface.BOLD else Typeface.NORMAL)

            listOf(tabStyles, tabFonts, tabColors, tabDepth).forEachIndexed { idx, tv ->
                tv.setTextColor(if (idx == currentTab) Color.BLACK else Color.parseColor("#8A9099"))
                tv.setTypeface(null, if (idx == currentTab) Typeface.BOLD else Typeface.NORMAL)
                tv.background = if (idx == currentTab) {
                    GradientDrawable().apply {
                        setColor(Color.parseColor("#EEF3FC"))
                        cornerRadius = dp(10).toFloat()
                    }
                } else null
            }

            contentHolder.removeAllViews()
            val hScroll = HorizontalScrollView(this@MainActivity).apply { isHorizontalScrollBarEnabled = false }
            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(2), dp(4), dp(2), dp(4))
            }

            when (currentTab) {
                0 -> {
                    val count = if (currentMode == 0) 4 else 3
                    currentStyle = currentStyle.coerceIn(0, count - 1)
                    for (idx in 0 until count) {
                        val card = buildStyleCard(idx, currentStyle == idx, currentMode == 1)
                        card.setOnClickListener {
                            currentStyle = idx
                            if (currentMode == 0 || currentStyle == 0) {
                                offsetX = 0f
                                offsetY = 0f
                            }
                            saveTempPrefs()
                            previewView.invalidate()
                            updateUI()
                        }
                        row.addView(card)
                    }
                    hScroll.addView(row)
                    contentHolder.addView(hScroll)
                }

                1 -> {
                    val fontCount = 6
                    for (idx in 0 until fontCount) {
                        val cardW = dp(82)
                        val cardH = dp(82)
                        val card = object : View(this@MainActivity) {
                            private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = Color.BLACK
                                textAlign = Paint.Align.CENTER
                            }
                            override fun onDraw(canvas: Canvas) {
                                super.onDraw(canvas)
                                p.textScaleX = if (currentMode == 1) 0.64f else 0.86f
                                p.textSize = dp(28).toFloat()
                                p.typeface = ClockDrawer.resolveTypeface(idx, currentWeight)
                                if (currentWeight < 0.45f) {
                                    p.style = Paint.Style.STROKE
                                    p.strokeWidth = dpF(1.2f + (0.45f - currentWeight) * 2.2f)
                                } else {
                                    p.style = Paint.Style.FILL
                                    p.strokeWidth = 0f
                                }
                                val yPos = height / 2f - (p.descent() + p.ascent()) / 2f
                                canvas.drawText("10", width / 2f, yPos, p)
                            }
                        }.apply {
                            background = GradientDrawable().apply {
                                setColor(Color.parseColor("#F5F6F8"))
                                cornerRadius = dp(14).toFloat()
                                if (currentFont == idx) setStroke(dp(2), Color.parseColor("#1A73E8"))
                            }
                            layoutParams = LinearLayout.LayoutParams(cardW, cardH).apply {
                                setMargins(0, 0, dp(10), 0)
                            }
                            setOnClickListener {
                                currentFont = idx
                                saveTempPrefs()
                                previewView.invalidate()
                                updateUI()
                            }
                        }
                        row.addView(card)
                    }
                    hScroll.addView(row)
                    contentHolder.addView(hScroll)

                    val sliderRow = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(6), dp(14), dp(6), 0)
                    }
                    val thinLabel = TextView(this@MainActivity).apply {
                        text = "A"
                        textSize = 15f
                        setTextColor(Color.parseColor("#757575"))
                        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                    }
                    val boldLabel = TextView(this@MainActivity).apply {
                        text = "A"
                        textSize = 17f
                        setTextColor(Color.BLACK)
                        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
                    }
                    val seek = SeekBar(this@MainActivity).apply {
                        max = 75
                        progress = ((currentWeight - 0.25f) * 100).toInt().coerceIn(0, 75)
                        progressTintList = ColorStateList.valueOf(Color.parseColor("#1A73E8"))
                        thumbTintList = ColorStateList.valueOf(Color.parseColor("#1A73E8"))
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                            setMargins(dp(12), 0, dp(12), 0)
                        }
                        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                                if (fromUser) {
                                    currentWeight = 0.25f + (p / 100f)
                                    saveTempPrefs()
                                    previewView.invalidate()
                                    for (i in 0 until row.childCount) {
                                        row.getChildAt(i).invalidate()
                                    }
                                }
                            }
                            override fun onStartTrackingTouch(s: SeekBar?) {}
                            override fun onStopTrackingTouch(s: SeekBar?) {}
                        })
                    }
                    sliderRow.addView(thinLabel)
                    sliderRow.addView(seek)
                    sliderRow.addView(boldLabel)
                    contentHolder.addView(sliderRow)
                }

                2 -> {
                    if (isHsvPickerOpen) {
                        val initColor = if (currentColor == ClockDrawer.COLOR_ADAPTIVE) adaptiveColor else currentColor
                        val hsv = FloatArray(3)
                        Color.colorToHSV(initColor, hsv)
                        if (initColor == Color.WHITE) {
                            hsv[0] = 210f; hsv[1] = 0.65f; hsv[2] = 0.95f
                        }

                        val headerRow = RelativeLayout(this@MainActivity).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                            setPadding(dp(4), dp(0), dp(4), dp(6))
                        }

                        val pickerTitle = TextView(this@MainActivity).apply {
                            text = "Customize color"
                            textSize = 14f
                            setTextColor(Color.BLACK)
                            setTypeface(null, Typeface.BOLD)
                            layoutParams = RelativeLayout.LayoutParams(
                                RelativeLayout.LayoutParams.WRAP_CONTENT,
                                RelativeLayout.LayoutParams.WRAP_CONTENT
                            ).apply { addRule(RelativeLayout.CENTER_VERTICAL) }
                        }

                        val btnBackColors = TextView(this@MainActivity).apply {
                            text = "Done"
                            textSize = 13f
                            setTextColor(Color.parseColor("#1A73E8"))
                            setTypeface(null, Typeface.BOLD)
                            setPadding(dp(10), dp(2), dp(6), dp(2))
                            layoutParams = RelativeLayout.LayoutParams(
                                RelativeLayout.LayoutParams.WRAP_CONTENT,
                                RelativeLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                addRule(RelativeLayout.ALIGN_PARENT_END)
                                addRule(RelativeLayout.CENTER_VERTICAL)
                            }
                            setOnClickListener {
                                isHsvPickerOpen = false
                                updateUI()
                            }
                        }

                        headerRow.addView(pickerTitle)
                        headerRow.addView(btnBackColors)
                        contentHolder.addView(headerRow)

                        fun applyHsv() {
                            currentColor = Color.HSVToColor(hsv)
                            saveTempPrefs()
                            previewView.invalidate()
                        }

                        fun buildHsvSlider(label: String, maxVal: Int, initProgress: Int, gradColors: IntArray, onSlide: (Int) -> Unit): LinearLayout {
                            return LinearLayout(this@MainActivity).apply {
                                orientation = LinearLayout.VERTICAL
                                setPadding(dp(4), dp(2), dp(4), dp(4))

                                val lbl = TextView(this@MainActivity).apply {
                                    text = label
                                    textSize = 11.5f
                                    setTextColor(Color.parseColor("#5F6368"))
                                }

                                val sb = SeekBar(this@MainActivity).apply {
                                    max = maxVal
                                    progress = initProgress
                                    progressDrawable = GradientDrawable(
                                        GradientDrawable.Orientation.LEFT_RIGHT,
                                        gradColors
                                    ).apply {
                                        cornerRadius = dp(8).toFloat()
                                    }
                                    thumbTintList = ColorStateList.valueOf(Color.parseColor("#1A73E8"))
                                    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                                        override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                                            if (fromUser) onSlide(p)
                                        }
                                        override fun onStartTrackingTouch(s: SeekBar?) {}
                                        override fun onStopTrackingTouch(s: SeekBar?) {}
                                    })
                                }

                                addView(lbl)
                                addView(sb)
                            }
                        }

                        contentHolder.addView(
                            buildHsvSlider(
                                "Hue", 360, hsv[0].toInt(),
                                intArrayOf(Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED)
                            ) { p -> hsv[0] = p.toFloat(); applyHsv() }
                        )
                        contentHolder.addView(
                            buildHsvSlider(
                                "Saturation", 100, (hsv[1] * 100).toInt(),
                                intArrayOf(Color.WHITE, Color.HSVToColor(floatArrayOf(hsv[0], 1f, 1f)))
                            ) { p -> hsv[1] = p / 100f; applyHsv() }
                        )
                        contentHolder.addView(
                            buildHsvSlider(
                                "Brightness", 100, (hsv[2] * 100).toInt(),
                                intArrayOf(Color.BLACK, Color.HSVToColor(floatArrayOf(hsv[0], hsv[1], 1f)))
                            ) { p -> hsv[2] = p / 100f; applyHsv() }
                        )
                    } else {
                        val isCustomHsvSelected = mergedPresetColors.none {
                            it != ClockDrawer.COLOR_CUSTOM_WHEEL && it == currentColor
                        }

                        mergedPresetColors.forEach { col ->
                            val swatch = object : View(this@MainActivity) {
                                private val p = Paint(Paint.ANTI_ALIAS_FLAG)
                                private val ringP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                    style = Paint.Style.STROKE
                                    strokeWidth = dp(2).toFloat()
                                    color = Color.parseColor("#1A73E8")
                                }
                                private val textP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                    textAlign = Paint.Align.CENTER
                                    textSize = dp(10).toFloat()
                                    typeface = Typeface.create("sans-serif-bold", Typeface.BOLD)
                                }

                                override fun onDraw(canvas: Canvas) {
                                    super.onDraw(canvas)
                                    val cx = width / 2f
                                    val cy = height / 2f
                                    val r = width * 0.38f

                                    when (col) {
                                        Color.WHITE -> {
                                            p.color = Color.WHITE
                                            canvas.drawCircle(cx, cy, r, p)
                                            p.style = Paint.Style.STROKE
                                            p.strokeWidth = dp(1).toFloat()
                                            p.color = Color.parseColor("#BDBDBD")
                                            canvas.drawCircle(cx, cy, r, p)
                                            p.style = Paint.Style.FILL
                                        }
                                        ClockDrawer.COLOR_ADAPTIVE -> {
                                            p.color = adaptiveColor
                                            canvas.drawCircle(cx, cy, r, p)
                                            p.style = Paint.Style.STROKE
                                            p.strokeWidth = dp(1).toFloat()
                                            p.color = Color.parseColor("#90A4AE")
                                            canvas.drawCircle(cx, cy, r, p)
                                            p.style = Paint.Style.FILL
                                            textP.color = if (isLightColor(adaptiveColor)) Color.BLACK else Color.WHITE
                                            val yPos = cy - (textP.descent() + textP.ascent()) / 2f
                                            canvas.drawText("A", cx, yPos, textP)
                                        }
                                        ClockDrawer.COLOR_CUSTOM_WHEEL -> {
                                            p.shader = SweepGradient(
                                                cx, cy,
                                                intArrayOf(Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED),
                                                null
                                            )
                                            canvas.drawCircle(cx, cy, r, p)
                                            p.shader = null
                                        }
                                        else -> {
                                            p.color = col
                                            canvas.drawCircle(cx, cy, r, p)
                                        }
                                    }

                                    val selected = if (col == ClockDrawer.COLOR_CUSTOM_WHEEL) {
                                        isCustomHsvSelected
                                    } else {
                                        currentColor == col
                                    }
                                    if (selected) {
                                        canvas.drawCircle(cx, cy, width * 0.46f, ringP)
                                    }
                                }
                            }.apply {
                                layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                                    setMargins(0, dp(10), dp(12), dp(10))
                                }
                                setOnClickListener {
                                    if (col == ClockDrawer.COLOR_CUSTOM_WHEEL) {
                                        isHsvPickerOpen = true
                                        updateUI()
                                    } else {
                                        currentColor = col
                                        saveTempPrefs()
                                        previewView.invalidate()
                                        updateUI()
                                    }
                                }
                            }
                            row.addView(swatch)
                        }
                        hScroll.addView(row)
                        contentHolder.addView(hScroll)
                    }
                }

                else -> {
                    val depthCard = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = GradientDrawable().apply {
                            setColor(Color.parseColor("#F5F6F8"))
                            cornerRadius = dp(18).toFloat()
                        }
                        setPadding(dp(16), dp(14), dp(16), dp(14))
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { setMargins(0, dp(6), 0, 0) }
                    }

                    val depthInfo = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    val dTitle = TextView(this@MainActivity).apply {
                        text = "Depth Effect"
                        textSize = 15f
                        setTextColor(Color.BLACK)
                        setTypeface(null, Typeface.BOLD)
                    }
                    val dSub = TextView(this@MainActivity).apply {
                        text = "Place foreground subject over clock digits"
                        textSize = 12f
                        setTextColor(Color.parseColor("#5F6368"))
                    }
                    depthInfo.addView(dTitle)
                    depthInfo.addView(dSub)

                    val depthSwitch = Switch(this@MainActivity).apply {
                        isChecked = isDepthEnabled
                        thumbTintList = ColorStateList.valueOf(
                            if (isDepthEnabled) Color.parseColor("#1A73E8") else Color.parseColor("#BDBDBD")
                        )
                        trackTintList = ColorStateList.valueOf(
                            if (isDepthEnabled) Color.parseColor("#90CAF9") else Color.parseColor("#E0E0E0")
                        )
                        setOnCheckedChangeListener { _, checked ->
                            isDepthEnabled = checked
                            saveTempPrefs()
                            previewView.invalidate()
                            updateUI()
                        }
                    }

                    depthCard.addView(depthInfo)
                    depthCard.addView(depthSwitch)
                    contentHolder.addView(depthCard)
                }
            }
        }

        val scaleDetector = ScaleGestureDetector(this@MainActivity, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (isPanelOpen && currentMode == 1) {
                    currentCustomSize = (currentCustomSize * detector.scaleFactor).coerceIn(0.75f, 2.45f)
                    saveTempPrefs()
                    previewView.invalidate()
                }
                return true
            }
        })

        previewView.setOnTouchListener { v, event ->
            scaleDetector.onTouchEvent(event)
            val h = v.height.toFloat()
            val w = v.width.toFloat()

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (isPanelOpen && currentMode == 1 && abs(event.y - activeHandleBoxBottom) <= dp(30)) {
                        isDraggingHandle = true
                        dragStartY = event.y
                        dragStartSize = currentCustomSize
                        return@setOnTouchListener true
                    }
                    if (isPanelOpen && currentMode == 1 && currentStyle >= 1) {
                        isDraggingClockPos = true
                        dragStartX = event.x
                        dragStartY = event.y
                        dragStartOffsetX = offsetX
                        dragStartOffsetY = offsetY
                        return@setOnTouchListener true
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDraggingHandle && h > 0f) {
                        val deltaRatio = (event.y - dragStartY) / (h * 0.28f)
                        currentCustomSize = (dragStartSize + deltaRatio).coerceIn(0.75f, 2.45f)
                        saveTempPrefs()
                        previewView.invalidate()
                        return@setOnTouchListener true
                    }
                    if (isDraggingClockPos && w > 0f && h > 0f) {
                        val dxRatio = (event.x - dragStartX) / w
                        val dyRatio = (event.y - dragStartY) / h
                        offsetX = (dragStartOffsetX + dxRatio).coerceIn(-0.28f, 0.28f)
                        offsetY = (dragStartOffsetY + dyRatio).coerceIn(-0.08f, 0.36f)
                        saveTempPrefs()
                        previewView.invalidate()
                        return@setOnTouchListener true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDraggingHandle || isDraggingClockPos) {
                        isDraggingHandle = false
                        isDraggingClockPos = false
                        return@setOnTouchListener true
                    }
                    if (!isPanelOpen && event.actionMasked == MotionEvent.ACTION_UP) {
                        val clockTop = h * 0.05f
                        val clockBottom = h * 0.62f
                        if (event.y in clockTop..clockBottom) {
                            isPanelOpen = true
                            topBarClosed.visibility = View.GONE
                            sheet.visibility = View.VISIBLE
                            previewView.invalidate()
                            updateUI()
                        }
                    }
                }
            }
            true
        }

        previewWrapper.addView(previewView)
        root.addView(previewWrapper)
        root.addView(sheet)

        btnClassic.setOnClickListener {
            currentMode = 0
            currentStyle = currentStyle.coerceIn(0, 3)
            offsetX = 0f
            offsetY = 0f
            isHsvPickerOpen = false
            saveTempPrefs(); previewView.invalidate(); updateUI()
        }
        btnCustom.setOnClickListener {
            currentMode = 1
            currentStyle = currentStyle.coerceIn(0, 2)
            isHsvPickerOpen = false
            saveTempPrefs(); previewView.invalidate(); updateUI()
        }
        tabStyles.setOnClickListener { currentTab = 0; isHsvPickerOpen = false; updateUI() }
        tabFonts.setOnClickListener { currentTab = 1; isHsvPickerOpen = false; updateUI() }
        tabColors.setOnClickListener { currentTab = 2; updateUI() }
        tabDepth.setOnClickListener { currentTab = 3; isHsvPickerOpen = false; updateUI() }

        dialog.setContentView(root)
        dialog.show()
    }

    private fun getRealScreenSize(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            Pair(bounds.width(), bounds.height())
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            Pair(metrics.widthPixels, metrics.heightPixels)
        }
    }

    private fun appendSelectedImages(uris: List<Uri>) {
        val existing = getCardPhotos()
        val existingCount = existing.size
        val remainingSlots = (6 - existingCount).coerceAtLeast(0)
        if (remainingSlots == 0) {
            Toast.makeText(this, "Maximum 6 photos allowed!", Toast.LENGTH_SHORT).show()
            return
        }

        val (screenW, screenH) = getRealScreenSize()
        bgExecutor.execute {
            var nextIndex = existingCount
            var added = 0

            for (uri in uris.take(remainingSlots)) {
                contentResolver.openInputStream(uri)?.use { input ->
                    val rawBitmap = BitmapFactory.decodeStream(input)
                    if (rawBitmap != null) {
                        val scaled = centerCropBitmap(rawBitmap, screenW, screenH)
                        val targetFile = File(filesDir, "wp_$nextIndex.jpg")
                        FileOutputStream(targetFile).use { out ->
                            scaled.compress(Bitmap.CompressFormat.JPEG, 92, out)
                        }
                        rawBitmap.recycle()
                        scaled.recycle()
                        nextIndex++
                        added++
                    }
                }
            }

            precomputeWallpaperCaches()

            runOnUiThread {
                refreshCardsStrip()
                Toast.makeText(this@MainActivity, "$added Photo(s) Added!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun centerCropBitmap(src: Bitmap, targetW: Int, targetH: Int): Bitmap {
        val scale = maxOf(targetW.toFloat() / src.width, targetH.toFloat() / src.height)
        val scaledW = (src.width * scale).toInt()
        val scaledH = (src.height * scale).toInt()
        val scaled = Bitmap.createScaledBitmap(src, scaledW, scaledH, true)
        val x = ((scaledW - targetW) / 2).coerceAtLeast(0)
        val y = ((scaledH - targetH) / 2).coerceAtLeast(0)
        return Bitmap.createBitmap(scaled, x, y, targetW, targetH)
    }
}