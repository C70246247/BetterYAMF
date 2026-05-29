package com.buildsession.betterYAMF.xposed.ui.window

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.ActivityTaskManager
import android.app.ITaskStackListenerProxy
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.DISPLAY_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.IPackageManagerHidden
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.graphics.drawable.BitmapDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.util.TypedValue
import android.view.Display
import android.view.GestureDetector
import android.view.Gravity
import android.view.IRotationWatcher
import android.view.InputDevice
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.WindowManagerHidden
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.ImageButton
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.dynamicanimation.animation.FlingAnimation
import androidx.dynamicanimation.animation.flingAnimationOf
import androidx.wear.widget.RoundedDrawable
import com.github.kyuubiran.ezxhelper.utils.argTypes
import com.github.kyuubiran.ezxhelper.utils.args
import com.github.kyuubiran.ezxhelper.utils.getObject
import com.github.kyuubiran.ezxhelper.utils.getObjectAs
import com.github.kyuubiran.ezxhelper.utils.invokeMethod
import com.google.android.material.color.MaterialColors
import com.buildsession.betterYAMF.common.getAttr
import com.buildsession.betterYAMF.common.onException
import com.buildsession.betterYAMF.common.runMain
import com.buildsession.betterYAMF.databinding.LeftBackGestureOverlayBinding
import com.buildsession.betterYAMF.databinding.RightBackGestureOverlayBinding
import com.buildsession.betterYAMF.databinding.WindowAppBinding
import kotlinx.coroutines.withContext
import com.buildsession.betterYAMF.xposed.services.YAMFManager
import com.buildsession.betterYAMF.xposed.services.YAMFManager.config
import com.buildsession.betterYAMF.xposed.utils.Instances
import com.buildsession.betterYAMF.xposed.utils.RunMainThreadQueue
import com.buildsession.betterYAMF.xposed.utils.TipUtil
import com.buildsession.betterYAMF.xposed.utils.animateAlpha
import com.buildsession.betterYAMF.xposed.utils.animateResize
import com.buildsession.betterYAMF.xposed.utils.animateScaleThenResize
import com.buildsession.betterYAMF.xposed.utils.dpToPx
import com.buildsession.betterYAMF.xposed.utils.getActivityInfoCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sqrt
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable


@SuppressLint("ClickableViewAccessibility", "SetTextI18n")
class AppWindow(
    val context: Context,
    private val flags: Int,
    private val onVirtualDisplayCreated: (AppWindow, Int) -> Unit
) :
    TextureView.SurfaceTextureListener, SurfaceHolder.Callback {
    companion object {
        const val TAG = "reYAMF_AppWindow"
        const val ACTION_RESET_ALL_WINDOW = "com.buildsession.betterYAMF.ui.window.action.ACTION_RESET_ALL_WINDOW"
    }

    lateinit var binding: WindowAppBinding
    lateinit var bindingLeftBackGesture: LeftBackGestureOverlayBinding
    lateinit var bindingRightBackGesture: RightBackGestureOverlayBinding
    private lateinit var virtualDisplay: VirtualDisplay
    
    var currentTaskId = -1
    private var isDestroyed = false

    private val rotationWatcher = RotationWatcher()
    private val surfaceOnTouchListener = SurfaceOnTouchListener()
    private val surfaceOnGenericMotionListener = SurfaceOnGenericMotionListener()
    var displayId = -1
    var rotateLock = false
    var isMini = false
    var isCollapsed = false
    private var halfWidth = 0
    private var halfHeight = 0
    lateinit var surfaceView: View
    private var newDpi = calculateDpi(
        config.defaultWindowWidth, config.defaultWindowHeight,
        calculateScreenInches(config.defaultWindowWidth, config.defaultWindowHeight)
    ) - config.reduceDPI
    private var originalWidth: Int = 0
    private var originalHeight: Int = 0
    private var isResize: Boolean = true
    private var orientation = 0
    private var params = WindowManager.LayoutParams()
    private var paramsBg = WindowManager.LayoutParams()
    private var backGestureJob: Job? = null
    private var keepInScreenAnimator: ValueAnimator? = null
    private var lastClickTime = 0L
    private val DOUBLE_CLICK_TIME_DELTA: Long = 300
    private var isSuperShown = false
    private var cornerDropZoneView: CornerDropZoneView? = null
    private var currentHighlightedCorner = -1

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_RESET_ALL_WINDOW) {
                val lp = binding.root.layoutParams as WindowManager.LayoutParams
                val dm = context.resources.displayMetrics
                val width = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 200F, context.resources.displayMetrics).toInt()
                val height = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 300F, context.resources.displayMetrics).toInt()
                
                lp.apply {
                    x = (dm.widthPixels - width) / 2
                    y = (dm.heightPixels - height) / 2
                }
                Instances.windowManager.updateViewLayout(binding.root, lp)
                
                binding.vSizePreviewer.updateLayoutParams {
                    this.width = width
                    this.height = height
                }
                surfaceView.updateLayoutParams {
                    this.width = width
                    this.height = height
                }
            }
        }
    }

    init {
        runCatching {
            binding = WindowAppBinding.inflate(LayoutInflater.from(context))
            bindingLeftBackGesture = LeftBackGestureOverlayBinding.inflate(LayoutInflater.from(context))
            bindingRightBackGesture = RightBackGestureOverlayBinding.inflate(LayoutInflater.from(context))
        }.onException { e ->
            Log.e(TAG, "Failed to create new window, did you reboot?", e)
            TipUtil.showToast("Failed to create new window, did you reboot?")
        }.onSuccess {
            doInit()
        }
    }

    private fun doInit() {
        when(config.surfaceView) {
            0 -> {
                surfaceView = binding.viewSurface
                binding.viewTexture.visibility = View.GONE
            }
            1 -> {
                surfaceView = binding.viewTexture
                binding.viewSurface.visibility = View.GONE
            }
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                    WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
            PixelFormat.TRANSLUCENT
        )

        val displayManager = context.getSystemService(DISPLAY_SERVICE) as DisplayManager
        val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
        val rotation = display?.rotation ?: Surface.ROTATION_0
        
        val dm = context.resources.displayMetrics
        val screenWidth = dm.widthPixels
        val screenHeight = dm.heightPixels
        val windowWidth = config.defaultWindowWidth.dpToPx().toInt()
        val windowHeight = config.defaultWindowHeight.dpToPx().toInt()

        params.apply {
            gravity = Gravity.TOP or Gravity.START
            
            when (rotation) {
                Surface.ROTATION_0, Surface.ROTATION_180 -> {
                    orientation = 0
                    binding.rlBarControllerSide.isVisible = false
                    // Center horizontally, use config.portraitY if set, else center vertically with offset
                    x = (screenWidth - windowWidth) / 2
                    y = if (config.portraitY != 0) config.portraitY else (screenHeight - windowHeight) / 2 - 80.dpToPx().toInt()
                }
                Surface.ROTATION_90, Surface.ROTATION_270 -> {
                    orientation = 1
                    binding.rlBarControllerBottom.isVisible = false
                    // Center horizontally, use config.landscapeY if set, else center vertically
                    x = (screenWidth - windowWidth) / 2
                    y = if (config.landscapeY != 0) config.landscapeY else (screenHeight - windowHeight) / 2
                }
                else -> {
                    x = 0
                    y = 0
                }
            }
        }

        paramsBg = WindowManager.LayoutParams(
            20.dpToPx().toInt(),
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        paramsBg.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS

        bindingLeftBackGesture.root.let {
            paramsBg.gravity = Gravity.START or Gravity.TOP
            Instances.windowManager.addView(bindingLeftBackGesture.root, paramsBg)
        }

        bindingRightBackGesture.root.let {
            val paramsBgR = WindowManager.LayoutParams(
                20.dpToPx().toInt(),
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            )
            paramsBgR.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            paramsBgR.gravity = Gravity.END or Gravity.TOP
            Instances.windowManager.addView(bindingRightBackGesture.root, paramsBgR)
        }

        binding.root.let { layout ->
            Instances.windowManager.addView(layout, params)
        }

        binding.rootClickMask.setOnTouchListener { _, event ->
            moveGestureDetector.onTouchEvent(event)
            moveToTopIfNeed(event)
            
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                when (currentHighlightedCorner) {
                    in 0..3 -> changeMini()
                    4, 5 -> changeCollapsed()
                    else -> keepInScreen()
                }
                currentHighlightedCorner = -1
                hideCornerDropZone()
            }
            true
        }

        binding.cvBarClickMask.setOnClickListener {
            val clickTime = System.currentTimeMillis()
            if (clickTime - lastClickTime < DOUBLE_CLICK_TIME_DELTA) {
                isResize = false
                backGestureJob?.cancel()
                backGestureJob = null

                binding.cvappIcon.visibility = View.INVISIBLE
                if (orientation == 0) {
                    animateAlpha(binding.rlBarControllerBottom, 1f, 0f) {
                        binding.rlBarControllerBottom.visibility = View.GONE
                    }
                } else {
                    animateAlpha(binding.rlBarControllerSide, 1f, 0f) {
                        binding.rlBarControllerSide.visibility = View.GONE
                    }
                }

                CoroutineScope(Dispatchers.IO).launch {
                    delay(200)

                    withContext(Dispatchers.Main) {
                        animateScaleThenResize(
                            binding.cvParent,
                            1F, 1F,
                            0F, 0F,
                            0.5F, 0.5F,
                            0, 0,
                            context
                        ) {
                            onDestroy()
                        }
                    }
                }
            }
            lastClickTime = clickTime
        }

        binding.cvBarSideClickMask.setOnClickListener {
            val clickTime = System.currentTimeMillis()
            if (clickTime - lastClickTime < DOUBLE_CLICK_TIME_DELTA) {
                isResize = false
                backGestureJob?.cancel()
                backGestureJob = null

                binding.cvappIcon.visibility = View.INVISIBLE
                if (orientation == 0) {
                    animateAlpha(binding.rlBarControllerBottom, 1f, 0f) {
                        binding.rlBarControllerBottom.visibility = View.GONE
                    }
                } else {
                    animateAlpha(binding.rlBarControllerSide, 1f, 0f) {
                        binding.rlBarControllerSide.visibility = View.GONE
                    }
                }

                CoroutineScope(Dispatchers.IO).launch {
                    delay(200)

                    withContext(Dispatchers.Main) {
                        animateScaleThenResize(
                            binding.cvParent,
                            1F, 1F,
                            0F, 0F,
                            0.5F, 0.5F,
                            0, 0,
                            context
                        ) {
                            onDestroy()
                        }
                    }
                }
            }
            lastClickTime = clickTime
        }

        binding.ibSuper.setOnTouchListener { _, event ->
            moveGestureDetector.onTouchEvent(event)
            moveToTopIfNeed(event)
            
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                when (currentHighlightedCorner) {
                    in 0..3 -> changeMini()
                    4, 5 -> changeCollapsed()
                    else -> keepInScreen()
                }
                currentHighlightedCorner = -1
                hideCornerDropZone()
            }
            false
        }

        binding.ibSuper.setOnClickListener {
            isSuperShown = true
            animateAlpha(binding.clSuperLayout, 0f, 1f)
        }

        binding.cvBarClickMask.setOnTouchListener(object : View.OnTouchListener {
             private var startRawY = 0f
             private val threshold = 80.dpToPx()
             private var isSwiping = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                moveToTopIfNeed(event)
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startRawY = event.rawY
                        isSwiping = true
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!isSwiping) return false
                        val diffY = event.rawY - startRawY
                        
                        if (diffY < 0) { // Swipe up - Shrink and fade
                            val progress = (-diffY / threshold).coerceIn(0f, 1f)
                            val scale = 1f - (progress * 0.3f) // Scale down to 70%
                            val alpha = 1f - progress
                            binding.cvParent.scaleX = scale
                            binding.cvParent.scaleY = scale
                            binding.cvParent.alpha = alpha
                            
                            binding.viewFullScreenMask.visibility = View.GONE
                            binding.tvFullScreenPrompt.visibility = View.GONE
                        } else { // Swipe down - Enlarge and show full screen mask
                            val progress = (diffY / threshold).coerceIn(0f, 1f)
                            val scale = 1f + (progress * 0.2f) // Scale up to 120%
                            binding.cvParent.scaleX = scale
                            binding.cvParent.scaleY = scale
                            binding.cvParent.alpha = 1f
                            
                            if (diffY > threshold) {
                                binding.viewFullScreenMask.visibility = View.VISIBLE
                                binding.tvFullScreenPrompt.visibility = View.VISIBLE
                            } else {
                                binding.viewFullScreenMask.visibility = View.GONE
                                binding.tvFullScreenPrompt.visibility = View.GONE
                            }
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (!isSwiping) return false
                        val diffY = event.rawY - startRawY
                        
                        if (diffY < -threshold) { // Swipe up past threshold - Close
                            animateScaleThenResize(
                                binding.cvParent,
                                binding.cvParent.scaleX, binding.cvParent.scaleY,
                                0f, 0f,
                                0.5f, 0.5f,
                                0, 0,
                                context
                            ) {
                                onDestroy()
                            }
                        } else if (diffY > threshold) { // Swipe down past threshold - Full Screen
                            getTopRootTask()?.runCatching {
                                Instances.activityTaskManager.moveRootTaskToDisplay(taskId, 0)
                                Instances.activityManager.moveTaskToFront(taskId, 0)
                            }?.onSuccess {
                                onDestroy()
                            }?.onFailure {
                                resetWindow()
                            }
                        } else {
                            resetWindow()
                        }
                        isSwiping = false
                        return true
                    }
                }
                return false
            }

            private fun resetWindow() {
                binding.cvParent.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(200)
                    .start()
                binding.viewFullScreenMask.visibility = View.GONE
                binding.tvFullScreenPrompt.visibility = View.GONE
            }
        })

        binding.cvBarSideClickMask.setOnTouchListener(object : View.OnTouchListener {
             private var startRawY = 0f
             private val threshold = 80.dpToPx()
             private var isSwiping = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                moveToTopIfNeed(event)
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startRawY = event.rawY
                        isSwiping = true
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!isSwiping) return false
                        val diffY = event.rawY - startRawY
                        
                        if (diffY < 0) { // Swipe up - Shrink and fade
                            val progress = (-diffY / threshold).coerceIn(0f, 1f)
                            val scale = 1f - (progress * 0.3f)
                            val alpha = 1f - progress
                            binding.cvParent.scaleX = scale
                            binding.cvParent.scaleY = scale
                            binding.cvParent.alpha = alpha
                            
                            binding.viewFullScreenMask.visibility = View.GONE
                            binding.tvFullScreenPrompt.visibility = View.GONE
                        } else { // Swipe down - Enlarge and show full screen mask
                            val progress = (diffY / threshold).coerceIn(0f, 1f)
                            val scale = 1f + (progress * 0.2f)
                            binding.cvParent.scaleX = scale
                            binding.cvParent.scaleY = scale
                            binding.cvParent.alpha = 1f
                            
                            if (diffY > threshold) {
                                binding.viewFullScreenMask.visibility = View.VISIBLE
                                binding.tvFullScreenPrompt.visibility = View.VISIBLE
                            } else {
                                binding.viewFullScreenMask.visibility = View.GONE
                                binding.tvFullScreenPrompt.visibility = View.GONE
                            }
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (!isSwiping) return false
                        val diffY = event.rawY - startRawY
                        
                        if (diffY < -threshold) { // Swipe up past threshold - Close
                            animateScaleThenResize(
                                binding.cvParent,
                                binding.cvParent.scaleX, binding.cvParent.scaleY,
                                0f, 0f,
                                0.5f, 0.5f,
                                0, 0,
                                context
                            ) {
                                onDestroy()
                            }
                        } else if (diffY > threshold) { // Swipe down past threshold - Full Screen
                            getTopRootTask()?.runCatching {
                                Instances.activityTaskManager.moveRootTaskToDisplay(taskId, 0)
                                Instances.activityManager.moveTaskToFront(taskId, 0)
                            }?.onSuccess {
                                onDestroy()
                            }?.onFailure {
                                resetWindow()
                            }
                        } else {
                            resetWindow()
                        }
                        isSwiping = false
                        return true
                    }
                }
                return false
            }

            private fun resetWindow() {
                binding.cvParent.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(200)
                    .start()
                binding.viewFullScreenMask.visibility = View.GONE
                binding.tvFullScreenPrompt.visibility = View.GONE
            }
        })

        rightResize(binding.ibRightResize)

        surfaceView.setOnTouchListener(surfaceOnTouchListener)
        surfaceView.setOnGenericMotionListener(surfaceOnGenericMotionListener)

        binding.ibClose.setOnClickListener {
            isResize = false
            backGestureJob?.cancel()
            backGestureJob = null

            binding.cvappIcon.visibility = View.INVISIBLE
            if (orientation == 0) {
                animateAlpha(binding.rlBarControllerBottom, 1f, 0f) {
                    binding.rlBarControllerBottom.visibility = View.GONE
                }
            } else {
                animateAlpha(binding.rlBarControllerSide, 1f, 0f) {
                    binding.rlBarControllerSide.visibility = View.GONE
                }
            }

            CoroutineScope(Dispatchers.IO).launch {
                delay(200)

                withContext(Dispatchers.Main) {
                    animateScaleThenResize(
                        binding.cvParent,
                        1F, 1F,
                        0F, 0F,
                        0.5F, 0.5F,
                        0, 0,
                        context
                    ) {
                        onDestroy()
                    }
                }
            }
        }

        binding.ibFullscreen.setOnClickListener {
            animateAlpha(binding.clSuperLayout, 1f, 0f)
            getTopRootTask()?.runCatching {
                Instances.activityTaskManager.moveRootTaskToDisplay(taskId, 0)
            }?.onFailure { t ->
                if (t is Error) throw t
                TipUtil.showToast("${t.message}")
            }?.onSuccess {
                binding.ibClose.callOnClick()
            }
        }

        binding.ibMinimize.setOnClickListener {
            isSuperShown = false
            binding.apply {
                animateAlpha(binding.clSuperLayout, 1f, 0f)
                binding.clSuperLayout.visibility = View.GONE
                changeMini()
            }
        }

        binding.ibCollapse.setOnClickListener {
            isSuperShown = false
            binding.apply {
                animateAlpha(binding.clSuperLayout, 1f, 0f)
                binding.clSuperLayout.visibility = View.GONE
                changeCollapsed()
            }
            true
        }

        binding.ibSuperClose.setOnClickListener {
            isSuperShown = false
            animateAlpha(binding.clSuperLayout, 1f, 0f)
        }

        virtualDisplay = Instances.displayManager.createVirtualDisplay(
            "yamf${System.currentTimeMillis()}", config.defaultWindowWidth, config.defaultWindowHeight, newDpi-config.reduceDPI, null, flags
        )
        displayId = virtualDisplay.display.displayId
        (Instances.windowManager as WindowManagerHidden).setDisplayImePolicy(displayId, if (config.showImeInWindow) WindowManagerHidden.DISPLAY_IME_POLICY_LOCAL else WindowManagerHidden.DISPLAY_IME_POLICY_FALLBACK_DISPLAY)
        
        (surfaceView as? TextureView)?.surfaceTextureListener = this
        (surfaceView as? SurfaceView)?.holder?.addCallback(this)
        var failCount = 0
        fun watchRotation() {
            runCatching {
                Instances.iWindowManager.watchRotation(rotationWatcher, displayId)
            }.onFailure {
                failCount++
                // Log.d(TAG, "watchRotation: fail $failCount")
                watchRotation()
            }
        }
        watchRotation()
        context.registerReceiver(broadcastReceiver, IntentFilter(ACTION_RESET_ALL_WINDOW), Context.RECEIVER_EXPORTED)
        val width = config.defaultWindowWidth.dpToPx().toInt()
        val height = config.defaultWindowHeight.dpToPx().toInt()
        surfaceView.updateLayoutParams {
            this.width = width
            this.height = height
        }
        binding.vSizePreviewer.updateLayoutParams {
            this.width = width
            this.height = height
        }
        onVirtualDisplayCreated(this, displayId)

        isResize = false
        binding.cvBackground.post {
            originalWidth = binding.cvBackground.width
            originalHeight = binding.cvBackground.height
            binding.cvBackground.visibility = View.VISIBLE

            binding.cvBackground.radius = config.windowRoundedCorner.dpToPx()
            binding.cvappIcon.radius = config.windowRoundedCorner.dpToPx()


            binding.cvParent.radius = (config.windowRoundedCorner+2).dpToPx()
            originalWidth = binding.cvParent.width
            originalHeight = binding.cvParent.height
            binding.cvParent.visibility = View.VISIBLE

            animateScaleThenResize(
                binding.cvBackground,
                0F, 0F,
                1F, 1F,
                0.5F, 0.5F,
                originalWidth, originalHeight,
                context
            ) {
                setBackgroundWrapContent()

                CoroutineScope(Dispatchers.Main).launch {
                    delay(200)

                    binding.cvParent.strokeWidth = 2.dpToPx().toInt()
                }

                isResize = true
            }
        }

        //TODO: Find me a better alternative for less resource usage instead of polling
        backGestureJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                if (isMini || isCollapsed) {
                    withContext(Dispatchers.Main) {
                        bindingLeftBackGesture.root.visibility = View.GONE
                        bindingRightBackGesture.root.visibility = View.GONE
                    }
                } else if (displayId == YAMFManager.currentDisplayId) {
                    withContext(Dispatchers.Main) {
                        bindingLeftBackGesture.root.visibility = View.VISIBLE
                        bindingRightBackGesture.root.visibility = View.VISIBLE
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        bindingLeftBackGesture.root.visibility = View.GONE
                        bindingRightBackGesture.root.visibility = View.GONE
                    }
                }

                if (!isMini && !isCollapsed) {
                    withContext(Dispatchers.Main) {
                        if (orientation == 0) {
                            binding.rlBarControllerBottom.visibility = View.VISIBLE
                        } else {
                            binding.rlBarControllerSide.visibility = View.VISIBLE
                        }
                    }
                }
                delay(500)
            }
        }
    }

    fun onDestroy() {
        if (isDestroyed) return
        isDestroyed = true
        
        runCatching { context.unregisterReceiver(broadcastReceiver) }
        runCatching { Instances.iWindowManager.removeRotationWatcher(rotationWatcher) }
        
        YAMFManager.removeWindow(displayId)
        runCatching { virtualDisplay.release() }
        
        runMain {
            if (binding.root.isAttachedToWindow) {
                runCatching { Instances.windowManager.removeView(binding.root) }
            }
            if (bindingLeftBackGesture.root.isAttachedToWindow) {
                runCatching { Instances.windowManager.removeView(bindingLeftBackGesture.root) }
            }
            if (bindingRightBackGesture.root.isAttachedToWindow) {
                runCatching { Instances.windowManager.removeView(bindingRightBackGesture.root) }
            }
            cornerDropZoneView?.let {
                if (it.isAttachedToWindow) {
                    runCatching { Instances.windowManager.removeView(it) }
                }
                cornerDropZoneView = null
            }
        }
    }

    private fun getTopRootTask(): ActivityTaskManager.RootTaskInfo? {
        Instances.activityTaskManager.getAllRootTaskInfosOnDisplay(displayId).forEach { task ->
            if (task.visible)
                return task
        }
        return null
    }

    private fun moveToTop() {
        if (bindingLeftBackGesture.root.isAttachedToWindow) {
            Instances.windowManager.removeView(bindingLeftBackGesture.root)
        }
        Instances.windowManager.addView(bindingLeftBackGesture.root, bindingLeftBackGesture.root.layoutParams)
        
        if (bindingRightBackGesture.root.isAttachedToWindow) {
            Instances.windowManager.removeView(bindingRightBackGesture.root)
        }
        Instances.windowManager.addView(bindingRightBackGesture.root, bindingRightBackGesture.root.layoutParams)

        if (binding.root.isAttachedToWindow) {
            Instances.windowManager.removeView(binding.root)
        }
        Instances.windowManager.addView(binding.root, binding.root.layoutParams)
        YAMFManager.moveToTop(displayId)
    }

    private fun moveToTopIfNeed(event: MotionEvent) {
        if (event.action == MotionEvent.ACTION_UP && YAMFManager.isTop(displayId).not()) {
            moveToTop()
        }
    }

    private fun updateTask(taskInfo: ActivityManager.RunningTaskInfo) {
        currentTaskId = taskInfo.taskId
        RunMainThreadQueue.add {
            if (taskInfo.isVisible.not()) {
                delay(500) // fixme: use a method that directly determines visibility
            }

            var backgroundColor = 0
            var statusBarColor = 0
            var navigationBarColor = 0
            var taskDescription: ActivityManager.TaskDescription?

            if (Build.VERSION.SDK_INT < 35) {
                val topActivity = taskInfo.topActivity ?: return@add
                taskDescription = Instances.activityTaskManager.getTaskDescription(taskInfo.taskId) ?: return@add
                val activityInfo = (Instances.iPackageManager as IPackageManagerHidden).getActivityInfoCompat(topActivity, 0, taskInfo.getObjectAs("userId"))

                backgroundColor = taskDescription.backgroundColor
                statusBarColor = taskDescription.backgroundColor
                navigationBarColor = taskDescription.backgroundColor
                
                val iconDrawable = runCatching { taskDescription.icon }.getOrNull()?.let { BitmapDrawable(context.resources, it) } 
                    ?: activityInfo.loadIcon(Instances.packageManager)
                
                binding.appIcon.setImageDrawable(iconDrawable)
                binding.appIcon.scaleType = ImageView.ScaleType.CENTER_CROP
            } else {
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val runningTasks = activityManager.getRunningTasks(5)

                for (task in runningTasks) {
                    if (task.taskId == taskInfo.taskId) {
                        val packageName = task.baseActivity?.packageName
                        try {
                            val packageManager = context.packageManager
                            backgroundColor = task.taskDescription!!.backgroundColor
                            statusBarColor = task.taskDescription!!.backgroundColor
                            navigationBarColor = task.taskDescription!!.backgroundColor
                            val iconDrawable = packageManager.getApplicationIcon(packageName!!)
                            binding.appIcon.setImageDrawable(iconDrawable)
                            binding.appIcon.scaleType = ImageView.ScaleType.CENTER_CROP
                        } catch (e: PackageManager.NameNotFoundException) {
                            e.printStackTrace()
                        }
                    }
                }
            }

            if (config.coloredController) {
                val onStateBar = if (MaterialColors.isColorLight(ColorUtils.compositeColors(statusBarColor, backgroundColor)) xor ((context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES)) {
                    context.theme.getAttr(com.google.android.material.R.attr.colorOnPrimaryContainer).data
                } else {
                    context.theme.getAttr(com.google.android.material.R.attr.colorOnPrimary).data
                }

                binding.ibClose.imageTintList = ColorStateList.valueOf(onStateBar)
                binding.background.setBackgroundColor(navigationBarColor)

                val onNavigationBar = if (MaterialColors.isColorLight(ColorUtils.compositeColors(navigationBarColor, backgroundColor)) xor ((context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES)) {
                    context.theme.getAttr(com.google.android.material.R.attr.colorOnPrimaryContainer).data
                } else {
                    context.theme.getAttr(com.google.android.material.R.attr.colorOnPrimary).data
                }

                binding.ibMinimize.imageTintList = ColorStateList.valueOf(onNavigationBar)
                binding.ibFullscreen.imageTintList = ColorStateList.valueOf(onNavigationBar)
                binding.ibRightResize.imageTintList = ColorStateList.valueOf(onNavigationBar)
            }
        }
    }

    fun onTaskMovedToFront(taskInfo: ActivityManager.RunningTaskInfo) {
        val taskDisplayId = runCatching { taskInfo.getObject("displayId") as Int }.getOrDefault(-1)
        if (taskDisplayId == displayId) {
            // 检测是否回到了桌面 (Home Activity)，如果是则自动关闭小窗
            // ACTIVITY_TYPE_HOME = 2
            val activityType = runCatching {
                val config = taskInfo.getObject("configuration")
                val windowConfig = config.getObject("windowConfiguration")
                windowConfig.invokeMethod("getActivityType") as Int
            }.getOrElse {
                // 兜底逻辑：通过 topActivity 的包名判断是否为桌面
                val topActivity = taskInfo.topActivity
                if (topActivity != null) {
                    val pkg = topActivity.packageName
                    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    val resolveInfo = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                    val defaultLauncher = resolveInfo?.activityInfo?.packageName
                    
                    // 1. 检查是否为默认桌面
                    // 2. 检查常见 Launcher 包名
                    // 3. 模糊匹配包含 launcher 的包名 (排除某些系统组件)
                    if (pkg == defaultLauncher || 
                        pkg == "com.android.launcher3" || 
                        pkg == "com.google.android.apps.nexuslauncher" ||
                        (pkg.contains("launcher", ignoreCase = true) && !pkg.contains("service", ignoreCase = true))) 2 else 0
                } else 0
            }

            if (activityType == 2) {
                onDestroy()
                return
            }

            updateTask(taskInfo)
        }
    }

    fun onTaskDescriptionChanged(taskInfo: ActivityManager.RunningTaskInfo) {
        val taskDisplayId = runCatching { taskInfo.getObject("displayId") as Int }.getOrDefault(-1)
        if (taskDisplayId == displayId) {
            if(!taskInfo.isVisible){
                return
            }
            
            // 同样增加桌面检测，防止某些情况下 onTaskMovedToFront 未触发
            val activityType = runCatching {
                val config = taskInfo.getObject("configuration")
                val windowConfig = config.getObject("windowConfiguration")
                windowConfig.invokeMethod("getActivityType") as Int
            }.getOrElse {
                val topActivity = taskInfo.topActivity
                if (topActivity != null) {
                    val pkg = topActivity.packageName
                    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    val resolveInfo = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                    val defaultLauncher = resolveInfo?.activityInfo?.packageName
                    if (pkg == defaultLauncher || 
                        pkg == "com.android.launcher3" || 
                        pkg == "com.google.android.apps.nexuslauncher" ||
                        (pkg.contains("launcher", ignoreCase = true) && !pkg.contains("service", ignoreCase = true))) 2 else 0
                } else 0
            }

            if (activityType == 2) {
                onDestroy()
                return
            }
            
            updateTask(taskInfo)
        }
    }

    inner class RotationWatcher : IRotationWatcher.Stub() {
        override fun onRotationChanged(rotation: Int) {
            runMain {
                if (rotateLock.not())
                    rotate(rotation)
            }
        }
    }

    fun rotate(rotation: Int) {
        val isLandscape = rotation == 1 || rotation == 3
        val newOrientation = if (isLandscape) 1 else 0
        
        if (orientation != newOrientation) {
            orientation = newOrientation
            
            // Swap surface view dimensions if transitioning between portrait and landscape
            val surfaceWidth = surfaceView.width
            val surfaceHeight = surfaceView.height
            binding.vSizePreviewer.updateLayoutParams {
                width = surfaceHeight
                height = surfaceWidth
            }
            surfaceView.updateLayoutParams {
                width = surfaceHeight
                height = surfaceWidth
            }

            // Update bar controllers visibility
            if (orientation == 0) { // Portrait
                binding.rlBarControllerSide.isVisible = false
                binding.rlBarControllerBottom.isVisible = true
            } else { // Landscape
                binding.rlBarControllerSide.isVisible = true
                binding.rlBarControllerBottom.isVisible = false
            }
            
            // After rotation, make sure the window is still in screen
            keepInScreen(animate = false)
        }
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        if (isMini.not() && isCollapsed.not()) {
            newDpi = calculateDpi(width, height, calculateScreenInches(width, height)) - config.reduceDPI
            virtualDisplay.resize(width, height, newDpi)
            surface.setDefaultBufferSize(width, height)
            halfWidth = width % 2
            halfHeight = height % 2
        } else {
            newDpi = calculateDpi(width, height, calculateScreenInches(width, height)) - config.reduceDPI
            virtualDisplay.resize(width * 2 + halfWidth, height * 2 + halfHeight, newDpi)
            surface.setDefaultBufferSize(width * 2 + halfWidth, height * 2 + halfHeight)
        }
        virtualDisplay.surface = Surface(surface)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        if (isResize) {
            if (isMini.not()) {
                newDpi = calculateDpi(width, height, calculateScreenInches(width, height)) - config.reduceDPI
                virtualDisplay.resize(width, height, newDpi)
                surface.setDefaultBufferSize(width, height)
                halfWidth = width % 2
                halfHeight = height % 2
            } else {
                newDpi = calculateDpi(width, height, calculateScreenInches(width, height)) - config.reduceDPI
                virtualDisplay.resize(width * 2 + halfWidth, height * 2 + halfHeight, newDpi)
                surface.setDefaultBufferSize(width * 2 + halfWidth, height * 2 + halfHeight)
            }
        }
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {

    }

    private fun keepInScreen(animate: Boolean = true) {
        binding.root.post {
            val params = binding.root.layoutParams as WindowManager.LayoutParams
            val displayMetrics = context.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels

            val windowWidth = binding.root.width
            val windowHeight = binding.root.height

            val minX = 0
            val minY = 0
            val maxX = screenWidth - windowWidth
            val maxY = screenHeight - windowHeight

            val targetX = params.x.coerceIn(minX, maxX)
            val targetY = params.y.coerceIn(minY, maxY)

            if (targetX == params.x && targetY == params.y) return@post

            keepInScreenAnimator?.cancel()

            if (animate) {
                val startX = params.x
                val startY = params.y
                keepInScreenAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 300
                    interpolator = DecelerateInterpolator()
                    addUpdateListener { animation ->
                        val fraction = animation.animatedValue as Float
                        params.x = (startX + (targetX - startX) * fraction).toInt()
                        params.y = (startY + (targetY - startY) * fraction).toInt()
                        runCatching {
                            Instances.windowManager.updateViewLayout(binding.root, params)
                        }
                    }
                    start()
                }
            } else {
                params.x = targetX
                params.y = targetY
                runCatching {
                    Instances.windowManager.updateViewLayout(binding.root, params)
                }
            }
        }
    }

    private fun animateResizeCentered(
        view: View,
        startWidth: Int,
        endWidth: Int,
        startHeight: Int,
        endHeight: Int,
        onEnd: (() -> Unit)? = null
    ) {
        val params = binding.root.layoutParams as WindowManager.LayoutParams
        val initialWindowWidth = binding.root.width
        val initialWindowHeight = binding.root.height
        val initialViewWidth = if (view.width > 0) view.width else startWidth
        val initialViewHeight = if (view.height > 0) view.height else startHeight
        
        val centerX = params.x + initialWindowWidth / 2
        val centerY = params.y + initialWindowHeight / 2

        animateResize(
            view, startWidth, endWidth, startHeight, endHeight, context,
            onUpdate = { currentViewWidth, currentViewHeight ->
                val currentParams = binding.root.layoutParams as WindowManager.LayoutParams
                val displayMetrics = context.resources.displayMetrics
                val screenWidth = displayMetrics.widthPixels
                val screenHeight = displayMetrics.heightPixels
                
                val currentWindowWidth = initialWindowWidth + (currentViewWidth - initialViewWidth)
                val currentWindowHeight = initialWindowHeight + (currentViewHeight - initialViewHeight)
                
                // Keep window within screen boundaries during resize
                val targetX = centerX - currentWindowWidth / 2
                val targetY = centerY - currentWindowHeight / 2
                
                currentParams.x = targetX.coerceIn(0, screenWidth - currentWindowWidth)
                currentParams.y = targetY.coerceIn(0, screenHeight - currentWindowHeight)
                
                runCatching {
                    Instances.windowManager.updateViewLayout(binding.root, currentParams)
                }
            },
            onEnd = onEnd
        )
    }

    // minimizes the floating window a bar-less only-content floating window
    private fun changeMini() {
        isCollapsed = false
        isResize = false

        if (isMini) {
            isMini = false
            isResize = true
            binding.rootClickMask.visibility = View.GONE

            if (surfaceView is SurfaceView) {
                binding.cvBackground.updateLayoutParams {
                    width = originalWidth
                    height = originalHeight
                }
                setBackgroundWrapContent()
                setParrentWrapContent()
                keepInScreen()
                if (orientation == 0) {
                    binding.rlBarControllerBottom.visibility = View.VISIBLE
                    binding.rlBarControllerSide.visibility = View.GONE
                } else {
                    binding.rlBarControllerSide.visibility = View.VISIBLE
                    binding.rlBarControllerBottom.visibility = View.GONE
                }
            } else {
                binding.cvBackground.updateLayoutParams {
                    width = originalWidth
                    height = originalHeight
                }
                animateScaleThenResize(
                    binding.cvBackground,
                    0.5F, 0.5F,
                    1F, 1F,
                    0.5F, 0.5F,
                    originalWidth, originalHeight,
                    context
                ){
                    setBackgroundWrapContent()
                    setParrentWrapContent()
                    bindingLeftBackGesture.root.visibility = View.VISIBLE
                    bindingRightBackGesture.root.visibility = View.VISIBLE
                    keepInScreen()
                }
            }

            binding.ibRightResize.visibility = View.VISIBLE
            if (orientation == 0) {
                binding.rlBarControllerBottom.visibility = View.VISIBLE
            } else {
                binding.rlBarControllerSide.visibility = View.VISIBLE
            }
            surfaceView.visibility = View.VISIBLE
            surfaceView.setOnTouchListener(surfaceOnTouchListener)
            surfaceView.setOnGenericMotionListener(surfaceOnGenericMotionListener)

            return
        }
        else if (!isMini) {
            binding.rootClickMask.visibility = View.VISIBLE
            binding.rlBarControllerBottom.visibility = View.GONE
            binding.rlBarControllerSide.visibility = View.GONE
            isMini = true

            if (config.surfaceView == 1) {
                binding.cvBackground.updateLayoutParams {
                    width = originalWidth/2
                    height = originalHeight/2
                }
                keepInScreen()
            } else {
                animateResizeCentered(
                    binding.cvBackground,
                    originalWidth, originalWidth/2,
                    originalHeight, originalHeight/2
                ){
                    isResize = true
                    bindingLeftBackGesture.root.visibility = View.GONE
                    bindingRightBackGesture.root.visibility = View.GONE
                    keepInScreen()
                }
            }

            binding.ibRightResize.visibility = View.GONE
            surfaceView.setOnTouchListener(null)
            surfaceView.setOnGenericMotionListener(null)

            return
        }
    }

    private fun setBackgroundWrapContent() {
        val layoutParams = binding.cvBackground.layoutParams
        layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT
        layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
        binding.cvBackground.layoutParams = layoutParams
    }

    private fun setParrentWrapContent() {
        val layoutParams = binding.cvParent.layoutParams
        layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT
        layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
        binding.cvParent.layoutParams = layoutParams
    }

    private fun changeCollapsed() {
        isResize = false
        if (isCollapsed) {
            binding.rootClickMask.visibility = View.GONE
            expandWindow()
            bindingLeftBackGesture.root.visibility = View.VISIBLE
            bindingRightBackGesture.root.visibility = View.VISIBLE
        } else {
            binding.rootClickMask.visibility = View.VISIBLE
            binding.rlBarControllerBottom.visibility = View.GONE
            binding.rlBarControllerSide.visibility = View.GONE
            collapseWindow()
            bindingLeftBackGesture.root.visibility = View.GONE
            bindingRightBackGesture.root.visibility = View.GONE
        }
    }

    private fun expandWindow() {
        isCollapsed = false
        binding.background.visibility = View.VISIBLE

        animateResizeCentered(
            binding.appIcon, 40.dpToPx().toInt(), 0, 40.dpToPx().toInt(), 0) {
            binding.cvappIcon.visibility = View.GONE
            animateResizeCentered(binding.cvBackground, 0, originalWidth, 0, originalHeight) {
                setBackgroundWrapContent()
                setParrentWrapContent()
                binding.cvappIcon.visibility = View.VISIBLE

                CoroutineScope(Dispatchers.Main).launch {
                    delay(200)

                    if (orientation == 0) {
                        binding.rlBarControllerBottom.visibility = View.VISIBLE
                    } else {
                        binding.rlBarControllerSide.visibility = View.VISIBLE
                    }
                }

                binding.cvappIcon.visibility = View.GONE
                isResize = true
                keepInScreen()
            }
        }
    }

    private fun collapseWindow() {
        isCollapsed = true

        CoroutineScope(Dispatchers.Main).launch {
            delay(200)

            animateResizeCentered(binding.cvBackground, binding.cvBackground.width, 0, binding.cvBackground.height, 0) {
                binding.cvappIcon.visibility = View.VISIBLE
                binding.cvappIcon.radius = 20.dpToPx() // 使其变成圆球 (40dp的一半)
                binding.background.visibility = View.GONE
                animateResizeCentered(binding.appIcon, 0, 40.dpToPx().toInt(), 0, 40.dpToPx().toInt()) {
                    keepInScreen()
                }

                isResize = true
            }
        }
    }

    private fun calculateScreenInches(width: Int, height: Int): Float {
        val x = (width / context.resources.displayMetrics.xdpi).pow(2)
        val y = (height / context.resources.displayMetrics.ydpi).pow(2)

        return sqrt(x + y)
    }

    private fun calculateDpi(width: Int, height: Int, screenSizeInInches: Float): Int {
        val widthSqr = width.toFloat().pow(2)
        val heightSqr = height.toFloat().pow(2)
        val diagonalPixels = sqrt(widthSqr + heightSqr)

        return floor(diagonalPixels / screenSizeInInches).toInt()
    }

    private fun rightResize(ibResize: ImageButton) {
        ibResize.setOnTouchListener(object : View.OnTouchListener {
            var beginX = 0F
            var beginY = 0F
            var beginWidth = 0
            var beginHeight = 0

            var offsetX = 0F
            var offsetY = 0F

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when(event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        beginX = event.rawX
                        beginY = event.rawY
                        binding.vSizePreviewer.layoutParams.let {
                            beginWidth = it.width
                            beginHeight = it.height
                        }
                        binding.vSizePreviewer.visibility = View.VISIBLE
                        binding.cvParent.strokeWidth = 0
                    }
                    MotionEvent.ACTION_MOVE -> {
                        offsetX = event.rawX - beginX
                        offsetY = event.rawY - beginY
                        binding.vSizePreviewer.updateLayoutParams {
                            val targetWidth = beginWidth + offsetX.toInt()
                            if (targetWidth > 0)
                                width = targetWidth
                            val targetHeight = beginHeight + offsetY.toInt()
                            if (targetHeight > 0)
                                height = targetHeight
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        binding.vSizePreviewer.post {
                            surfaceView.updateLayoutParams {
                                width = binding.vSizePreviewer.width
                                height = binding.vSizePreviewer.height
                            }
                        }

                        binding.vSizePreviewer.visibility = View.GONE
                        moveToTopIfNeed(event)
                        binding.cvParent.strokeWidth = 2.dpToPx().toInt()
                        keepInScreen()
                    }
                }
                return true
            }
        })
    }

    fun forwardMotionEvent(event: MotionEvent) {
        if (!isSuperShown) {
            val newEvent = MotionEvent.obtain(event)
            newEvent.invokeMethod("setDisplayId", args(displayId), argTypes(Integer.TYPE))
            Instances.inputManager.injectInputEvent(newEvent, 0)
            newEvent.recycle()
        }
    }

    inner class SurfaceOnTouchListener : View.OnTouchListener {
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            bindingLeftBackGesture.root.visibility = View.VISIBLE
            bindingRightBackGesture.root.visibility = View.VISIBLE
            forwardMotionEvent(event)
            moveToTopIfNeed(event)
            return true
        }
    }

    inner class SurfaceOnGenericMotionListener : View.OnGenericMotionListener {
        override fun onGenericMotion(v: View, event: MotionEvent): Boolean {
            bindingLeftBackGesture.root.visibility = View.VISIBLE
            bindingRightBackGesture.root.visibility = View.VISIBLE
            forwardMotionEvent(event)
            return true
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        virtualDisplay.surface = holder.surface
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        newDpi = calculateDpi(width, height, calculateScreenInches(width, height )) - config.reduceDPI
        virtualDisplay.resize(width, height, newDpi)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        virtualDisplay.surface = null
    }

    private val moveGestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        var startX = 0
        var startY = 0
        var xAnimation: FlingAnimation? = null
        var yAnimation: FlingAnimation? = null
        private var keepInScreenAnimator: ValueAnimator? = null
        var lastX = 0F
        var lastY = 0F
        var last2X = 0F
        var last2Y = 0F

        override fun onDown(e: MotionEvent): Boolean {
            xAnimation?.cancel()
            yAnimation?.cancel()
            keepInScreenAnimator?.cancel()
            val params = binding.root.layoutParams as WindowManager.LayoutParams
            startX = params.x
            startY = params.y
            currentHighlightedCorner = -1
            return true
        }


        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            e1 ?: return false
            val params = binding.root.layoutParams as WindowManager.LayoutParams
            params.x = (startX + (e2.rawX - e1.rawX)).toInt()
            params.y = (startY + (e2.rawY - e1.rawY)).toInt()
            Instances.windowManager.updateViewLayout(binding.root, params)
            last2X = lastX
            last2Y = lastY
            lastX = e2.rawX
            lastY = e2.rawY

            showCornerDropZone()
            currentHighlightedCorner = cornerDropZoneView?.updateHighlight(e2.rawX, e2.rawY) ?: -1
            return true
        }

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            e1 ?: return false
            if (e1.source == InputDevice.SOURCE_MOUSE) return false
            val params = binding.root.layoutParams as WindowManager.LayoutParams
            val displayMetrics = context.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            val windowWidth = binding.root.width
            val windowHeight = binding.root.height

            val minX = 0f
            val maxX = (screenWidth - windowWidth).toFloat()
            val minY = 0f
            val maxY = (screenHeight - windowHeight).toFloat()

            runCatching {
                if (sign(velocityX) != sign(e2.rawX - last2X)) return@runCatching
                xAnimation = flingAnimationOf({
                    params.x = it.toInt()
                    runCatching { Instances.windowManager.updateViewLayout(binding.root, params) }
                }, {
                    params.x.toFloat()
                })
                    .setStartVelocity(velocityX)
                    // If already outside, don't set boundaries here to avoid instant snap.
                    // keepInScreen() will handle it after the fling or on release.
                    .apply {
                        if (params.x >= minX && params.x <= maxX) {
                            setMinValue(minX)
                            setMaxValue(maxX)
                        }
                    }
                xAnimation?.addEndListener { _, _, _, _ ->
                    keepInScreen()
                }
                xAnimation?.start()
            }
            runCatching {
                if (sign(velocityY) != sign(e2.rawY - last2Y)) return@runCatching
                yAnimation = flingAnimationOf({
                    params.y = it.toInt()
                    runCatching { Instances.windowManager.updateViewLayout(binding.root, params) }
                }, {
                    params.y.toFloat()
                })
                    .setStartVelocity(velocityY)
                    .apply {
                        if (params.y >= minY && params.y <= maxY) {
                            setMinValue(minY)
                            setMaxValue(maxY)
                        }
                    }
                yAnimation?.addEndListener { _, _, _, _ ->
                    keepInScreen()
                }
                yAnimation?.start()
            }
            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (isCollapsed) {
                isMini = false // Always restore to Normal window from Collapsed state
                changeCollapsed()
            } else if (isMini) {
                changeMini()
            }
            return true
        }
    })

    private fun showCornerDropZone() {
        if (cornerDropZoneView == null) {
            cornerDropZoneView = CornerDropZoneView(context)
            val lp = WindowManager.LayoutParams().apply {
                type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                format = PixelFormat.TRANSLUCENT
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.MATCH_PARENT
            }
            Instances.windowManager.addView(cornerDropZoneView, lp)
        }
        cornerDropZoneView?.visibility = View.VISIBLE
    }

    private fun hideCornerDropZone() {
        cornerDropZoneView?.visibility = View.GONE
    }

    private inner class CornerDropZoneView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private fun getRealScreenMetrics(): android.util.DisplayMetrics {
            val dm = android.util.DisplayMetrics()
            context.display?.getRealMetrics(dm)
            return dm
        }

        private val radius: Float
            get() {
                val dm = getRealScreenMetrics()
                return if (dm.widthPixels > dm.heightPixels) {
                    dm.heightPixels * 0.20f
                } else {
                    dm.widthPixels * 0.20f
                }
            }
        private val proximityRadius: Float
            get() = radius * 1.75f
        
        private var currentCorner = -1 // -1: none, 0: TL, 1: TR, 2: BL, 3: BR
        private var isInsideActiveZone = false

        init {
            paint.style = Paint.Style.FILL
            dashPaint.style = Paint.Style.STROKE
            dashPaint.strokeWidth = 2f.dpToPx()
            dashPaint.pathEffect = DashPathEffect(floatArrayOf(10f.dpToPx(), 10f.dpToPx()), 0f)
        }

        fun updateHighlight(rawX: Float, rawY: Float): Int {
            if (isCollapsed) return -1 // Collapsed mode: no edge interaction

            val dm = getRealScreenMetrics()
            val sw = dm.widthPixels.toFloat()
            val sh = dm.heightPixels.toFloat()
            
            val x = rawX
            val y = rawY
            
            if (sw == 0f || sh == 0f) return -1

            // Calculate distance to each corner using orientation-corrected coordinates
            val distTL = sqrt(x.pow(2) + y.pow(2))
            val distTR = sqrt((sw - x).pow(2) + y.pow(2))
            val distBL = sqrt(x.pow(2) + (sh - y).pow(2))
            val distBR = sqrt((sw - x).pow(2) + (sh - y).pow(2))

            val minDist = min(distTL, min(distTR, min(distBL, distBR)))
            
            val corner = if (!isMini) { // Only Normal mode allows corner interaction
                when {
                    distTL == minDist && distTL < proximityRadius -> 0
                    distTR == minDist && distTR < proximityRadius -> 1
                    distBL == minDist && distBL < proximityRadius -> 2
                    distBR == minDist && distBR < proximityRadius -> 3
                    else -> -1
                }
            } else -1

            if (corner != -1) {
                currentCorner = corner
                isInsideActiveZone = minDist < radius
            } else {
                // Check sides: 4 for Left, 5 for Right. Both Normal and Mini modes allow side interaction
                currentCorner = when {
                    x < proximityRadius -> 4
                    x > sw - proximityRadius -> 5
                    else -> -1
                }
                // Active zone: 5% width, 80% height centered
                isInsideActiveZone = when (currentCorner) {
                    4 -> x < sw * 0.05f && y > sh * 0.1f && y < sh * 0.9f
                    5 -> x > sw * 0.95f && y > sh * 0.1f && y < sh * 0.9f
                    else -> false
                }
            }

            invalidate()
            return if (isInsideActiveZone) currentCorner else -1
        }

        override fun onDraw(canvas: Canvas) {
            val dm = getRealScreenMetrics()
            val w = dm.widthPixels.toFloat()
            val h = dm.heightPixels.toFloat()
            if (w == 0f || h == 0f || currentCorner == -1) return

            when (currentCorner) {
                0 -> drawCorner(canvas, 0f, 0f, 0f, 90f) // TL
                1 -> drawCorner(canvas, w, 0f, 90f, 90f) // TR
                2 -> drawCorner(canvas, 0f, h, 270f, 90f) // BL
                3 -> drawCorner(canvas, w, h, 180f, 90f) // BR
                4 -> drawSide(canvas, 0f, h * 0.1f, w * 0.05f, h * 0.9f) // Left
                5 -> drawSide(canvas, w * 0.95f, h * 0.1f, w, h * 0.9f) // Right
            }
        }

        private fun drawCorner(canvas: Canvas, cx: Float, cy: Float, startAngle: Float, sweepAngle: Float) {
            val color = if (isInsideActiveZone) Color.parseColor("#CC61D4FF") else Color.parseColor("#40FFFFFF")
            val strokeColor = if (isInsideActiveZone) Color.parseColor("#FF61D4FF") else Color.WHITE

            paint.color = color
            dashPaint.color = strokeColor

            val rectF = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
            canvas.drawArc(rectF, startAngle, sweepAngle, true, paint)
            canvas.drawArc(rectF, startAngle, sweepAngle, true, dashPaint)
        }

        private fun drawSide(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
            val color = if (isInsideActiveZone) Color.parseColor("#CC61D4FF") else Color.parseColor("#40FFFFFF")
            val strokeColor = if (isInsideActiveZone) Color.parseColor("#FF61D4FF") else Color.WHITE

            paint.color = color
            dashPaint.color = strokeColor

            val rectF = RectF(left, top, right, bottom)
            val rx = 10f.dpToPx()
            canvas.drawRoundRect(rectF, rx, rx, paint)
            canvas.drawRoundRect(rectF, rx, rx, dashPaint)
        }
    }
}

