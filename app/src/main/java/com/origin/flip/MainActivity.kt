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
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var footerStatus: TextView
    private lateinit var cardsRow: LinearLayout

    private val pickImages = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@registerForActivityResult
        appendSelectedImages(uris)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

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

        val btnApply = TextView(this).apply {
            text = "Apply Origin Flip Wallpaper"
            textSize = 17f
            setTextColor(Color.WHITE)
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            gravity = Gravity.CENTER
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#29B6F6"), Color.parseColor("#1976D2"))
            ).apply {
                cornerRadius = dp(24).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(64)
            ).apply { setMargins(0, 0, 0, dp(16)) }
            setOnClickListener {
                val savedCount = getSavedPhotoFiles().size
                if (savedCount < 2) {
                    Toast.makeText(this@MainActivity, "Kam se kam 2 photos chuno!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                    putExtra(
                        WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                        ComponentName(this@MainActivity, FlipWallpaperService::class.java)
                    )
                }
                startActivity(intent)
            }
        }

        val btnCustomize = TextView(this).apply {
            text = "CUSTOMIZATION"
            textSize = 17f
            setTextColor(Color.parseColor("#0D1117"))
            setTypeface(Typeface.create("sans-serif-bold", Typeface.BOLD))
            letterSpacing = 0.04f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(24).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(64)
            ).apply { setMargins(0, 0, 0, dp(22)) }
            setOnClickListener {
                openCustomizationDialog()
            }
        }

        footerStatus = TextView(this).apply {
            text = "Active  •  OriginOS Seamless Flip  •  Ready to apply"
            textSize = 13f
            setTextColor(Color.parseColor("#4FC3F7"))
            gravity = Gravity.CENTER
        }

        refreshCardsStrip()

        content.addView(badge)
        content.addView(title)
        content.addView(subtitle)
        content.addView(hScrollCards)
        content.addView(btnApply)
        content.addView(btnCustomize)
        content.addView(footerStatus)

        root.addView(content)
        setContentView(root)
    }

    private fun getSavedPhotoFiles(): List<File> {
        return filesDir.listFiles()
            ?.filter { it.name.startsWith("wp_") && it.name.endsWith(".jpg") }
            ?.sortedBy { it.name } ?: emptyList()
    }

    private fun reindexPhotoFiles() {
        val currentFiles = getSavedPhotoFiles()
        currentFiles.forEachIndexed { idx, file ->
            val tempFile = File(filesDir, "tmp_wp_$idx.jpg")
            file.renameTo(tempFile)
        }
        filesDir.listFiles()
            ?.filter { it.name.startsWith("tmp_wp_") && it.name.endsWith(".jpg") }
            ?.sortedBy { it.name }
            ?.forEachIndexed { idx, tempFile ->
                tempFile.renameTo(File(filesDir, "wp_$idx.jpg"))
            }
    }

    private fun removePhotoAt(index: Int) {
        val files = getSavedPhotoFiles()
        if (index in files.indices) {
            files[index].delete()
            reindexPhotoFiles()
            refreshCardsStrip()
            Toast.makeText(this, "Photo removed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshCardsStrip() {
        cardsRow.removeAllViews()
        val cardW = dp(104)
        val cardH = dp(192)
        val gap = dp(12)

        val savedFiles = getSavedPhotoFiles()

        if (savedFiles.isNotEmpty()) {
            savedFiles.forEachIndexed { index, file ->
                val bmp = BitmapFactory.decodeFile(file.absolutePath)
                if (bmp != null) {
                    cardsRow.addView(buildPhotoCard(bmp, cardW, cardH, gap, showClose = true) {
                        removePhotoAt(index)
                    })
                }
            }
        } else {
            for (i in 0 until 3) {
                cardsRow.addView(buildPhotoCard(makeDefaultCardBitmap(i), cardW, cardH, gap, showClose = true) {
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

        if (::footerStatus.isInitialized) {
            footerStatus.text = if (savedFiles.isNotEmpty()) {
                "Active  •  ${savedFiles.size} Photos Selected  •  Ready to apply"
            } else {
                "Active  •  OriginOS Seamless Flip  •  Ready to apply"
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
        val w = 300
        val h = 520
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
        val dialog = Dialog(this@MainActivity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val wpFile = File(filesDir, "wp_0.jpg")
        val previewBmp: Bitmap? = if (wpFile.exists()) {
            BitmapFactory.decodeFile(wpFile.absolutePath)
        } else null

        var isPanelOpen = false
        var currentMode = prefs.getInt("clock_mode", 0)
        var currentTab = 0
        var currentStyle = prefs.getInt("clock_style", 0)
        var currentFont = prefs.getInt("clock_font", 0)
        var currentSize = prefs.getFloat("clock_size", 1.0f)
        var currentColor = prefs.getInt("clock_color", Color.parseColor("#5C76B8"))

        fun saveTempPrefs() {
            prefs.edit()
                .putInt("clock_mode", currentMode)
                .putInt("clock_style", currentStyle)
                .putInt("clock_font", currentFont)
                .putFloat("clock_size", currentSize)
                .putInt("clock_color", currentColor)
                .apply()
        }

        val root = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }

        val topBarClosed = RelativeLayout(this@MainActivity).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(dp(20), dp(18), dp(20), dp(6))
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
            layoutParams = RelativeLayout.LayoutParams(dp(40), dp(40)).apply {
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
            layoutParams = RelativeLayout.LayoutParams(dp(40), dp(40)).apply {
                addRule(RelativeLayout.ALIGN_PARENT_END)
                addRule(RelativeLayout.CENTER_VERTICAL)
            }
            setOnClickListener {
                saveTempPrefs()
                prefs.edit().putBoolean("clock_enabled", true).apply()
                Toast.makeText(this@MainActivity, "Clock Applied to Home & Lock Screen!", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }

        topBarClosed.addView(btnExitPreview)
        topBarClosed.addView(tvPreviewHint)
        topBarClosed.addView(btnConfirmClock)
        root.addView(topBarClosed)

        val previewWrapper = FrameLayout(this@MainActivity).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.35f
            )
            setPadding(dp(44), dp(10), dp(44), dp(18))
        }

        val previewView = object : View(this@MainActivity) {
            private val clipPath = Path()
            private val rectF = RectF()
            private val frameRect = RectF()
            private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(225, 255, 255, 255)
                style = Paint.Style.STROKE
                strokeWidth = dp(2).toFloat()
            }
            private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val w = width.toFloat()
                val h = height.toFloat()
                rectF.set(0f, 0f, w, h)
                clipPath.reset()
                clipPath.addRoundRect(rectF, dp(28).toFloat(), dp(28).toFloat(), Path.Direction.CW)
                canvas.save()
                canvas.clipPath(clipPath)

                if (previewBmp != null) {
                    canvas.drawBitmap(previewBmp, null, rectF, bgPaint)
                } else {
                    val grad = LinearGradient(
                        0f, 0f, w, h,
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

                ClockDrawer.drawClock(canvas, prefs, w, h)

                if (isPanelOpen) {
                    val boxTop = h * 0.10f
                    val boxBottom = if (currentMode == 1) h * 0.50f else h * 0.38f
                    frameRect.set(w * 0.06f, boxTop, w * 0.94f, boxBottom)
                    canvas.drawRoundRect(frameRect, dp(16).toFloat(), dp(16).toFloat(), boxPaint)

                    if (currentMode == 1) {
                        val handleW = w * 0.14f
                        val handleH = dp(4).toFloat()
                        canvas.drawRoundRect(
                            w * 0.5f - handleW / 2f, boxTop - handleH / 2f,
                            w * 0.5f + handleW / 2f, boxTop + handleH / 2f,
                            handleH, handleH, handlePaint
                        )
                        canvas.drawRoundRect(
                            w * 0.5f - handleW / 2f, boxBottom - handleH / 2f,
                            w * 0.5f + handleW / 2f, boxBottom + handleH / 2f,
                            handleH, handleH, handlePaint
                        )
                    }
                }
                canvas.restore()
            }
        }

        val sheet = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 0.92f
            )
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                val r = dp(28).toFloat()
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
            setPadding(dp(20), dp(18), dp(20), dp(16))
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
                Toast.makeText(this@MainActivity, "Clock Applied to Home & Lock Screen!", Toast.LENGTH_SHORT).show()
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
            setPadding(dp(4), dp(18), dp(4), dp(14))
        }
        val tabStyles = TextView(this@MainActivity).apply { text = "Styles"; textSize = 15f; setPadding(0, dp(4), dp(26), dp(4)) }
        val tabFonts = TextView(this@MainActivity).apply { text = "Fonts"; textSize = 15f; setPadding(0, dp(4), dp(26), dp(4)) }
        val tabColors = TextView(this@MainActivity).apply { text = "Colors"; textSize = 15f; setPadding(0, dp(4), dp(26), dp(4)) }
        tabRow.addView(tabStyles)
        tabRow.addView(tabFonts)
        tabRow.addView(tabColors)
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
                layoutParams = LinearLayout.LayoutParams(dp(106), dp(122)).apply {
                    setMargins(0, 0, dp(12), 0)
                }
                setPadding(dp(8), dp(8), dp(8), dp(8))

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
                        !isCustomMode && idx == 1 -> { text = "04\n37"; textSize = 20f; setLineSpacing(0f, 0.85f) }
                        !isCustomMode && idx == 2 -> { text = "4:37"; textSize = 20f; gravity = Gravity.START }
                        !isCustomMode && idx == 3 -> { text = "4:37  11/19"; textSize = 11.5f }
                        isCustomMode && idx == 0 -> { text = "4:37"; textSize = 34f }
                        isCustomMode && idx == 1 -> { text = "04\n37"; textSize = 26f; setLineSpacing(0f, 0.82f) }
                        else -> { text = "0 4\n3 7"; textSize = 25f; setLineSpacing(0f, 0.82f) }
                    }
                }

                if (!(!isCustomMode && idx == 3)) addView(dateTv)
                addView(timeTv)
            }
        }

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

            listOf(tabStyles, tabFonts, tabColors).forEachIndexed { idx, tv ->
                tv.setTextColor(if (idx == currentTab) Color.BLACK else Color.parseColor("#9E9E9E"))
                tv.setTypeface(null, if (idx == currentTab) Typeface.BOLD else Typeface.NORMAL)
            }

            contentHolder.removeAllViews()
            val hScroll = HorizontalScrollView(this@MainActivity).apply { isHorizontalScrollBarEnabled = false }
            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(2), dp(6), dp(2), dp(6))
            }

            when (currentTab) {
                0 -> {
                    val count = if (currentMode == 0) 4 else 3
                    for (idx in 0 until count) {
                        val card = buildStyleCard(idx, currentStyle == idx, currentMode == 1)
                        card.setOnClickListener {
                            currentStyle = idx
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
                    val fontCount = if (currentMode == 0) 5 else 2
                    for (idx in 0 until fontCount) {
                        val cardW = if (currentMode == 1) dp(156) else dp(78)
                        val cardH = if (currentMode == 1) dp(110) else dp(78)
                        val card = object : View(this@MainActivity) {
                            private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = Color.BLACK
                                textAlign = Paint.Align.CENTER
                            }
                            override fun onDraw(canvas: Canvas) {
                                super.onDraw(canvas)
                                p.style = Paint.Style.FILL
                                p.strokeWidth = 0f
                                p.textScaleX = if (currentMode == 1) 0.62f else 1.0f
                                p.textSize = if (currentMode == 1) dp(38).toFloat() else dp(28).toFloat()
                                when {
                                    currentMode == 0 && idx == 0 -> p.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                                    currentMode == 0 && idx == 1 -> p.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                                    currentMode == 0 && idx == 2 -> p.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                                    currentMode == 0 && idx == 3 -> p.typeface = Typeface.SERIF
                                    currentMode == 0 && idx == 4 -> {
                                        p.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                                        p.style = Paint.Style.STROKE
                                        p.strokeWidth = dp(2).toFloat()
                                    }
                                    currentMode == 1 && idx == 0 -> p.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
                                    else -> p.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
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

                    if (currentMode == 0) {
                        val sliderRow = LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding(dp(8), dp(22), dp(8), 0)
                        }
                        val smallA = TextView(this@MainActivity).apply { text = "A"; textSize = 14f; setTextColor(Color.parseColor("#555555")) }
                        val bigA = TextView(this@MainActivity).apply { text = "A"; textSize = 18f; setTextColor(Color.BLACK); setTypeface(null, Typeface.BOLD) }
                        val seek = SeekBar(this@MainActivity).apply {
                            max = 70
                            progress = ((currentSize - 0.7f) * 100).toInt().coerceIn(0, 70)
                            progressTintList = ColorStateList.valueOf(Color.parseColor("#1A73E8"))
                            thumbTintList = ColorStateList.valueOf(Color.parseColor("#1A73E8"))
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                                setMargins(dp(12), 0, dp(12), 0)
                            }
                            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                                    currentSize = 0.7f + (p / 100f)
                                    saveTempPrefs()
                                    previewView.invalidate()
                                }
                                override fun onStartTrackingTouch(s: SeekBar?) {}
                                override fun onStopTrackingTouch(s: SeekBar?) {}
                            })
                        }
                        sliderRow.addView(smallA)
                        sliderRow.addView(seek)
                        sliderRow.addView(bigA)
                        contentHolder.addView(sliderRow)
                    }
                }

                else -> {
                    val palette = listOf(
                        Color.WHITE,
                        -1,
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
                        Color.BLACK
                    )

                    palette.forEach { col ->
                        val swatch = object : View(this@MainActivity) {
                            private val p = Paint(Paint.ANTI_ALIAS_FLAG)
                            private val ringP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                style = Paint.Style.STROKE
                                strokeWidth = dp(2).toFloat()
                                color = Color.parseColor("#1A73E8")
                            }
                            override fun onDraw(canvas: Canvas) {
                                super.onDraw(canvas)
                                val cx = width / 2f
                                val cy = height / 2f
                                val r = width * 0.38f

                                if (col == Color.WHITE) {
                                    p.color = Color.WHITE
                                    canvas.drawCircle(cx, cy, r, p)
                                    p.color = Color.parseColor("#2B2B2B")
                                    canvas.drawArc(cx - r, cy - r, cx + r, cy + r, 45f, 180f, true, p)
                                    p.style = Paint.Style.STROKE
                                    p.strokeWidth = dp(1).toFloat()
                                    p.color = Color.parseColor("#BDBDBD")
                                    canvas.drawCircle(cx, cy, r, p)
                                    p.style = Paint.Style.FILL
                                } else if (col == -1) {
                                    p.shader = SweepGradient(
                                        cx, cy,
                                        intArrayOf(Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED),
                                        null
                                    )
                                    canvas.drawCircle(cx, cy, r, p)
                                    p.shader = null
                                } else {
                                    p.color = col
                                    canvas.drawCircle(cx, cy, r, p)
                                }

                                if (currentColor == col) {
                                    canvas.drawCircle(cx, cy, width * 0.46f, ringP)
                                }
                            }
                        }.apply {
                            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                                setMargins(0, dp(12), dp(12), dp(12))
                            }
                            setOnClickListener {
                                currentColor = col
                                saveTempPrefs()
                                previewView.invalidate()
                                updateUI()
                            }
                        }
                        row.addView(swatch)
                    }
                    hScroll.addView(row)
                    contentHolder.addView(hScroll)
                }
            }
        }

        previewView.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val h = v.height.toFloat()
                val clockTop = h * 0.08f
                val clockBottom = if (currentMode == 1) h * 0.54f else h * 0.42f

                if (!isPanelOpen && event.y in clockTop..clockBottom) {
                    isPanelOpen = true
                    topBarClosed.visibility = View.GONE
                    sheet.visibility = View.VISIBLE
                    previewView.invalidate()
                    updateUI()
                }
            }
            true
        }

        previewWrapper.addView(previewView)
        root.addView(previewWrapper)
        root.addView(sheet)

        btnClassic.setOnClickListener {
            currentMode = 0; currentStyle = 0; currentFont = 0
            saveTempPrefs(); previewView.invalidate(); updateUI()
        }
        btnCustom.setOnClickListener {
            currentMode = 1; currentStyle = 0; currentFont = 0
            saveTempPrefs(); previewView.invalidate(); updateUI()
        }
        tabStyles.setOnClickListener { currentTab = 0; updateUI() }
        tabFonts.setOnClickListener { currentTab = 1; updateUI() }
        tabColors.setOnClickListener { currentTab = 2; updateUI() }

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
        reindexPhotoFiles()
        val existingCount = getSavedPhotoFiles().size
        val remainingSlots = (6 - existingCount).coerceAtLeast(0)
        if (remainingSlots == 0) {
            Toast.makeText(this, "Maximum 6 photos allowed!", Toast.LENGTH_SHORT).show()
            return
        }

        val (screenW, screenH) = getRealScreenSize()
        var nextIndex = existingCount
        var added = 0

        for (uri in uris.take(remainingSlots)) {
            contentResolver.openInputStream(uri)?.use { input ->
                val rawBitmap = BitmapFactory.decodeStream(input)
                if (rawBitmap != null) {
                    val scaled = centerCropBitmap(rawBitmap, screenW, screenH)
                    val outFile = File(filesDir, "wp_$nextIndex.jpg")
                    FileOutputStream(outFile).use { out ->
                        scaled.compress(Bitmap.CompressFormat.JPEG, 92, out)
                    }
                    rawBitmap.recycle()
                    scaled.recycle()
                    nextIndex++
                    added++
                }
            }
        }

        refreshCardsStrip()
        Toast.makeText(this, "$added Photo(s) Added!", Toast.LENGTH_SHORT).show()
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