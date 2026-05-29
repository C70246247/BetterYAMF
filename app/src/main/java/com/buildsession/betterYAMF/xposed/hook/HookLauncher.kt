package com.buildsession.betterYAMF.xposed.hook

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AndroidAppHelper
import android.app.PendingIntent
import android.app.RemoteAction
import android.content.ComponentName
import android.content.Intent
import android.content.res.Resources
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.RoundRectShape
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.drawable.toBitmap
import com.github.kyuubiran.ezxhelper.init.EzXHelperInit
import com.github.kyuubiran.ezxhelper.init.InitFields.moduleRes
import com.github.kyuubiran.ezxhelper.utils.argTypes
import com.github.kyuubiran.ezxhelper.utils.args
import com.github.kyuubiran.ezxhelper.utils.findAllMethods
import com.github.kyuubiran.ezxhelper.utils.findConstructor
import com.github.kyuubiran.ezxhelper.utils.findField
import com.github.kyuubiran.ezxhelper.utils.findMethod
import com.github.kyuubiran.ezxhelper.utils.findMethodOrNull
import com.github.kyuubiran.ezxhelper.utils.getObject
import com.github.kyuubiran.ezxhelper.utils.hookAfter
import com.github.kyuubiran.ezxhelper.utils.hookBefore
import com.github.kyuubiran.ezxhelper.utils.hookReplace
import com.github.kyuubiran.ezxhelper.utils.hookReturnConstant
import com.github.kyuubiran.ezxhelper.utils.invokeMethod
import com.github.kyuubiran.ezxhelper.utils.invokeMethodAuto
import com.github.kyuubiran.ezxhelper.utils.invokeMethodAutoAs
import com.github.kyuubiran.ezxhelper.utils.loadClass
import com.github.kyuubiran.ezxhelper.utils.loadClassOrNull
import com.github.kyuubiran.ezxhelper.utils.newInstance
import com.github.kyuubiran.ezxhelper.utils.paramCount
import com.buildsession.betterYAMF.R
import com.buildsession.betterYAMF.xposed.services.YAMFManager
import com.buildsession.betterYAMF.xposed.utils.dpToPx
import com.buildsession.betterYAMF.xposed.utils.log
import com.buildsession.betterYAMF.xposed.utils.registerReceiver
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.reflect.Proxy


class HookLauncher : IXposedHookLoadPackage, IXposedHookZygoteInit {
    companion object {
        const val TAG = "BetterYAMF_HookLauncher"
        const val ACTION_RECEIVE_LAUNCHER_CONFIG =
            "com.buildsession.betterYAMF.ACTION_RECEIVE_LAUNCHER_CONFIG"

        const val EXTRA_HOOK_RECENT = "hookRecent"
        const val EXTRA_HOOK_TASKBAR = "hookTaskbar"
        const val EXTRA_HOOK_POPUP = "hookPopup"
        const val EXTRA_HOOK_TRANSIENT_TASKBAR = "hookTransientTaskbar"
    }

    private var isRegistered = false

    private var mDropZoneView: View? = null
    private val mDropZoneRect = Rect()
    private val mMainHandler: Handler by lazy { Handler(Looper.getMainLooper()) }
    private var mScreenHeight = 0
    private var mScreenWidth = 0
    private var mStartY = 0f
    private var mIsShowingZone = false
    private var mCurrentTaskId = -1

    private var mCurrentRotation = 0
    private var mIsAlreadyVisual = true
    private var mIsPotentialSwipeUp = false

    private fun updateDimensions(context: android.content.Context) {
        val wm = context.getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager
        val display = wm.defaultDisplay
        val displayMetrics = android.util.DisplayMetrics()
        display.getRealMetrics(displayMetrics)
        
        val newWidth = displayMetrics.widthPixels
        val newHeight = displayMetrics.heightPixels
        
        if (newWidth != mScreenWidth || newHeight != mScreenHeight) {
            mScreenWidth = newWidth
            mScreenHeight = newHeight
            
            // 横屏下让释放区更宽一些，竖屏下保持原
            val zoneWidthPercent = if (mScreenWidth > mScreenHeight) 0.5 else 0.6
            val zoneHeightPercent = if (mScreenWidth > mScreenHeight) 0.4 else 0.3
            
            val zoneWidth = (mScreenWidth * zoneWidthPercent).toInt()
            val zoneHeight = (mScreenHeight * zoneHeightPercent).toInt()
            mDropZoneRect.set(mScreenWidth - zoneWidth, 0, mScreenWidth, zoneHeight)
            
            mMainHandler.post {
                mDropZoneView?.let { view ->
                    val lp = view.layoutParams as? WindowManager.LayoutParams ?: return@post
                    lp.width = zoneWidth
                    lp.height = zoneHeight
                    wm.updateViewLayout(view, lp)
                }
            }
            // log(TAG, "Dimensions updated: ${mScreenWidth}x${mScreenHeight}, Zone: $mDropZoneRect")
        }
    }

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        EzXHelperInit.initZygote(startupParam)
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        EzXHelperInit.initHandleLoadPackage(lpparam)
        
        // Pixel Launcher: com.google.android.apps.nexuslauncher
        // Launcher3: com.android.launcher3
        val isLauncher = lpparam.packageName == "com.android.launcher3" || 
                        lpparam.packageName == "com.google.android.apps.nexuslauncher" ||
                        lpparam.packageName.contains("launcher")
        
        if (!isLauncher) return
        // log(TAG, "Handling package: ${lpparam.packageName} (Process: ${lpparam.processName})")

        // 尝试加载 TouchInteractionService，Pixel Launcher 可能会继承或直接使用该类
        val tisClass = loadClassOrNull("com.android.quickstep.TouchInteractionService")
        if (tisClass != null) {
            // log(TAG, "Found TouchInteractionService in ${lpparam.packageName}")
            findMethod(tisClass) { name == "onCreate" }.hookAfter { param ->
                // log(TAG, "TouchInteractionService#onCreate hooked in ${lpparam.packageName}")
                val context = param.thisObject as android.app.Service
                
                // 确保在主线程执行 UI 操作
                mMainHandler.post {
                    try {
                        updateDimensions(context)
                        val zoneWidth = mDropZoneRect.width()
                        val zoneHeight = mDropZoneRect.height()

                        if (mDropZoneView == null) {
                            mDropZoneView = TextView(context).apply {
                                visibility = View.GONE
                                gravity = Gravity.CENTER
                                text = "Drag here to open in window"
                                setTextColor(Color.WHITE)
                                textSize = 16f
                                setPadding(20, 20, 20, 20)
                            }
                            updateDropZoneColor(false)

                            val lp = WindowManager.LayoutParams().apply {
                                type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                format = PixelFormat.TRANSLUCENT
                                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                                width = zoneWidth
                                height = zoneHeight
                                gravity = Gravity.TOP or Gravity.END
                            }

                            val windowManager =
                                context.getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager
                            windowManager.addView(mDropZoneView, lp)
                            // log(TAG, "Successfully added mDropZoneView to WindowManager")
                        }
                    } catch (e: Exception) {
                        log(TAG, "Error creating drop zone view", e)
                    }
                }
            }

            // 使用更通用的方式 Hook onInputEvent
            XposedBridge.hookAllMethods(tisClass, "onInputEvent", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val event = param.args[0] as? MotionEvent ?: return
                    val action = event.actionMasked
                    if (action != MotionEvent.ACTION_DOWN && 
                        action != MotionEvent.ACTION_MOVE && 
                        action != MotionEvent.ACTION_UP &&
                        action != MotionEvent.ACTION_CANCEL) return

                    val context = param.thisObject as android.app.Service
                    
                    // 仅在 DOWN 时更新屏幕信息，减少开销
                    if (action == MotionEvent.ACTION_DOWN) {
                        updateDimensions(context)
                        val wm = context.getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager
                        mCurrentRotation = wm.defaultDisplay.rotation
                        val rawX = event.rawX
                        val rawY = event.rawY
                        
                        mIsAlreadyVisual = if (mCurrentRotation == 0) {
                            true
                        } else if (mScreenWidth > mScreenHeight) {
                            rawX > mScreenHeight + 100 || rawY > mScreenHeight + 100
                        } else {
                            rawX > mScreenWidth + 100 || rawY > mScreenWidth + 100
                        }
                    }

                    val rawX = event.rawX
                    val rawY = event.rawY
                    val correctedX: Float
                    val correctedY: Float

                    if (mIsAlreadyVisual) {
                        correctedX = rawX
                        correctedY = rawY
                    } else {
                        if (mCurrentRotation == 1) { // ROTATION_90 (Landscape CCW)
                            correctedX = rawY
                            correctedY = mScreenHeight.toFloat() - rawX
                        } else if (mCurrentRotation == 3) { // ROTATION_270 (Landscape CW)
                            correctedX = mScreenWidth.toFloat() - rawY
                            correctedY = rawX
                        } else {
                            correctedX = rawX
                            correctedY = rawY
                        }
                    }

                    val isInZone = mDropZoneRect.contains(correctedX.toInt(), correctedY.toInt())

                    when (action) {
                        MotionEvent.ACTION_DOWN -> {
                            mStartY = correctedY
                            mIsShowingZone = false
                            mCurrentTaskId = -1
                            // 只有从底部 15% 区域开始的滑动才被认为是潜在的上划手势
                            mIsPotentialSwipeUp = correctedY > mScreenHeight * 0.85
                            // log(TAG, "ACTION_DOWN at ($correctedX, $correctedY), potential=$mIsPotentialSwipeUp")
                        }

                        MotionEvent.ACTION_MOVE -> {
                            if (!mIsPotentialSwipeUp) return
                            
                            val diffY = mStartY - correctedY
                            val threshold = mScreenHeight * 0.2
                            
                            if (diffY > threshold) {
                                if (!mIsShowingZone) {
                                    // 仅在达到阈值时才进行反射调用，大幅减少性能损耗
                                    var capturedTaskId = -1
                                    runCatching {
                                        val gestureState = XposedHelpers.getObjectField(param.thisObject, "mGestureState")
                                        if (gestureState != null) {
                                            val taskId = XposedHelpers.callMethod(gestureState, "getTopRunningTaskId") as Int
                                            val runningTask = XposedHelpers.callMethod(gestureState, "getRunningTask")
                                            val isHomeTask = if (runningTask != null) {
                                                XposedHelpers.callMethod(runningTask, "isHomeTask") as Boolean
                                            } else false
                                            
                                            if (taskId != -1 && !isHomeTask) {
                                                capturedTaskId = taskId
                                            }
                                        }
                                    }

                                    if (capturedTaskId != -1) {
                                        mCurrentTaskId = capturedTaskId
                                        mIsShowingZone = true
                                        mMainHandler.post {
                                            mDropZoneView?.visibility = View.VISIBLE
                                        }
                                        // log(TAG, "Swipe up threshold reached with taskId $mCurrentTaskId")
                                    } else {
                                        // 如果不是有效的任务（例如在桌面），则不再处理后续 MOVE
                                        mIsPotentialSwipeUp = false
                                    }
                                }
                            } else {
                                if (mIsShowingZone) {
                                    mIsShowingZone = false
                                    mMainHandler.post {
                                        mDropZoneView?.visibility = View.GONE
                                    }
                                }
                            }
                            
                            if (mIsShowingZone) {
                                updateDropZoneColor(isInZone)
                            }
                        }

                        MotionEvent.ACTION_UP -> {
                                // log(TAG, "ACTION_UP at ($correctedX, $correctedY), isInZone=$isInZone, isShowing=$mIsShowingZone, taskId=$mCurrentTaskId")
                                if (mIsShowingZone && isInZone && mCurrentTaskId != -1) {
                                    val intent = Intent(YAMFManager.ACTION_OPEN_IN_YAMF).apply {
                                        setPackage("android")
                                        putExtra(YAMFManager.EXTRA_TASK_ID, mCurrentTaskId)
                                        putExtra(YAMFManager.EXTRA_SOURCE, YAMFManager.SOURCE_RECENT)
                                    }
                                    AndroidAppHelper.currentApplication().sendBroadcast(intent)
                                    // log(TAG, "Sent broadcast to open task $mCurrentTaskId in YAMF")
                                    
                                    mMainHandler.post {
                                        Toast.makeText(
                                            mDropZoneView?.context ?: AndroidAppHelper.currentApplication(),
                                            "Task $mCurrentTaskId windowed!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                                mMainHandler.post {
                                    mDropZoneView?.visibility = View.GONE
                                }
                                mIsShowingZone = false
                                mCurrentTaskId = -1
                            }

                            MotionEvent.ACTION_CANCEL -> {
                                // log(TAG, "ACTION_CANCEL")
                                mMainHandler.post {
                                    mDropZoneView?.visibility = View.GONE
                                }
                                mIsShowingZone = false
                            }
                        }
                }
            })

            findMethod(tisClass) { name == "onDestroy" }.hookBefore {
                // log(TAG, "TouchInteractionService#onDestroy hooked")
                mMainHandler.post {
                    val context = it.thisObject as android.app.Service
                    val windowManager =
                        context.getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager
                    mDropZoneView?.let { view ->
                        try {
                            windowManager.removeView(view)
                            // log(TAG, "Removed mDropZoneView from WindowManager")
                        } catch (e: Exception) {
                            log(TAG, "Error removing drop zone view", e)
                        }
                        mDropZoneView = null
                    }
                }
            }
        }

        findMethod("com.android.launcher3.Launcher") {
            name == "onCreate"
        }.hookAfter {
            if (!isRegistered) {
                val activity = it.thisObject as Activity
                val application = activity.application
                application.registerReceiver(ACTION_RECEIVE_LAUNCHER_CONFIG) { _, intent ->
                    val hookRecent = intent.getBooleanExtra(EXTRA_HOOK_RECENT, false)
                    val hookTaskbar = intent.getBooleanExtra(EXTRA_HOOK_TASKBAR, false)
                    val hookPopup = intent.getBooleanExtra(EXTRA_HOOK_POPUP, false)
                    val hookTransientTaskbar =
                        intent.getBooleanExtra(EXTRA_HOOK_TRANSIENT_TASKBAR, false)
                    /* log(
                        TAG,
                        "receive config hookRecent=$hookRecent hookTaskbar=$hookTaskbar hookPopup=$hookPopup hookTranslucentTaskbar=$hookTransientTaskbar"
                    ) */
                    if (hookRecent) runCatching { hookRecent(lpparam) }.onFailure { e ->
                        log(TAG, "hook recent failed", e) }
                    if (hookTaskbar) runCatching { hookTaskbar(lpparam) }.onFailure { e ->
                        log(TAG, "hook taskbar failed", e) }
                    if (hookPopup) runCatching { hookPopup(lpparam) }.onFailure { e ->
                        log(TAG, "hook popup failed", e) }
                    if (hookTransientTaskbar) runCatching { hookTransientTaskbar(lpparam) }.onFailure { e ->
                        log(TAG, "hook transient failed", e) }
                    application.unregisterReceiver(this)
                }
                application.sendBroadcast(Intent(YAMFManager.ACTION_GET_LAUNCHER_CONFIG).apply {
                    `package` = "android"
                    putExtra("sender", application.packageName)
                })

                isRegistered = true
            }
        }
    }

    private fun hookRecent(lpparam: XC_LoadPackage.LoadPackageParam) {
        // log(TAG, "hooking recent ${lpparam.packageName}")
        XposedBridge.hookAllMethods(
            XposedHelpers.findClass(
                "com.android.quickstep.TaskOverlayFactory",
                lpparam.classLoader
            ), "getEnabledShortcuts", object : XC_MethodHook() {
                @SuppressLint("UseCompatLoadingForDrawables")
                override fun afterHookedMethod(param: MethodHookParam) {
                    val taskView = param.args[0] as View
                    val shortcuts = param.result as MutableList<Any>
                    val itemInfo = XposedHelpers.getObjectField(shortcuts[0], "mItemInfo")

                    var task: Any = Unit

                    runCatching {
                        task = XposedHelpers.callMethod(taskView, "getTask")
                    }.onFailure {
                        val taskContainers = XposedHelpers.getObjectField(taskView, "taskContainers") as List<*>
                        val firstContainer = taskContainers[0]
                        task = XposedHelpers.getObjectField(firstContainer, "task")
                    }

                    val activity = taskView.context
                    val key = XposedHelpers.getObjectField(task, "key")
                    val taskId = XposedHelpers.getIntField(key, "id")

                    val userId = XposedHelpers.getIntField(key, "userId")

                    val classRemoteActionShortcut = XposedHelpers.findClass(
                        "com.android.launcher3.popup.RemoteActionShortcut",
                        lpparam.classLoader
                    )

                    val intent = Intent(YAMFManager.ACTION_OPEN_IN_YAMF).apply {
                        setPackage("android")
                    }

                    runCatching {
                        val itemInfoTmp =
                            itemInfo.javaClass.newInstance(args(itemInfo), argTypes(itemInfo.javaClass))
                        val topComponent = XposedHelpers.callMethod(itemInfoTmp, "getTargetComponent") as ComponentName
                        intent.putExtra(YAMFManager.EXTRA_COMPONENT_NAME, topComponent)
                    }.onFailure {
                        val topComponent = extractComponentInfo(itemInfo.toString()).toString()
                        intent.putExtra(YAMFManager.EXTRA_COMPONENT_NAME, topComponent)
                    }
                    intent.putExtra(YAMFManager.EXTRA_TASK_ID, taskId)
                    intent.putExtra(YAMFManager.EXTRA_USER_ID, userId)
                    intent.putExtra(YAMFManager.EXTRA_SOURCE, YAMFManager.SOURCE_RECENT)

                    val action = RemoteAction(
                        Icon.createWithBitmap(
                            moduleRes.getDrawable(R.drawable.ic_picture_in_picture_alt_24, null)
                                .toBitmap()
                        ),
                        moduleRes.getString(R.string.open_with_yamf), // + if (BuildConfig.DEBUG) " ($taskId)" else "",
                        "",
                        PendingIntent.getBroadcast(
                            AndroidAppHelper.currentApplication(),
                            1345,
                            intent,
                            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
                        )
                    )
                    val c = classRemoteActionShortcut.constructors[0]
                    val shortcut = when (c.parameterCount) {
                        4 -> c.newInstance(action, activity, itemInfo, null)
                        3 -> c.newInstance(action, activity, itemInfo)
                        else -> {
                            log(
                                TAG,
                                "unknown RemoteActionShortcut constructor: ${c.toGenericString()}"
                            )
                            null
                        }
                    }

                    if (shortcut != null) {
                        shortcuts.add(shortcut)
                    }
                }
            })
    }

    private fun hookTaskbar(lpparam: XC_LoadPackage.LoadPackageParam) {
        // log(TAG, "hooking taskbar ${lpparam.packageName}")
        loadClass("com.android.launcher3.taskbar.TaskbarActivityContext").apply {
            findMethodOrNull { name == "startItemInfoActivity" }
                ?.hookReplace {
                    val infoIntent = it.args[0].invokeMethodAutoAs<Intent>("getIntent")!!
                    val intent = Intent(YAMFManager.ACTION_OPEN_IN_YAMF).apply {
                        setPackage("android")
                        putExtra(YAMFManager.EXTRA_COMPONENT_NAME, infoIntent.component)
                        putExtra(YAMFManager.EXTRA_SOURCE, YAMFManager.SOURCE_TASKBAR)
                    }
                    AndroidAppHelper.currentApplication().sendBroadcast(intent)
                }
            val classWorkspaceiteminfo =
                loadClass("com.android.launcher3.model.data.WorkspaceItemInfo")
            findMethod { name == "onTaskbarIconClicked" }
                .hookBefore {
                    val tag = it.args[0].invokeMethodAuto("getTag")!!
                    if (classWorkspaceiteminfo.isInstance(tag)) {
                        val infoIntent = tag.invokeMethodAutoAs<Intent>("getIntent")!!
                        val intent = Intent(YAMFManager.ACTION_OPEN_IN_YAMF).apply {
                            setPackage("android")
                            putExtra(YAMFManager.EXTRA_COMPONENT_NAME, infoIntent.component)
                            putExtra(YAMFManager.EXTRA_SOURCE, YAMFManager.SOURCE_TASKBAR)
                        }
                        AndroidAppHelper.currentApplication().sendBroadcast(intent)
                        it.result = Unit
                    }
                }
        }

    }

    private var proxyClass: Any? = null

    private fun hookPopup(lpparam: XC_LoadPackage.LoadPackageParam) {
        // log(TAG, "hooking popup ${lpparam.packageName}")
        loadClass("com.android.launcher3.popup.ArrowPopup").apply {
            findMethod { name == "onVisibilityAggregated" }.hookAfter {
                if (it.args[0] as Boolean) {
                    val popup = it.thisObject as View
                    val container = popup.parent as ViewGroup
                    for (i in 0 until container.childCount) {
                        val child = container.getChildAt(i)
                        if (child::class.java.name.contains("SystemShortcut")) {
                            // TODO
                        }
                    }
                }
            }
        }
    }

    private fun hookTransientTaskbar(lpparam: XC_LoadPackage.LoadPackageParam) {
        // log(TAG, "hooking transient taskbar ${lpparam.packageName}")
    }

    private fun updateDropZoneColor(isInZone: Boolean) {
        mMainHandler.post {
            mDropZoneView?.let { view ->
                val baseColor = if (isInZone) Color.parseColor("#8061d4ff") else Color.parseColor("#30FFFFFF")
                val shape = ShapeDrawable(RoundRectShape(floatArrayOf(40f, 40f, 40f, 40f, 40f, 40f, 40f, 40f), null, null))
                shape.paint.apply {
                    color = baseColor
                    style = Paint.Style.FILL_AND_STROKE
                    strokeWidth = 8f
                    pathEffect = DashPathEffect(floatArrayOf(20f, 15f), 0f)
                }
                view.background = shape
                if (view is TextView) {
                    view.alpha = if (isInZone) 1.0f else 0.8f
                }
            }
        }
    }

    private fun extractComponentInfo(input: String): ComponentName? {
        val regex = Regex("""ComponentInfo\{([^/]+)/([^}]+)\}""")
        val matchResult = regex.find(input)
        return if (matchResult != null) {
            val (packageName, className) = matchResult.destructured
            ComponentName(packageName, className)
        } else {
            null
        }
    }
}
