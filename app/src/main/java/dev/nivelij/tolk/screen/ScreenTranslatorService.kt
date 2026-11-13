package dev.nivelij.tolk.screen

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nivelij.tolk.Status
import dev.nivelij.tolk.data.Languages
import dev.nivelij.tolk.data.Photos
import dev.nivelij.tolk.data.Prefs
import dev.nivelij.tolk.data.Prompts
import dev.nivelij.tolk.data.Settings
import dev.nivelij.tolk.data.parsePhotoResult
import dev.nivelij.tolk.data.parseScreenTranslation
import dev.nivelij.tolk.net.Message
import dev.nivelij.tolk.net.OpenRouter
import dev.nivelij.tolk.net.batched
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.hypot

class ScreenTranslatorService : AccessibilityService() {
    private val scope = MainScope()
    private val api = OpenRouter()
    private lateinit var settings: Settings
    private lateinit var windowManager: WindowManager

    private val state = MutableStateFlow(ScreenState())
    private var job: Job? = null

    private var bubble: ComposeView? = null
    private var bubbleOwner: OverlayOwner? = null
    private var target: ComposeView? = null
    private var targetOwner: OverlayOwner? = null
    private val nearTarget = MutableStateFlow(false)
    private var inPlace: ComposeView? = null
    private var inPlaceOwner: OverlayOwner? = null
    private var bar: ComposeView? = null
    private var barLifecycle: OverlayOwner? = null
    private var panel: ComposeView? = null
    private var panelOwner: OverlayOwner? = null

    private val bubbleParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        y = BUBBLE_START_Y
    }

    override fun onServiceConnected() {
        settings = Settings(this)
        windowManager = getSystemService(WindowManager::class.java)
        bubbleParams.x = screenWidth() - bubbleSizePx()
        scope.launch {
            settings.prefs.map { it.showBubble }.distinctUntilChanged().collect { if (it) showBubble() else hideBubble() }
        }
    }

    // The in-place view is a snapshot; once the page scrolls or changes, its boxes no longer line up.
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || inPlace == null || event.packageName == packageName) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> closeAll()
        }
    }
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scope.cancel()
        closeAll()
        hideBubble()
        super.onDestroy()
    }

    private fun showBubble() {
        if (bubble != null) return
        val owner = OverlayOwner()
        val view = ComposeView(this).apply {
            setContent {
                Bubble(
                    onTap = ::onBubbleTap,
                    onGrab = ::showTarget,
                    onDrag = { dx, dy -> moveBubble(dx, dy) },
                    onRelease = ::releaseBubble,
                )
            }
        }
        owner.attach(view)
        windowManager.addView(view, bubbleParams)
        bubble = view
        bubbleOwner = owner
    }

    private fun hideBubble() {
        hideTarget()
        bubble?.let { windowManager.removeView(it) }
        bubbleOwner?.destroy()
        bubble = null
        bubbleOwner = null
    }

    private fun moveBubble(dx: Float, dy: Float) {
        val view = bubble ?: return
        val screen = windowManager.currentWindowMetrics.bounds
        bubbleParams.x = (bubbleParams.x + dx.toInt()).coerceIn(0, screen.width() - view.width)
        bubbleParams.y = (bubbleParams.y + dy.toInt()).coerceIn(0, screen.height() - view.height)
        windowManager.updateViewLayout(view, bubbleParams)
        val near = isOverTarget()
        if (near && !nearTarget.value) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        nearTarget.value = near
    }

    private fun releaseBubble() {
        val dismiss = isOverTarget()
        hideTarget()
        if (dismiss) {
            bubbleParams.x = screenWidth() - bubbleSizePx()
            bubbleParams.y = BUBBLE_START_Y
            scope.launch { settings.setShowBubble(false) }
        } else {
            snapBubble()
        }
    }

    private fun snapBubble() {
        val view = bubble ?: return
        val width = screenWidth()
        bubbleParams.x = if (bubbleParams.x + view.width / 2 < width / 2) 0 else width - view.width
        windowManager.updateViewLayout(view, bubbleParams)
    }

    private fun showTarget() {
        if (target != null) return
        val owner = OverlayOwner()
        val view = ComposeView(this).apply {
            setContent {
                val near by nearTarget.collectAsStateWithLifecycle()
                DismissTarget(near)
            }
        }
        owner.attach(view)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (64 * resources.displayMetrics.density).toInt()
        }
        windowManager.addView(view, params)
        target = view
        targetOwner = owner
    }

    private fun hideTarget() {
        nearTarget.value = false
        target?.let { windowManager.removeView(it) }
        targetOwner?.destroy()
        target = null
        targetOwner = null
    }

    private fun isOverTarget(): Boolean {
        val b = bubble ?: return false
        val t = target ?: return false
        if (t.width == 0) return false
        val bp = IntArray(2).also(b::getLocationOnScreen)
        val tp = IntArray(2).also(t::getLocationOnScreen)
        val dx = (bp[0] + b.width / 2f) - (tp[0] + t.width / 2f)
        val dy = (bp[1] + b.height / 2f) - (tp[1] + t.height / 2f)
        return hypot(dx, dy) < TARGET_RADIUS_DP * resources.displayMetrics.density
    }

    private fun screenWidth() = windowManager.currentWindowMetrics.bounds.width()

    private fun bubbleSizePx() = (BUBBLE_DP * resources.displayMetrics.density).toInt()

    private fun onBubbleTap() {
        if (panel != null || inPlace != null) return closeAll()
        val root = appWindowRoot()
        job?.cancel()
        job = scope.launch {
            val started = System.nanoTime()
            val blocks = withContext(Dispatchers.Default) { ScreenReader.read(root) }
            val chars = blocks.sumOf { it.text.length }
            Log.d(TAG, "Read ${blocks.size} blocks, $chars chars in ${(System.nanoTime() - started) / 1_000_000} ms")
            val prefs = settings.prefs.first()
            if (prefs.apiKey == null) {
                state.value = ScreenState(status = Status.Failed("Add your OpenRouter key in Tolk first."))
                openPanel()
                return@launch
            }
            if (chars >= MIN_TEXT) {
                translateBlocks(prefs, blocks)
            } else {
                val shot = captureScreen()
                if (shot == null) {
                    state.value = ScreenState(status = Status.Failed("Couldn't read this screen."))
                    openPanel()
                } else {
                    translateShot(prefs, shot)
                }
            }
        }
    }

    // Touching the bubble can make it the "active" window, so look for the app underneath explicitly.
    private fun appWindowRoot() = windows
        .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        .let { apps -> apps.firstOrNull { it.isFocused } ?: apps.maxByOrNull { it.layer } }
        ?.root ?: rootInActiveWindow

    private suspend fun translateBlocks(prefs: Prefs, blocks: List<ScreenBlock>) {
        state.value = ScreenState(mode = ScreenMode.Text, blocks = blocks, status = Status.Working)
        openInPlace()
        val raw = StringBuilder()
        val prompt = Prompts.screen(blocks.map { it.text }, Languages.byCode(prefs.target), prefs.tone)
        stream(prefs, prefs.textModel, prompt, "minimal") { chunk ->
            raw.append(chunk)
            val parsed = parseScreenTranslation(raw.toString())
            state.update {
                it.copy(
                    detected = parsed.lang,
                    items = parsed.items,
                    output = blocks.indices.mapNotNull { i -> parsed.items[i] }.joinToString("\n"),
                )
            }
        }
    }

    private suspend fun translateShot(prefs: Prefs, shot: Bitmap) {
        state.value = ScreenState(mode = ScreenMode.Photo, status = Status.Working)
        openPanel()
        val dataUrl = withContext(Dispatchers.Default) { Photos.toDataUrl(shot) }
        val raw = StringBuilder()
        stream(prefs, prefs.visionModel, Prompts.photo(dataUrl, Languages.byCode(prefs.target), ""), "low") { chunk ->
            raw.append(chunk)
            state.update { it.copy(photo = parsePhotoResult(raw.toString())) }
        }
    }

    private suspend fun stream(prefs: Prefs, model: String, messages: List<Message>, effort: String, onChunk: (String) -> Unit) {
        try {
            api.stream(prefs.apiKey!!, model, messages, prefs.noDataCollection, effort).batched().collect(onChunk)
            state.update { it.copy(status = Status.Idle) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            state.update { it.copy(status = Status.Failed(e.message ?: "Something went wrong talking to OpenRouter.")) }
        }
    }

    private suspend fun captureScreen(): Bitmap? {
        bubble?.visibility = View.INVISIBLE
        try {
            delay(150)
            return suspendCancellableCoroutine { cont ->
                takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        val hardware = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                        val full = hardware?.copy(Bitmap.Config.ARGB_8888, false)
                        hardware?.recycle()
                        result.hardwareBuffer.close()
                        val small = full?.let(Photos::shrink)
                        if (small !== full) full?.recycle()
                        cont.resume(small)
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.w(TAG, "Screenshot failed: $errorCode")
                        cont.resume(null)
                    }
                })
            }
        } finally {
            bubble?.visibility = View.VISIBLE
        }
    }

    private fun overlayParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply { fitInsetsTypes = 0 }

    private fun overlayView(content: @androidx.compose.runtime.Composable (Prefs) -> Unit): Pair<ComposeView, OverlayOwner> {
        val owner = OverlayOwner()
        val view = ComposeView(this).apply {
            setContent {
                val prefs by settings.prefs.collectAsStateWithLifecycle(initialValue = null)
                prefs?.let { content(it) }
            }
        }
        owner.attach(view)
        return view to owner
    }

    private fun openInPlace() {
        if (inPlace != null) return
        val (view, owner) = overlayView { prefs ->
            val current by state.collectAsStateWithLifecycle()
            InPlaceOverlay(current, Languages.byCode(prefs.target))
        }
        val params = overlayParams().apply {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        windowManager.addView(view, params)
        inPlace = view
        inPlaceOwner = owner

        val (barView, barOwner) = overlayView { prefs ->
            val current by state.collectAsStateWithLifecycle()
            InPlaceBar(current, Languages.byCode(prefs.target), onList = ::showList, onClose = ::closeAll, onRetry = ::onRetry)
        }
        val barParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (24 * resources.displayMetrics.density).toInt()
        }
        windowManager.addView(barView, barParams)
        bar = barView
        barLifecycle = barOwner
    }

    private fun openPanel() {
        if (panel != null) return
        val (view, owner) = overlayView { prefs ->
            val current by state.collectAsStateWithLifecycle()
            ScreenPanel(current, Languages.byCode(prefs.target), onClose = ::closeAll, onRetry = ::onRetry)
        }
        windowManager.addView(view, overlayParams())
        panel = view
        panelOwner = owner
    }

    private fun showList() {
        removeInPlace()
        openPanel()
    }

    private fun removeInPlace() {
        inPlace?.let { windowManager.removeView(it) }
        inPlaceOwner?.destroy()
        inPlace = null
        inPlaceOwner = null
        bar?.let { windowManager.removeView(it) }
        barLifecycle?.destroy()
        bar = null
        barLifecycle = null
    }

    private fun closeAll() {
        job?.cancel()
        removeInPlace()
        panel?.let { windowManager.removeView(it) }
        panelOwner?.destroy()
        panel = null
        panelOwner = null
    }

    private fun onRetry() {
        closeAll()
        onBubbleTap()
    }

    private companion object {
        const val TAG = "TolkScreen"
        const val MIN_TEXT = 12
        const val BUBBLE_DP = 70
        const val BUBBLE_START_Y = 700
        const val TARGET_RADIUS_DP = 64
    }
}
