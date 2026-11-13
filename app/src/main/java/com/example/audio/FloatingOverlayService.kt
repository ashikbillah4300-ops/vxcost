package com.example.audio

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.VoxCastApplication
import com.example.VoxCastController
import com.example.data.AudioClipEntity
import com.example.data.VoxCastSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.abs

class FloatingOverlayService : Service() {

    companion object {
        const val ACTION_SHOW = "com.example.action.SHOW_OVERLAY"
        const val ACTION_HIDE = "com.example.action.HIDE_OVERLAY"
    }

    private lateinit var windowManager: WindowManager
    private lateinit var controller: VoxCastController
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var rootContainer: FrameLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var isExpanded = false

    // Dynamic UI references
    private var collapsedPill: LinearLayout? = null
    private var collapsedDot: View? = null
    private var collapsedLabel: TextView? = null

    private var expandedCard: LinearLayout? = null
    private var expandedStatusDot: View? = null
    private var micToggleBtn: TextView? = null
    private var presetRow: LinearLayout? = null
    private var clipsRow: LinearLayout? = null
    private var stopClipBtn: TextView? = null

    private var lastTelemetry = BroadcastTelemetry()
    private var lastSettings = VoxCastSettings()
    private var lastClips: List<AudioClipEntity> = emptyList()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        controller = (application as VoxCastApplication).controller
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> {
                removeOverlay()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SHOW, null -> {
                val canDraw = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
                if (!canDraw) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                ensureOverlayAttached()
            }
        }
        return START_STICKY
    }

    private fun ensureOverlayAttached() {
        if (rootContainer != null) return

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(140)
        }
        layoutParams = params

        val container = FrameLayout(this)
        val pill = buildCollapsedPill()
        val card = buildExpandedCard()
        card.visibility = View.GONE

        container.addView(pill)
        container.addView(card)
        rootContainer = container
        collapsedPill = pill
        expandedCard = card

        runCatching {
            windowManager.addView(container, params)
        }.onFailure {
            rootContainer = null
            stopSelf()
            return
        }

        observeControllerState()
    }

    private fun observeControllerState() {
        serviceScope.launch {
            combine(
                controller.telemetry,
                controller.settings,
                controller.clips
            ) { telemetry, settings, clips ->
                Triple(telemetry, settings, clips)
            }.collect { (telemetry, settings, clips) ->
                lastTelemetry = telemetry
                lastSettings = settings
                lastClips = clips
                renderState(telemetry, settings, clips)
            }
        }
    }

    private fun renderState(
        telemetry: BroadcastTelemetry,
        settings: VoxCastSettings,
        clips: List<AudioClipEntity>
    ) {
        val activePreset = VoicePreset.fromId(settings.selectedPresetId)
        val isBroadcasting = telemetry.isMicLive || telemetry.isClipPlaying

        // Collapsed Pill UI
        val dotColor = when {
            telemetry.isMicLive -> Color.parseColor("#FF6B2C")
            telemetry.isClipPlaying -> Color.parseColor("#22D3EE")
            else -> Color.parseColor("#6B7A94")
        }
        collapsedDot?.background = circleDrawable(dotColor)
        collapsedLabel?.text = when {
            telemetry.isMicLive -> "ON AIR • ${activePreset.displayName}"
            telemetry.isClipPlaying -> "CLIP • ${telemetry.playingClipTitle ?: "Playing"}"
            else -> "VoxCast"
        }
        collapsedPill?.background = roundedRectDrawable(
            fillColor = Color.parseColor("#E6131822"),
            strokeColor = if (isBroadcasting) dotColor else Color.parseColor("#2E384D"),
            strokePx = dp(1.5f),
            radiusPx = dp(24).toFloat()
        )

        // Expanded Card UI
        expandedStatusDot?.background = circleDrawable(dotColor)
        micToggleBtn?.apply {
            if (telemetry.isMicLive) {
                text = "STOP MIC"
                setTextColor(Color.WHITE)
                background = roundedRectDrawable(
                    fillColor = Color.parseColor("#FF6B2C"),
                    strokeColor = Color.parseColor("#FF996E"),
                    strokePx = dp(1f),
                    radiusPx = dp(12).toFloat()
                )
            } else {
                text = "GO LIVE"
                setTextColor(Color.parseColor("#0B0E14"))
                background = roundedRectDrawable(
                    fillColor = Color.parseColor("#22D3EE"),
                    strokeColor = Color.parseColor("#67E8F9"),
                    strokePx = dp(1f),
                    radiusPx = dp(12).toFloat()
                )
            }
        }

        // Update Voice Preset chips
        presetRow?.let { row ->
            row.removeAllViews()
            val quickPresets = listOf(
                VoicePreset.NATURAL,
                VoicePreset.FEMALE,
                VoicePreset.MALE,
                VoicePreset.KID,
                VoicePreset.MEGAPHONE,
                VoicePreset.ROBOT
            )
            quickPresets.forEach { preset ->
                val selected = preset == activePreset
                val chip = TextView(this).apply {
                    text = preset.displayName
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(
                        if (selected) Color.parseColor("#0B0E14") else Color.parseColor("#F3F6FC")
                    )
                    setPadding(dp(10), dp(6), dp(10), dp(6))
                    background = roundedRectDrawable(
                        fillColor = if (selected) Color.parseColor("#22D3EE") else Color.parseColor("#1B2230"),
                        strokeColor = if (selected) Color.parseColor("#22D3EE") else Color.parseColor("#2E384D"),
                        strokePx = dp(1f),
                        radiusPx = dp(10).toFloat()
                    )
                    setOnClickListener { controller.selectVoicePreset(preset) }
                }
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = dp(6)
                }
                row.addView(chip, lp)
            }
        }

        // Stop Clip visibility
        stopClipBtn?.visibility = if (telemetry.isClipPlaying) View.VISIBLE else View.GONE

        // Update Quick Clips + Alert Chime row
        clipsRow?.let { row ->
            row.removeAllViews()
            val chimeChip = TextView(this).apply {
                text = "Chime"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor("#FF6B2C"))
                setPadding(dp(10), dp(6), dp(10), dp(6))
                background = roundedRectDrawable(
                    fillColor = Color.parseColor("#2A1812"),
                    strokeColor = Color.parseColor("#FF6B2C"),
                    strokePx = dp(1f),
                    radiusPx = dp(10).toFloat()
                )
                setOnClickListener { controller.triggerAlertTone(QuickAlertTone.CHIME) }
            }
            row.addView(
                chimeChip,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(6) }
            )

            clips.take(4).forEach { clip ->
                val playing = telemetry.isClipPlaying && telemetry.playingClipId == clip.id && !telemetry.isClipPaused
                val clipChip = TextView(this).apply {
                    text = if (playing) "❚❚ ${clip.title}" else "▶ ${clip.title}"
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    maxWidth = dp(115)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(if (playing) Color.WHITE else Color.parseColor("#F3F6FC"))
                    setPadding(dp(10), dp(6), dp(10), dp(6))
                    background = roundedRectDrawable(
                        fillColor = if (playing) Color.parseColor("#FF6B2C") else Color.parseColor("#1B2230"),
                        strokeColor = if (playing) Color.parseColor("#FF6B2C") else Color.parseColor("#2E384D"),
                        strokePx = dp(1f),
                        radiusPx = dp(10).toFloat()
                    )
                    setOnClickListener { controller.toggleClipPlayback(clip) }
                }
                row.addView(
                    clipChip,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { marginEnd = dp(6) }
                )
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildCollapsedPill(): LinearLayout {
        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(14), dp(10))
            background = roundedRectDrawable(
                fillColor = Color.parseColor("#E6131822"),
                strokeColor = Color.parseColor("#2E384D"),
                strokePx = dp(1.5f),
                radiusPx = dp(24).toFloat()
            )
            elevation = dp(8).toFloat()
        }

        val dot = View(this).apply {
            background = circleDrawable(Color.parseColor("#6B7A94"))
        }
        collapsedDot = dot
        pill.addView(dot, LinearLayout.LayoutParams(dp(10), dp(10)).apply {
            marginEnd = dp(8)
        })

        val label = TextView(this).apply {
            text = "VoxCast"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#F3F6FC"))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            maxWidth = dp(155)
        }
        collapsedLabel = label
        pill.addView(label)

        attachDragListener(pill) {
            toggleExpanded(true)
        }

        return pill
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildExpandedCard(): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(12))
            background = roundedRectDrawable(
                fillColor = Color.parseColor("#F50F141E"),
                strokeColor = Color.parseColor("#2E384D"),
                strokePx = dp(1.5f),
                radiusPx = dp(18).toFloat()
            )
            elevation = dp(12).toFloat()
            layoutParams = FrameLayout.LayoutParams(dp(278), FrameLayout.LayoutParams.WRAP_CONTENT)
        }

        // Draggable Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(8))
        }

        val dot = View(this).apply {
            background = circleDrawable(Color.parseColor("#6B7A94"))
        }
        expandedStatusDot = dot
        header.addView(dot, LinearLayout.LayoutParams(dp(8), dp(8)).apply { marginEnd = dp(6) })

        val title = TextView(this).apply {
            text = "VOXCAST LIVE"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#A3B1C6"))
            letterSpacing = 0.08f
        }
        header.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val collapseBtn = TextView(this).apply {
            text = "—"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#F3F6FC"))
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = roundedRectDrawable(
                fillColor = Color.parseColor("#1B2230"),
                strokeColor = Color.parseColor("#2E384D"),
                strokePx = dp(1f),
                radiusPx = dp(8).toFloat()
            )
            setOnClickListener { toggleExpanded(false) }
        }
        header.addView(collapseBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = dp(6) })

        val closeBtn = TextView(this).apply {
            text = "✕"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#EF4444"))
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = roundedRectDrawable(
                fillColor = Color.parseColor("#2A1318"),
                strokeColor = Color.parseColor("#EF4444"),
                strokePx = dp(1f),
                radiusPx = dp(8).toFloat()
            )
            setOnClickListener {
                controller.setFloatingOverlayEnabled(false)
            }
        }
        header.addView(closeBtn)

        attachDragListener(header, onClick = null)
        card.addView(header)

        // Mic Live + Open App Row
        val actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val micBtn = TextView(this).apply {
            text = "GO LIVE"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setOnClickListener {
                val hasMic = ContextCompat.checkSelfPermission(
                    this@FloatingOverlayService,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (!hasMic) {
                    openMainApp()
                } else {
                    controller.toggleMicBroadcast()
                }
            }
        }
        micToggleBtn = micBtn
        actionsRow.addView(micBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(8)
        })

        val stopClip = TextView(this).apply {
            text = "STOP CLIP"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = roundedRectDrawable(
                fillColor = Color.parseColor("#EF4444"),
                strokeColor = Color.parseColor("#EF4444"),
                strokePx = dp(1f),
                radiusPx = dp(12).toFloat()
            )
            visibility = View.GONE
            setOnClickListener { controller.stopClipPlayback() }
        }
        stopClipBtn = stopClip
        actionsRow.addView(stopClip, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = dp(8) })

        val openAppBtn = TextView(this).apply {
            text = "APP"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#F3F6FC"))
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = roundedRectDrawable(
                fillColor = Color.parseColor("#1B2230"),
                strokeColor = Color.parseColor("#2E384D"),
                strokePx = dp(1f),
                radiusPx = dp(12).toFloat()
            )
            setOnClickListener { openMainApp() }
        }
        actionsRow.addView(openAppBtn)

        card.addView(actionsRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) })

        // Voice FX Label + Scroll Row
        val voiceLabel = TextView(this).apply {
            text = "VOICE CHANGER"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#6B7A94"))
            letterSpacing = 0.08f
        }
        card.addView(voiceLabel, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(5) })

        val presetScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        val pRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        presetRow = pRow
        presetScroll.addView(pRow)
        card.addView(presetScroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) })

        // Soundboard Quick Clips Row
        val clipsLabel = TextView(this).apply {
            text = "SOUNDBOARD & SIGNALS"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#6B7A94"))
            letterSpacing = 0.08f
        }
        card.addView(clipsLabel, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(5) })

        val clipsScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        val cRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        clipsRow = cRow
        clipsScroll.addView(cRow)
        card.addView(clipsScroll)

        return card
    }

    private fun toggleExpanded(expand: Boolean) {
        isExpanded = expand
        collapsedPill?.visibility = if (expand) View.GONE else View.VISIBLE
        expandedCard?.visibility = if (expand) View.VISIBLE else View.GONE
        val container = rootContainer ?: return
        val params = layoutParams ?: return
        runCatching { windowManager.updateViewLayout(container, params) }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachDragListener(target: View, onClick: (() -> Unit)?) {
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var moved = false

        target.setOnTouchListener { _, event ->
            val params = layoutParams ?: return@setOnTouchListener false
            val container = rootContainer ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                        moved = true
                    }
                    params.x = initialX + dx
                    params.y = initialY + dy
                    runCatching { windowManager.updateViewLayout(container, params) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved && onClick != null) {
                        onClick()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun openMainApp() {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(launchIntent)
    }

    private fun removeOverlay() {
        val container = rootContainer ?: return
        runCatching { windowManager.removeView(container) }
        rootContainer = null
        collapsedPill = null
        expandedCard = null
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        removeOverlay()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

    private fun roundedRectDrawable(
        fillColor: Int,
        strokeColor: Int,
        strokePx: Int,
        radiusPx: Float
    ) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fillColor)
        setStroke(strokePx, strokeColor)
        cornerRadius = radiusPx
    }

    private fun circleDrawable(fillColor: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(fillColor)
    }
}
