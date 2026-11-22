package com.origin.flip

import android.app.Dialog
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
    private lateinit var arrowView: TextView

    // EXPANDABLE TARGET SCREENS
    private lateinit var expandScreensPanel: LinearLayout
    private lateinit var lockPill: FrameLayout
    private lateinit var lockPillText: TextView
    private lateinit var lockPillKnob: View
    private lateinit var homePill: FrameLayout
    private lateinit var homePillText: TextView
    private lateinit var homePillKnob: View
    private lateinit var homeWpStatusText: TextView
    private var isScreensPanelOpen = false

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

    private val pickHomeWallpaper = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        bgExecutor.execute {
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    val (screenW, screenH) = getRealScreenSize()
                    val raw = BitmapFactory.decodeStream(input)
                    if (raw != null) {
                        val scaled = centerCropBitmap(raw, screenW, screenH)
                        val target = File(filesDir, "home_static_wp.jpg")
                        FileOutputStream(target).use { out ->
                            scaled.compress(Bitmap.CompressFormat.JPEG, 92, out)
                        }
                        raw.recycle()
                        scaled.recycle()
                        FlipWallpaperService.homeStaticBitmap?.recycle()
                        FlipWallpaperService.homeStaticBitmap = null
                    }
                }
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Home Screen Wallpaper Updated!", Toast.LENGTH_SHORT).show()
                    updateHomeWpStatusUI()
                }
            } catch (_: Exception) {}
        }
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

        // SMOOTH VERTICAL SCROLL VIEW FOR ENTIRE SCREEN
        val vScrollView = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(44), dp(20), dp(32))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        ViewCompat.setOnApplyWindowInsetsListener(vScrollView) { _, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            content.setPadding(dp(20), statusBars.top + dp(12), dp(20), dp(36))
            insets
        }

        val badge = TextView(this).apply {
            text = "ORIGIN OS 6 ENGINE"
            textSize = 10.5f
            setTextColor(Color.parseColor("#7EB4FF"))
            setTypeface(Typeface.create("sans-serif-bold", Typeface.BOLD))
            letterSpacing = 0.12f
            background = GradientDrawable().apply {
                setColor(Color.argb(55, 30, 90, 200))
                cornerRadius = dp(14).toFloat()
            }
            setPadding(dp(14), dp(5), dp(14), dp(5))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(10)
            layoutParams = lp
        }

        val title = TextView(this).apply {
            text = "Flip Cards Wallpaper"
            textSize = 28f
            setTextColor(Color.WHITE)
            setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD))
            letterSpacing = -0.02f
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(6)
            layoutParams = lp
        }

        val subtitle = TextView(this).apply {
            text = "Dynamic flip-effect wallpapers • Curated & ready"
            textSize = 13.5f
            setTextColor(Color.parseColor("#9AA4B2"))
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(24)
            layoutParams = lp
        }

        val hScrollCards = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(250)
            ).apply { setMargins(0, 0, 0, dp(24)) }
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

        // 1. MAIN APPLY ORIGIN CARD CONTAINER
        val applyCardContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(14)) }
        }

        val btnApplyCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#29B6F6"), Color.parseColor("#1976D2"))
            ).apply {
                cornerRadius = dp(24).toFloat()
            }
            setPadding(dp(18), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(72)
            )
        }

        // Left Click Section: Arrow + Title (NO FARZI SUBTITLE TEXT!)
        val applyLeftSection = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            isClickable = true
            isFocusable = true
        }

        arrowView = TextView(this).apply {
            text = "▼"
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(0, 0, dp(12), 0)
        }

        val applyTitle = TextView(this).apply {
            text = "Apply Origin Flip Wallpaper"
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        applyLeftSection.addView(arrowView)
        applyLeftSection.addView(applyTitle)

        // Arrow toggles dropdown
        arrowView.setOnClickListener {
            toggleScreensDropdown()
        }

        // Tapping card title applies directly with NO POPUP!
        applyTitle.setOnClickListener {
            applyWallpaperDirectly()
        }

        btnApplyCard.setOnClickListener {
            applyWallpaperDirectly()
        }

        // MASTER ON/OFF PILL SWITCH
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

        btnApplyCard.addView(applyLeftSection)
        btnApplyCard.addView(applyTogglePill)
        applyCardContainer.addView(btnApplyCard)

        // EXPANDABLE TARGET SCREENS PANEL (PINK & GREEN SWITCHES)
        expandScreensPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#151D2A"))
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), Color.parseColor("#26354A"))
            }
            setPadding(dp(18), dp(16), dp(18), dp(16))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(8), 0, 0) }
        }

        val targetHeader = TextView(this).apply {
            text = "TARGET SCREENS (SELECT WHERE TO RUN FLIP)"
            textSize = 10.5f
            setTextColor(Color.parseColor("#7E8B9B"))
            setTypeface(Typeface.DEFAULT_BOLD)
            letterSpacing = 0.05f
            setPadding(0, 0, 0, dp(10))
        }
        expandScreensPanel.addView(targetHeader)

        // 1. LOCK SCREEN OPTION (PINK SWITCH)
        val lockRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(6))
        }

        val lockTextCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val lockTitle = TextView(this).apply {
            text = "🔒 Lock Screen"
            textSize = 15f
            setTextColor(Color.WHITE)
            setTypeface(Typeface.DEFAULT_BOLD)
        }

        val lockSubtitle = TextView(this).apply {
            text = "3D Flip & Clock on lock screen"
            textSize = 11.5f
            setTextColor(Color.parseColor("#94A3B8"))
        }

        lockTextCol.addView(lockTitle)
        lockTextCol.addView(lockSubtitle)

        lockPill = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(pillW, pillH)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }

        lockPillText = TextView(this).apply {
            textSize = 12f
            setTypeface(Typeface.create("sans-serif-bold", Typeface.BOLD))
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }

        lockPillKnob = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
            }
            elevation = dp(3).toFloat()
            layoutParams = FrameLayout.LayoutParams(knobSize, knobSize, Gravity.CENTER_VERTICAL)
        }

        lockPill.addView(lockPillText)
        lockPill.addView(lockPillKnob)

        lockPill.setOnClickListener {
            val curLock = prefs.getBoolean("apply_lock_screen", true)
            val curHome = prefs.getBoolean("apply_home_screen", true)
            val newLock = !curLock

            if (!newLock && !curHome) {
                Toast.makeText(this@MainActivity, "At least one screen must be selected", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.edit().putBoolean("apply_lock_screen", newLock).apply()
            updateTargetMode()
            updateLockPillUI(newLock)
            updateFooterText()
        }

        lockRow.addView(lockTextCol)
        lockRow.addView(lockPill)
        expandScreensPanel.addView(lockRow)

        val rowDivider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
                setMargins(0, dp(8), 0, dp(8))
            }
            setBackgroundColor(Color.parseColor("#26354A"))
        }
        expandScreensPanel.addView(rowDivider)

        // 2. HOME SCREEN OPTION (GREEN SWITCH)
        val homeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }

        val homeTextCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val homeTitle = TextView(this).apply {
            text = "📱 Home Screen"
            textSize = 15f
            setTextColor(Color.WHITE)
            setTypeface(Typeface.DEFAULT_BOLD)
        }

        val homeSubtitle = TextView(this).apply {
            text = "3D Flip & Clock on home screen"
            textSize = 11.5f
            setTextColor(Color.parseColor("#94A3B8"))
        }

        homeTextCol.addView(homeTitle)
        homeTextCol.addView(homeSubtitle)

        homePill = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(pillW, pillH)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }

        homePillText = TextView(this).apply {
            textSize = 12f
            setTypeface(Typeface.create("sans-serif-bold", Typeface.BOLD))
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }

        homePillKnob = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
            }
            elevation = dp(3).toFloat()
            layoutParams = FrameLayout.LayoutParams(knobSize, knobSize, Gravity.CENTER_VERTICAL)
        }

        homePill.addView(homePillText)
        homePill.addView(homePillKnob)

        homePill.setOnClickListener {
            val curLock = prefs.getBoolean("apply_lock_screen", true)
            val curHome = prefs.getBoolean("apply_home_screen", true)
            val newHome = !curHome

            if (!newHome && !curLock) {
                Toast.makeText(this@MainActivity, "At least one screen must be selected", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.edit().putBoolean("apply_home_screen", newHome).apply()
            updateTargetMode()
            updateHomePillUI(newHome)
            updateFooterText()
        }

        homeRow.addView(homeTextCol)
        homeRow.addView(homePill)
        expandScreensPanel.addView(homeRow)

        // CHOOSE CUSTOM HOME SCREEN WALLPAPER BUTTON
        val homeWpCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = dp(14).toFloat()
            }
            setPadding(dp(14), dp(10), dp(14), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(10), 0, 0) }
            setOnClickListener {
                pickHomeWallpaper.launch("image/*")
            }
        }

        val homeWpBtnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val homeWpBtnTitle = TextView(this).apply {
            text = "🖼️ Choose Home Wallpaper (From Gallery)"
            textSize = 12.5f
            setTextColor(Color.parseColor("#60A5FA"))
            setTypeface(Typeface.DEFAULT_BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val homeWpArrow = TextView(this).apply {
            text = "›"
            textSize = 18f
            setTextColor(Color.parseColor("#60A5FA"))
        }

        homeWpBtnRow.addView(homeWpBtnTitle)
        homeWpBtnRow.addView(homeWpArrow)

        homeWpStatusText = TextView(this).apply {
            textSize = 11f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, dp(2), 0, 0)
        }
        updateHomeWpStatusUI()

        homeWpCard.addView(homeWpBtnRow)
        homeWpCard.addView(homeWpStatusText)
        expandScreensPanel.addView(homeWpCard)

        applyCardContainer.addView(expandScreensPanel)

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

        val lockActive = prefs.getBoolean("apply_lock_screen", true)
        val homeActive = prefs.getBoolean("apply_home_screen", true)
        updateLockPillUI(lockActive)
        updateHomePillUI(homeActive)

        footerStatus = TextView(this).apply {
            text = "Active  •  OriginOS Seamless Flip  •  Ready to apply"
            textSize = 13f
            setTextColor(Color.parseColor("#4FC3F7"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(24))
        }

        refreshCardsStrip()

        content.addView(badge)
        content.addView(title)
        content.addView(subtitle)
        content.addView(hScrollCards)
        content.addView(applyCardContainer)
        content.addView(btnCustomizeCard)
        content.addView(footerStatus)

        vScrollView.addView(content)
        root.addView(vScrollView)
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
        if (::lockPill.isInitialized) {
            updateLockPillUI(prefs.getBoolean("apply_lock_screen", true))
        }
        if (::homePill.isInitialized) {
            updateHomePillUI(prefs.getBoolean("apply_home_screen", true))
        }
        updateHomeWpStatusUI()
        updateFooterText()
    }

    private fun updateHomeWpStatusUI() {
        if (!::homeWpStatusText.isInitialized) return
        val customFile = File(filesDir, "home_static_wp.jpg")
        homeWpStatusText.text = if (customFile.exists()) {
            "✓ Custom wallpaper active for Home Screen"
        } else {
            "Default: Uses first flip photo when unlocked"
        }
    }

    private fun toggleScreensDropdown() {
        isScreensPanelOpen = !isScreensPanelOpen
        arrowView.text = if (isScreensPanelOpen) "▲" else "▼"
        expandScreensPanel.visibility = if (isScreensPanelOpen) View.VISIBLE else View.GONE
    }

    private fun updateTargetMode() {
        val prefs = getSharedPreferences("origin_prefs", Context.MODE_PRIVATE)
        val lockOn = prefs.getBoolean("apply_lock_screen", true)
        val homeOn = prefs.getBoolean("apply_home_screen", true)
        val targetMode = when {
            lockOn && homeOn -> "both"
            lockOn -> "lock_only"
            homeOn -> "home_only"
            else -> "both"
        }
        prefs.edit().putString("apply_mode", targetMode).apply()
    }

    // DIRECT FAST APPLY - ZERO BLACK SCREEN, PRE-CACHED IN RAM
    private fun applyWallpaperDirectly() {
        val cardPhotos = getCardPhotos()
        if (cardPhotos.size < 2) {
            Toast.makeText(this@MainActivity, "select minimum two photo", Toast.LENGTH_SHORT).show()
            return
        }

        updateTargetMode()

        val prefs = getSharedPreferences("origin_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("flip_toggle_active", true)
            .putBoolean("wallpaper_paused", false)
            .apply()

        updateApplyToggleUI(true)
        updateFooterText()

        // Fast RAM Pre-Cache before launching chooser
        bgExecutor.execute {
            try {
                if (FlipWallpaperService.cachedBitmaps.isEmpty()) {
                    val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
                    val list = mutableListOf<Bitmap>()
                    for (f in cardPhotos) {
                        val b = BitmapFactory.decodeFile(f.absolutePath, opts)
                        if (b != null) list.add(b)
                    }
                    FlipWallpaperService.cachedBitmaps.clear()
                    FlipWallpaperService.cachedBitmaps.addAll(list)
                }
            } catch (_: Exception) {}
        }

        launchLiveWallpaperChooser()
    }

    private fun launchLiveWallpaperChooser() {
        try {
            val intent = Intent(android.app.WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(
                    android.app.WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(this@MainActivity, FlipWallpaperService::class.java)
                )
            }
            startActivity(intent)
        } catch (_: Throwable) {
            try {
                startActivity(Intent(android.app.WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
            } catch (_: Throwable) {
                startActivity(Intent(Intent.ACTION_SET_WALLPAPER))
            }
        }
    }

    private fun updateLockPillUI(isActive: Boolean) {
        val knobParams = lockPillKnob.layoutParams as FrameLayout.LayoutParams
        if (isActive) {
            lockPill.background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#EC4899"), Color.parseColor("#F43F5E"))
            ).apply {
                cornerRadius = dp(20).toFloat()
            }
            lockPillText.text = "ON"
            lockPillText.setTextColor(Color.WHITE)
            lockPillText.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            lockPillText.setPadding(dp(10), 0, 0, 0)
            knobParams.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        } else {
            lockPill.background = GradientDrawable().apply {
                setColor(Color.parseColor("#374151"))
                cornerRadius = dp(20).toFloat()
            }
            lockPillText.text = "OFF"
            lockPillText.setTextColor(Color.parseColor("#9CA3AF"))
            lockPillText.gravity = Gravity.END or Gravity.CENTER_VERTICAL
            lockPillText.setPadding(0, 0, dp(9), 0)
            knobParams.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }
        lockPillKnob.layoutParams = knobParams
    }

    private fun updateHomePillUI(isActive: Boolean) {
        val knobParams = homePillKnob.layoutParams as FrameLayout.LayoutParams
        if (isActive) {
            homePill.background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#10B981"), Color.parseColor("#22C55E"))
            ).apply {
                cornerRadius = dp(20).toFloat()
            }
            homePillText.text = "ON"
            homePillText.setTextColor(Color.WHITE)
            homePillText.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            homePillText.setPadding(dp(10), 0, 0, 0)
            knobParams.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        } else {
            homePill.background = GradientDrawable().apply {
                setColor(Color.parseColor("#374151"))
                cornerRadius = dp(20).toFloat()
            }
            homePillText.text = "OFF"
            homePillText.setTextColor(Color.parseColor("#9CA3AF"))
            homePillText.gravity = Gravity.END or Gravity.CENTER_VERTICAL
            homePillText.setPadding(0, 0, dp(9), 0)
            knobParams.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }
        homePillKnob.layoutParams = knobParams
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
        val prefs = getSharedPreferences("origin_prefs", Context.MODE_PRIVATE)
        val active = prefs.getBoolean("flip_toggle_active", false)
        val mode = prefs.getString("apply_mode", "both")
        val count = getCardPhotos().size
        footerStatus.text = when {
            !active -> "OFF  •  System Wallpaper Mode"
            count < 2 -> "Select minimum two photo"
            mode == "lock_only" -> "Active  •  Only Lock Screen Mode"
            mode == "home_only" -> "Active  •  Only Home Screen Mode"
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

        if (bestScore > 0f) {
            val hsl = FloatArray(3)
            androidx.core.graphics.ColorUtils.colorToHSL(bestColor, hsl)
            hsl[2] = hsl[2].coerceIn(0.55f, 0.88f)
            return androidx.core.graphics.ColorUtils.HSLToColor(hsl)
        }

        val avgR = (rSum / count).toInt().coerceIn(0, 255)
        val avgG = (gSum / count).toInt().coerceIn(0, 255)
        val avgB = (bSum / count).toInt().coerceIn(0, 255)
        val avgColor = Color.rgb(avgR, avgG, avgB)

        return if (isLightColor(avgColor)) {
            Color.parseColor("#1B2A4A")
        } else {
            Color.parseColor("#DCEBFF")
        }
    }

    private fun buildSubjectCutoutBitmap(source: Bitmap): Bitmap? {
        val w = source.width
        val h = source.height
        val cutout = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

        val srcPixels = IntArray(w * h)
        val outPixels = IntArray(w * h)
        source.getPixels(srcPixels, 0, w, 0, 0, w, h)

        var borderR = 0L
        var borderG = 0L
        var borderB = 0L
        var borderCount = 0

        for (x in 0 until w step 2) {
            val topPx = srcPixels[x]
            borderR += (topPx shr 16) and 0xFF
            borderG += (topPx shr 8) and 0xFF
            borderB += topPx and 0xFF
            borderCount++

            val btmPx = srcPixels[(h - 1) * w + x]
            borderR += (btmPx shr 16) and 0xFF
            borderG += (btmPx shr 8) and 0xFF
            borderB += btmPx and 0xFF
            borderCount++
        }

        for (y in 0 until h step 2) {
            val lftPx = srcPixels[y * w]
            borderR += (lftPx shr 16) and 0xFF
            borderG += (lftPx shr 8) and 0xFF
            borderB += lftPx and 0xFF
            borderCount++

            val rgtPx = srcPixels[y * w + (w - 1)]
            borderR += (rgtPx shr 16) and 0xFF
            borderG += (rgtPx shr 8) and 0xFF
            borderB += rgtPx and 0xFF
            borderCount++
        }

        val bgR = if (borderCount > 0) (borderR / borderCount).toFloat() else 128f
        val bgG = if (borderCount > 0) (borderG / borderCount).toFloat() else 128f
        val bgB = if (borderCount > 0) (borderB / borderCount).toFloat() else 128f

        val cx = w * 0.5f
        val cy = h * 0.38f
        val maxR = sqrt(cx * cx + cy * cy)

        for (y in 0 until h) {
            val yOffset = y * w
            for (x in 0 until w) {
                val idx = yOffset + x
                val c = srcPixels[idx]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF

                val dr = r - bgR
                val dg = g - bgG
                val db = b - bgB
                val colorDist = sqrt(dr * dr + dg * dg + db * db)

                val dx = x - cx
                val dy = y - cy
                val spatialWeight = (1f - (sqrt(dx * dx + dy * dy) / maxR)).coerceIn(0.1f, 1f)
                val subjectScore = colorDist * spatialWeight

                val alpha = when {
                    subjectScore > 48f -> 255
                    subjectScore > 24f -> (((subjectScore - 24f) / 24f) * 255f).toInt()
                    else -> 0
                }
                outPixels[idx] = (alpha shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        cutout.setPixels(outPixels, 0, w, 0, 0, w, h)
        return cutout
    }

    private fun precomputeWallpaperCaches() {
        if (isPrecomputing) return
        isPrecomputing = true
        try {
            val files = getCardPhotos()
            if (files.isEmpty()) return

            val adaptiveColors = mutableListOf<Int>()
            files.forEachIndexed { idx, file ->
                var bmp: Bitmap? = null
                try {
                    bmp = BitmapFactory.decodeFile(file.absolutePath)
                    if (bmp != null) {
                        val color = extractAdaptiveColorFromBitmap(bmp)
                        adaptiveColors.add(color)

                        val depthFile = File(filesDir, "depth_$idx.png")
                        if (!depthFile.exists()) {
                            val cutout = buildSubjectCutoutBitmap(bmp)
                            if (cutout != null) {
                                FileOutputStream(depthFile).use { out ->
                                    cutout.compress(Bitmap.CompressFormat.PNG, 100, out)
                                }
                                cutout.recycle()
                            }
                        }
                    }
                } catch (_: Exception) {
                } finally {
                    bmp?.recycle()
                }
            }

            val csv = adaptiveColors.joinToString(",")
            val prefs = getSharedPreferences("origin_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("adaptive_colors_csv", csv).apply()

            if (adaptiveColors.isNotEmpty()) {
                prefs.edit().putInt("adaptive_clock_color", adaptiveColors[0]).apply()
            }
        } finally {
            isPrecomputing = false
        }
    }

    private fun refreshCardsStrip() {
        cardsRow.removeAllViews()
        val photos = getCardPhotos()

        photos.forEachIndexed { index, file ->
            val cardFrame = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(130), dp(220)).apply {
                    setMargins(0, 0, dp(14), 0)
                }
            }

            val imgCard = ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = GradientDrawable().apply {
                    cornerRadius = dp(20).toFloat()
                    setColor(Color.parseColor("#1B2234"))
                }
                clipToOutline = true
            }

            try {
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                val bmp = BitmapFactory.decodeFile(file.absolutePath, opts)
                imgCard.setImageBitmap(bmp)
            } catch (_: Exception) {}

            val closeBtn = TextView(this).apply {
                text = "✕"
                textSize = 12f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.argb(175, 20, 20, 20))
                }
                val btnSize = dp(28)
                layoutParams = FrameLayout.LayoutParams(btnSize, btnSize, Gravity.TOP or Gravity.END).apply {
                    setMargins(0, dp(8), dp(8), 0)
                }
                setOnClickListener {
                    removePhotoAt(index)
                }
            }

            cardFrame.addView(imgCard)
            cardFrame.addView(closeBtn)
            cardsRow.addView(cardFrame)
        }

        if (photos.size < 6) {
            val addFrame = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(dp(130), dp(220)).apply {
                    setMargins(0, 0, dp(14), 0)
                }
                background = GradientDrawable().apply {
                    cornerRadius = dp(20).toFloat()
                    setColor(Color.argb(40, 255, 255, 255))
                    setStroke(dp(1), Color.argb(80, 255, 255, 255), dp(4).toFloat(), dp(4).toFloat())
                }
                setOnClickListener {
                    pickImages.launch(arrayOf("image/*"))
                }
            }

            val plus = TextView(this).apply {
                text = "+"
                textSize = 34f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            }
            val addText = TextView(this).apply {
                text = "Add Photo"
                textSize = 12.5f
                setTextColor(Color.parseColor("#A0ABC0"))
            }

            addFrame.addView(plus)
            addFrame.addView(addText)
            cardsRow.addView(addFrame)
        }

        updateFooterText()
        bgExecutor.execute {
            precomputeWallpaperCaches()
        }
    }

    // CLOCK CUSTOMIZATION POPUP
    private fun openCustomizationDialog() {
        val prefs = getSharedPreferences("origin_prefs", Context.MODE_PRIVATE)

        var currentMode = prefs.getInt("clock_mode", 0)
        var currentStyle = prefs.getInt("clock_style", 0)
        var currentFont = prefs.getInt("clock_font", 0)
        var currentWeight = prefs.getFloat("clock_weight", 0.72f)
        var currentCustomSize = prefs.getFloat("clock_custom_size", 1.35f)
        var currentColor = prefs.getInt("clock_color", ClockDrawer.COLOR_ADAPTIVE)
        var isDepthEnabled = prefs.getBoolean("depth_effect_enabled", false)
        var offsetX = prefs.getFloat("clock_offset_x", 0f)
        var offsetY = prefs.getFloat("clock_offset_y", 0f)

        val adaptiveCol = prefs.getInt("adaptive_clock_color", Color.WHITE)

        var isPanelOpen = false
        var currentTab = 0
        var isHsvPickerOpen = false
        var pickerHue = 200f
        var pickerSat = 0.8f
        var pickerVal = 0.9f

        var isDraggingHandle = false
        var dragStartY = 0f
        var dragStartSize = 1.35f
        var activeHandleBoxBottom = 0f

        var isDraggingClockPos = false
        var dragStartX = 0f
        var dragStartYPos = 0f
        var dragStartOffsetX = 0f
        var dragStartOffsetY = 0f

        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val root = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
        }

        val previewWrapper = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        var sampleBmp: Bitmap? = null
        val photos = getCardPhotos()
        if (photos.isNotEmpty()) {
            try {
                sampleBmp = BitmapFactory.decodeFile(photos[0].absolutePath)
            } catch (_: Exception) {}
        }
        val depthFile0 = File(filesDir, "depth_0.png")
        val sampleDepth: Bitmap? = if (depthFile0.exists()) BitmapFactory.decodeFile(depthFile0.absolutePath) else null

        val previewView = object : View(this) {
            private val pBmp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            private val pDepth = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = Color.parseColor("#40C4FF")
                strokeWidth = dpF(1.8f)
                pathEffect = DashPathEffect(floatArrayOf(dpF(6f), dpF(5f)), 0f)
            }
            private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.parseColor("#00E5FF")
            }
            private val handleInner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.WHITE
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val w = width.toFloat()
                val h = height.toFloat()

                sampleBmp?.let {
                    canvas.drawBitmap(it, null, RectF(0f, 0f, w, h), pBmp)
                } ?: run {
                    val p = Paint().apply {
                        shader = LinearGradient(0f, 0f, 0f, h, Color.parseColor("#152039"), Color.parseColor("#070A12"), Shader.TileMode.CLAMP)
                    }
                    canvas.drawRect(0f, 0f, w, h, p)
                }

                ClockDrawer.drawClock(canvas, prefs, w, h, adaptiveCol)

                if (isDepthEnabled && sampleDepth != null) {
                    canvas.drawBitmap(sampleDepth, null, RectF(0f, 0f, w, h), pDepth)
                }

                if (isPanelOpen && currentMode == 1 && currentTab == 1) {
                    val boxW = w * 0.88f
                    val boxH = (h * 0.22f * currentCustomSize).coerceIn(h * 0.16f, h * 0.54f)
                    val boxLeft = (w - boxW) / 2f
                    val boxTop = (h * (0.155f + offsetY)).coerceIn(h * 0.08f, h * 0.65f) - dpF(12f)
                    val boxBottom = boxTop + boxH
                    activeHandleBoxBottom = boxBottom

                    val r = RectF(boxLeft, boxTop, boxLeft + boxW, boxBottom)
                    canvas.drawRoundRect(r, dpF(16f), dpF(16f), boxPaint)

                    val hSize = dpF(20f)
                    val cx = w * 0.5f
                    canvas.drawRoundRect(RectF(cx - hSize, boxBottom - dpF(8f), cx + hSize, boxBottom + dpF(8f)), dpF(8f), dpF(8f), handlePaint)
                    canvas.drawRoundRect(RectF(cx - hSize * 0.45f, boxBottom - dpF(2f), cx + hSize * 0.45f, boxBottom + dpF(2f)), dpF(2f), dpF(2f), handleInner)
                }
            }
        }

        val topBarClosed = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(36), dp(16), dp(12))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
        }

        val btnBackTop = TextView(this).apply {
            text = "✕"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(150, 20, 20, 20))
            }
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(38))
            setOnClickListener {
                dialog.dismiss()
            }
        }

        val tapHint = TextView(this).apply {
            text = "Tap clock to edit style & depth"
            textSize = 13f
            setTextColor(Color.parseColor("#E0E0E0"))
            background = GradientDrawable().apply {
                setColor(Color.argb(160, 10, 15, 25))
                cornerRadius = dp(16).toFloat()
            }
            setPadding(dp(14), dp(8), dp(14), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(16), 0, 0, 0) }
        }

        topBarClosed.addView(btnBackTop)
        topBarClosed.addView(tapHint)
        previewWrapper.addView(topBarClosed)

        // BOTTOM EDITING SHEET
        val sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadii = floatArrayOf(
                    dpF(28f), dpF(28f), dpF(28f), dpF(28f),
                    0f, 0f, 0f, 0f
                )
            }
            setPadding(dp(18), dp(14), dp(18), dp(22))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        }

        val sheetHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(10)) }
        }

        val modeSwitchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F1F3F4"))
                cornerRadius = dp(16).toFloat()
            }
            setPadding(dp(3), dp(3), dp(3), dp(3))
            layoutParams = LinearLayout.LayoutParams(0, dp(34), 1f)
        }

        val btnClassic = TextView(this).apply {
            text = "Classic"
            textSize = 13f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        }

        val btnCustom = TextView(this).apply {
            text = "Custom"
            textSize = 13f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        }

        modeSwitchRow.addView(btnClassic)
        modeSwitchRow.addView(btnCustom)

        val btnDone = TextView(this).apply {
            text = "Done"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1A73E8"))
            setPadding(dp(16), dp(6), dp(4), dp(6))
            setOnClickListener {
                isPanelOpen = false
                topBarClosed.visibility = View.VISIBLE
                sheet.visibility = View.GONE
                previewView.invalidate()
            }
        }

        sheetHeader.addView(modeSwitchRow)
        sheetHeader.addView(btnDone)

        val tabStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(34)
            ).apply { setMargins(0, 0, 0, dp(12)) }
        }

        fun makeTab(label: String): TextView = TextView(this).apply {
            text = label
            textSize = 13f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        }

        val tabStyles = makeTab("Style")
        val tabFonts = makeTab("Font & Size")
        val tabColors = makeTab("Color")
        val tabDepth = makeTab("Depth")

        tabStrip.addView(tabStyles)
        tabStrip.addView(tabFonts)
        tabStrip.addView(tabColors)
        tabStrip.addView(tabDepth)

        val contentHolder = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(142)
            )
        }

        sheet.addView(sheetHeader)
        sheet.addView(tabStrip)
        sheet.addView(contentHolder)

        fun saveTempPrefs() {
            prefs.edit()
                .putInt("clock_mode", currentMode)
                .putInt("clock_style", currentStyle)
                .putInt("clock_font", currentFont)
                .putFloat("clock_weight", currentWeight)
                .putFloat("clock_custom_size", currentCustomSize)
                .putInt("clock_color", currentColor)
                .putBoolean("depth_effect_enabled", isDepthEnabled)
                .putFloat("clock_offset_x", offsetX)
                .putFloat("clock_offset_y", offsetY)
                .apply()
        }

        fun updateUI() {
            if (currentMode == 0) {
                btnClassic.background = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = dp(14).toFloat()
                }
                btnClassic.setTextColor(Color.parseColor("#1A73E8"))
                btnClassic.setTypeface(null, Typeface.BOLD)

                btnCustom.background = null
                btnCustom.setTextColor(Color.parseColor("#5F6368"))
                btnCustom.setTypeface(null, Typeface.NORMAL)
            } else {
                btnCustom.background = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = dp(14).toFloat()
                }
                btnCustom.setTextColor(Color.parseColor("#1A73E8"))
                btnCustom.setTypeface(null, Typeface.BOLD)

                btnClassic.background = null
                btnClassic.setTextColor(Color.parseColor("#5F6368"))
                btnClassic.setTypeface(null, Typeface.NORMAL)
            }

            val tabs = listOf(tabStyles, tabFonts, tabColors, tabDepth)
            tabs.forEachIndexed { i, t ->
                if (i == currentTab) {
                    t.setTextColor(Color.BLACK)
                    t.setTypeface(null, Typeface.BOLD)
                } else {
                    t.setTextColor(Color.parseColor("#70757A"))
                    t.setTypeface(null, Typeface.NORMAL)
                }
            }

            contentHolder.removeAllViews()

            when (currentTab) {
                0 -> {
                    val hScroll = HorizontalScrollView(this@MainActivity).apply {
                        isHorizontalScrollBarEnabled = false
                    }
                    val row = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    }

                    val maxStyles = if (currentMode == 0) 4 else 3
                    val styleNames = if (currentMode == 0) {
                        listOf("Stacked", "Double Line", "Left Align", "Compact")
                    } else {
                        listOf("Origin Big", "Two Tier", "Offset Split")
                    }

                    for (s in 0 until maxStyles) {
                        val card = LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            gravity = Gravity.CENTER
                            val isSel = (s == currentStyle)
                            background = GradientDrawable().apply {
                                setColor(if (isSel) Color.parseColor("#E8F0FE") else Color.parseColor("#F8F9FA"))
                                cornerRadius = dp(16).toFloat()
                                if (isSel) setStroke(dp(2), Color.parseColor("#1A73E8"))
                            }
                            layoutParams = LinearLayout.LayoutParams(dp(96), dp(112)).apply {
                                setMargins(0, 0, dp(10), 0)
                            }
                            setOnClickListener {
                                currentStyle = s
                                saveTempPrefs()
                                previewView.invalidate()
                                updateUI()
                            }
                        }

                        val lbl = TextView(this@MainActivity).apply {
                            text = styleNames.getOrElse(s) { "Style $s" }
                            textSize = 12f
                            gravity = Gravity.CENTER
                            setTextColor(if (s == currentStyle) Color.parseColor("#1A73E8") else Color.BLACK)
                            setTypeface(null, if (s == currentStyle) Typeface.BOLD else Typeface.NORMAL)
                        }
                        card.addView(lbl)
                        row.addView(card)
                    }
                    hScroll.addView(row)
                    contentHolder.addView(hScroll)
                }

                1 -> {
                    val col = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.VERTICAL
                    }

                    val fontScroll = HorizontalScrollView(this@MainActivity).apply {
                        isHorizontalScrollBarEnabled = false
                    }
                    val fontRow = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                    }

                    val fontNames = listOf("Modern", "Slim", "Bold", "Serif", "Outline", "Round")
                    for (f in 0..5) {
                        val fCard = TextView(this@MainActivity).apply {
                            text = fontNames[f]
                            textSize = 13f
                            gravity = Gravity.CENTER
                            val isSel = (f == currentFont)
                            background = GradientDrawable().apply {
                                setColor(if (isSel) Color.parseColor("#1A73E8") else Color.parseColor("#F1F3F4"))
                                cornerRadius = dp(12).toFloat()
                            }
                            setTextColor(if (isSel) Color.WHITE else Color.BLACK)
                            setTypeface(ClockDrawer.resolveTypeface(f, currentWeight), if (isSel) Typeface.BOLD else Typeface.NORMAL)
                            setPadding(dp(14), dp(8), dp(14), dp(8))
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply { setMargins(0, 0, dp(8), 0) }
                            setOnClickListener {
                                currentFont = f
                                saveTempPrefs()
                                previewView.invalidate()
                                updateUI()
                            }
                        }
                        fontRow.addView(fCard)
                    }
                    fontScroll.addView(fontRow)
                    col.addView(fontScroll)

                    val weightRow = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(0, dp(12), 0, 0)
                    }

                    val wLbl = TextView(this@MainActivity).apply {
                        text = "Weight"
                        textSize = 12f
                        setTextColor(Color.parseColor("#5F6368"))
                        layoutParams = LinearLayout.LayoutParams(dp(54), LinearLayout.LayoutParams.WRAP_CONTENT)
                    }

                    val wSeek = SeekBar(this@MainActivity).apply {
                        max = 100
                        progress = ((currentWeight - 0.25f) / (1.0f - 0.25f) * 100).toInt().coerceIn(0, 100)
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                            override fun onProgressChanged(sb: SeekBar?, prog: Int, fromUser: Boolean) {
                                if (fromUser) {
                                    currentWeight = 0.25f + (prog / 100f) * 0.75f
                                    saveTempPrefs()
                                    previewView.invalidate()
                                }
                            }
                            override fun onStartTrackingTouch(sb: SeekBar?) {}
                            override fun onStopTrackingTouch(sb: SeekBar?) {}
                        })
                    }

                    weightRow.addView(wLbl)
                    weightRow.addView(wSeek)
                    col.addView(weightRow)

                    if (currentMode == 1) {
                        val hint = TextView(this@MainActivity).apply {
                            text = "Tip: Pinch on clock to resize & drag to reposition"
                            textSize = 11.5f
                            setTextColor(Color.parseColor("#1A73E8"))
                            setPadding(0, dp(6), 0, 0)
                        }
                        col.addView(hint)
                    }

                    contentHolder.addView(col)
                }

                2 -> {
                    if (isHsvPickerOpen) {
                        val pickerCol = LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.VERTICAL
                        }

                        val hRow = LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                        }
                        val hLbl = TextView(this@MainActivity).apply {
                            text = "Hue"
                            textSize = 11.5f
                            layoutParams = LinearLayout.LayoutParams(dp(44), LinearLayout.LayoutParams.WRAP_CONTENT)
                        }
                        val hSeek = SeekBar(this@MainActivity).apply {
                            max = 360
                            progress = pickerHue.toInt()
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                                override fun onProgressChanged(sb: SeekBar?, prog: Int, fromUser: Boolean) {
                                    if (fromUser) {
                                        pickerHue = prog.toFloat()
                                        currentColor = Color.HSVToColor(floatArrayOf(pickerHue, pickerSat, pickerVal))
                                        saveTempPrefs()
                                        previewView.invalidate()
                                    }
                                }
                                override fun onStartTrackingTouch(sb: SeekBar?) {}
                                override fun onStopTrackingTouch(sb: SeekBar?) {}
                            })
                        }
                        hRow.addView(hLbl)
                        hRow.addView(hSeek)

                        val bRow = LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding(0, dp(6), 0, 0)
                        }
                        val bLbl = TextView(this@MainActivity).apply {
                            text = "Light"
                            textSize = 11.5f
                            layoutParams = LinearLayout.LayoutParams(dp(44), LinearLayout.LayoutParams.WRAP_CONTENT)
                        }
                        val bSeek = SeekBar(this@MainActivity).apply {
                            max = 100
                            progress = (pickerVal * 100).toInt()
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                                override fun onProgressChanged(sb: SeekBar?, prog: Int, fromUser: Boolean) {
                                    if (fromUser) {
                                        pickerVal = (prog / 100f).coerceIn(0.2f, 1f)
                                        currentColor = Color.HSVToColor(floatArrayOf(pickerHue, pickerSat, pickerVal))
                                        saveTempPrefs()
                                        previewView.invalidate()
                                    }
                                }
                                override fun onStartTrackingTouch(sb: SeekBar?) {}
                                override fun onStopTrackingTouch(sb: SeekBar?) {}
                            })
                        }
                        bRow.addView(bLbl)
                        bRow.addView(bSeek)

                        val donePick = TextView(this@MainActivity).apply {
                            text = "Back to Presets"
                            textSize = 12f
                            setTextColor(Color.parseColor("#1A73E8"))
                            setTypeface(null, Typeface.BOLD)
                            setPadding(0, dp(8), 0, 0)
                            setOnClickListener {
                                isHsvPickerOpen = false
                                updateUI()
                            }
                        }

                        pickerCol.addView(hRow)
                        pickerCol.addView(bRow)
                        pickerCol.addView(donePick)
                        contentHolder.addView(pickerCol)
                    } else {
                        val hScroll = HorizontalScrollView(this@MainActivity).apply {
                            isHorizontalScrollBarEnabled = false
                        }
                        val row = LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                        }

                        val adaptiveCircle = FrameLayout(this@MainActivity).apply {
                            val isSel = (currentColor == ClockDrawer.COLOR_ADAPTIVE)
                            background = GradientDrawable().apply {
                                shape = GradientDrawable.OVAL
                                setColor(adaptiveCol)
                                if (isSel) setStroke(dp(3), Color.parseColor("#1A73E8"))
                            }
                            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                                setMargins(0, 0, dp(12), 0)
                            }
                            val tag = TextView(this@MainActivity).apply {
                                text = "A"
                                textSize = 13f
                                setTextColor(if (isLightColor(adaptiveCol)) Color.BLACK else Color.WHITE)
                                setTypeface(null, Typeface.BOLD)
                                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
                            }
                            addView(tag)
                            setOnClickListener {
                                currentColor = ClockDrawer.COLOR_ADAPTIVE
                                saveTempPrefs()
                                previewView.invalidate()
                                updateUI()
                            }
                        }
                        row.addView(adaptiveCircle)

                        val presets = listOf(
                            Color.WHITE,
                            Color.parseColor("#111111"),
                            Color.parseColor("#FFD54F"),
                            Color.parseColor("#FF7043"),
                            Color.parseColor("#E91E63"),
                            Color.parseColor("#AB47BC"),
                            Color.parseColor("#42A5F5"),
                            Color.parseColor("#26A69A")
                        )

                        for (colVal in presets) {
                            val isSel = (currentColor == colVal)
                            val dot = View(this@MainActivity).apply {
                                background = GradientDrawable().apply {
                                    shape = GradientDrawable.OVAL
                                    setColor(colVal)
                                    if (isSel) setStroke(dp(3), Color.parseColor("#1A73E8"))
                                    else if (colVal == Color.WHITE) setStroke(dp(1), Color.LTGRAY)
                                }
                                layoutParams = LinearLayout.LayoutParams(dp(42), dp(42)).apply {
                                    setMargins(0, 0, dp(10), 0)
                                }
                                setOnClickListener {
                                    currentColor = colVal
                                    saveTempPrefs()
                                    previewView.invalidate()
                                    updateUI()
                                }
                            }
                            row.addView(dot)
                        }

                        val wheelBtn = TextView(this@MainActivity).apply {
                            text = "Custom"
                            textSize = 12f
                            gravity = Gravity.CENTER
                            background = GradientDrawable().apply {
                                setColor(Color.parseColor("#F1F3F4"))
                                cornerRadius = dp(14).toFloat()
                            }
                            setPadding(dp(14), dp(10), dp(14), dp(10))
                            setOnClickListener {
                                isHsvPickerOpen = true
                                updateUI()
                            }
                        }
                        row.addView(wheelBtn)

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
                        dragStartYPos = event.y
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
                        val dyRatio = (event.y - dragStartYPos) / h
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
