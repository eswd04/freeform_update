package com.sunshine.freeform.view.floating

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.PointFEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Binder
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import android.system.Os
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.widget.LinearLayout
import android.widget.Toast
import androidx.cardview.widget.CardView
import com.sunshine.freeform.MiFreeForm
import com.sunshine.freeform.R
import com.sunshine.freeform.callback.OrientationChangedListener
import com.sunshine.freeform.hook.utils.HookFailException
import com.sunshine.freeform.utils.RemoteServiceUtils
import kotlinx.coroutines.*
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import kotlin.math.*


/**
 * @author sunshine
 * @date 2022/1/6
 */
@DelicateCoroutinesApi
class FreeFormView(
    private val context: Context,
    val command: String,
    private val packageName: String,
    private val launcherActivity: String
) {

    companion object {
        private const val TAG = "FreeFormView"

        private const val FREEFORM_DEFAULT_PROPORTION = 1f
        private const val FREEFORM_DEFAULT_PROPORTION_LANDSCAPE = 0.9f
        private const val FREEFORM_SUSPEND_HEIGHT = 192 * 2

        private const val BAR_WIDTH = 128
        private const val BAR_HEIGHT = 8
        private const val BAR_VIEW_HEIGHT = 96

        //控制栏与小窗的距离
        private const val BAR_DISTANCE = 48

        val orientationChangedListener = object : OrientationChangedListener {
            override fun onChanged(orientation: Int) {
                FreeFormHelper.onOrientationChanged()
            }
        }

        private const val MIN_SCALE = 0.3f
        private const val MAX_SCALE = 0.9f
        private const val INITIAL_SCALE = 0.75f
        private const val FREEFORM_DEFAULT_WIDTH = 1080
        private const val FREEFORM_DEFAULT_HEIGHT = 1920
        private const val WIDTH_HEIGHT_RATIO = 9 / 16.0f


        private const val MAIN_SCALE_SIZE = 0.75


        //控制栏滑动方向
        private const val CHANGE_LEFT = 0
        private const val CHANGE_RIGHT = 1
        private const val BACK = 2
        private const val CLOSE = 3
        private const val MAX = 4
        private const val MOVE = 5
        private const val DOUBLE = 6
        private var curEvent = -1

        const val DPI = "freeform_dpi"
        const val DEFAULT_DPI = 400

        //小窗可以关闭和最大化滑动距离
        private const val CAN_CLOSE = 300
        private const val CAN_MAX = 200

        private const val SUSPEND_DPI = 400
        private const val SUSPEND_DISTANCE = 50
    }

    private val viewModel = MiFreeForm.baseViewModel
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private lateinit var freeFormRootView: CardView
    private lateinit var virtualDisplay: VirtualDisplay
    private var displayId: Int = -1
    private var textureView: TextureView? = null
    private lateinit var displayManager: DisplayManager
    private val touchListener = TouchListener()
    private var dpi = DEFAULT_DPI
    private var suspendDPI = SUSPEND_DPI

    private var freeFormWidth = 0
    private var freeFormHeight = 0

    //屏幕大致宽高，请注意，这不是屏幕真正宽高，而是和小窗宽高比相同且和屏幕相近的一个宽高
    private var screenWidth = 0
    private var screenHeight = 0

    //屏幕真正的高
    private var realScreenHeight = 0
    private var realScreenWidth = 0

    private val windowLayoutParams = WindowManager.LayoutParams()

    //是否是第一次初始化textureView，如果是的话，需要启动应用，否则就不启动了，因为会弹出root允许
    private var firstStart = true

    private var scale = 1.0f

    //小窗中显示方向：0竖屏 1横屏
    private var freeFormRotate = 0

    private var hasChanged = false

    private var screenChange = false

    //小窗是否为边角挂起状态
    private var isSuspend = false

    //禁用横屏操作
    private var disableLan = false
    private var disableLanKey = "disable_lan"


    private var launcherType = "0"
    private val launcherTypeKey = "launch_type"

    private var autoClose = true
    private val autoCloseKey = "listen_close"
    private val rememberSizeKey = "remember_size"
    private var rememberSize = false


    var taskListener: TaskListener? = null


    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayChanged(displayId: Int) {
            //原理：小窗内方向旋转有两次回调：1.小窗内变为横屏，此时执行else，让小窗也跟着旋转
            //2.小窗和小窗内方向保持一致后，会再一次回调，此时不需要做任何操作
            if (freeFormRotate != virtualDisplay.display.rotation) {
                freeFormRotate = virtualDisplay.display.rotation
                hasChanged = if (hasChanged) {
                    false
                } else {
                    if (freeFormRotate == Surface.ROTATION_90 || freeFormRotate == Surface.ROTATION_270) {
                        screenChange = !screenChange
                    }
                    onFreeFormRotationChanged()
                    true
                }
            }
        }

        override fun onDisplayAdded(displayId: Int) {

        }

        override fun onDisplayRemoved(displayId: Int) {

        }

    }


    //---------------bar--------------
    private lateinit var barLayout: LinearLayout
    private lateinit var barView: View
    private lateinit var topBarLayout: LinearLayout
    private lateinit var topBarView: View
    var taskManager: IActivityTaskManager? = null


    private val barWindowLayoutParams = WindowManager.LayoutParams()
    private val topBarWindowLayoutParams = WindowManager.LayoutParams()
    private val myGestureListener = MyGestureListener(context.resources.configuration.orientation)
    private val gestureDetector = GestureDetector(context, myGestureListener)


    init {

        Log.d(TAG, "${packageName} : ${launcherActivity} ")
        if (FreeFormHelper.hasFreeFormWindow(command)) {
            Toast.makeText(context, context.getString(R.string.already_show), Toast.LENGTH_SHORT)
                .show()
        } else {
//            if (MiFreeForm.baseViewModel.isRunning.value!!) {
            dpi = viewModel.getIntFromSP(DPI, DEFAULT_DPI)
            launcherType = viewModel.getStringFromSP(launcherTypeKey, "0").toString()
            disableLan = viewModel.getBooleanFromSP(disableLanKey, false)
            autoClose = viewModel.getBooleanFromSP(autoCloseKey, true)
            rememberSize = viewModel.getBooleanFromSP(rememberSizeKey, true)
            if (autoClose) {
                initTaskManager()
            }

            initView()
            initBar()
            initTopBar()
//            } else {
//                Toast.makeText(
//                    context,
//                    context.getString(R.string.shizuku_not_running),
//                    Toast.LENGTH_SHORT
//                ).show()
//            }
        }
    }


    var activityManager: IActivityManager? = null


    private fun initTaskManager() {
        kotlin.runCatching {
            taskManager = IActivityTaskManager.Stub.asInterface(
                ShizukuBinderWrapper(
                    SystemServiceHelper.getSystemService("activity_task")
                )
            )

            activityManager = IActivityManager.Stub.asInterface(
                ShizukuBinderWrapper(
                    SystemServiceHelper.getSystemService("activity")
                )
            )


        }.onFailure {
        }

    }


    private fun initView() {
        val rootLayout =
            LayoutInflater.from(context).inflate(R.layout.view_freeform_view, null, false)
        freeFormRootView = rootLayout.findViewById(R.id.freeform_root_view)

        initSize()
        initTextureView()
        freeFormWidth = (freeFormWidth * MAIN_SCALE_SIZE).toInt()
        freeFormHeight = (freeFormHeight * MAIN_SCALE_SIZE).toInt()
        windowLayoutParams.apply {
            dimAmount
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                type = WindowManager.LayoutParams.TYPE_TOAST
            }
            width = freeFormWidth
            height = freeFormHeight
            //设置这个在点击外部时响应外部操作，如果设置了FLAG_NOT_FOCUSABLE，悬浮窗不会躲避输入法，但是同时设置FLAG_ALT_FOCUSABLE_IM就都正常了，不设置FLAG_NOT_FOCUSABLE，会产生断触
            flags =
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS// or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED// or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS//
            format = PixelFormat.RGBA_8888
            x =
                if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && !disableLan) (screenWidth - realScreenWidth) / 2
                else 0
        }
        if (rememberSize) {
            val posX=if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE )
                viewModel.getIntFromSP("posLanX",if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && !disableLan) (screenWidth - realScreenWidth) / 2 else 0)
            else viewModel.getIntFromSP("posX",if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && !disableLan) (screenWidth - realScreenWidth) / 2 else 0)
            val posY=if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ) viewModel.getIntFromSP("posLanY",0) else viewModel.getIntFromSP("posY",0)
            windowLayoutParams.apply {
                x=posX
                y=posY
            }
        }

        try {
            windowManager.addView(freeFormRootView, windowLayoutParams)
        } catch (e: Exception) {
            Configuration.ORIENTATION_PORTRAIT
            Toast.makeText(
                context,
                context.getString(R.string.show_overlay_fail),
                Toast.LENGTH_SHORT
            ).show()
        }


    }

    private fun initSize() {
        val point = Point()
        val dm = DisplayMetrics()
        windowManager.defaultDisplay.getSize(point)
        windowManager.defaultDisplay.getMetrics(dm)
        var realWidth = 0
        var realHeight = 0
        if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            realWidth = max(point.x, point.y)
            realHeight = min(point.x, point.y)
            realScreenWidth = realWidth
            realScreenHeight = realHeight
            screenHeight = min(realHeight, realWidth / 9 * 16)
            screenWidth = screenHeight / 16 * 9

            freeFormHeight = (screenHeight * FREEFORM_DEFAULT_PROPORTION_LANDSCAPE).roundToInt()
            freeFormWidth = freeFormHeight / 16 * 9

        } else {
            realWidth = min(point.x, point.y)
            realHeight = max(point.x, point.y)
            realScreenWidth = realWidth
            realScreenHeight = realHeight

            screenHeight = min(realHeight, realWidth / 9 * 16)
            screenWidth = screenHeight / 16 * 9

            freeFormHeight = (screenHeight * 1.0).roundToInt()
            freeFormWidth = freeFormHeight / 16 * 9
        }

        //SB qq如果当前dpi与默认dpi不同不能显示
        if (command.contains("com.tencent.mobileqq")) {
            dpi = dm.densityDpi
            suspendDPI = dpi
        }
    }

    //横屏宽高记录
    var fwL = -1;
    var fhL = -1;
    var posLan: Point? = null
    var posLanTopBarLan: Point? = null
    var posBbottomBarLan: Point? = null


    //竖屏宽高记录
    var fwP = -1;
    var fhP = -1;
    var posPor: Point? = null
    var posLanTopBarPor: Point? = null
    var posBbottomBarPor: Point? = null

    fun restoreSize() {
        if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            if (fwL != -1&&fwL>0) {
                freeFormWidth = fwL
                freeFormHeight = fhL
            }
        } else {
            if (fwP != -1 &&fwP>0) {
                freeFormWidth = fwP
                freeFormHeight = fhP
            }
        }

    }


    fun restorePos() {
        if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            if (posLan != null) {
                updateView(posLanTopBarLan!!,posLan!!, posBbottomBarLan!!)
            }else{

            }
        } else {
            if (posPor != null) {
                updateView(posLanTopBarPor!!,posPor!!, posBbottomBarPor!!)
            }else{

            }
        }
    }

    fun updateView(pointT: Point,pointV: Point,pointB: Point) {
        barWindowLayoutParams.apply {
            x = pointB.x
            y = pointB.y
        }
        topBarWindowLayoutParams.apply {
            x = pointT.x
            y = pointT.y
        }

        windowLayoutParams.apply {
            x = pointV.x
            y = pointV.y
        }

        applyUpdate()


    }

    fun applyUpdate(){
        windowManager.updateViewLayout(
            barLayout,
            barWindowLayoutParams
        )
        windowManager.updateViewLayout(
            topBarLayout,
            topBarWindowLayoutParams
        )
        windowManager.updateViewLayout(
            freeFormRootView,
            windowLayoutParams
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initTextureView() {
        //获取DisplayManager服务
        displayManager = context.getSystemService(DisplayManager::class.java) as DisplayManager
        displayManager.registerDisplayListener(displayListener, null)
        //创建VirtualDisplay

        virtualDisplay = displayManager.createVirtualDisplay(
            "mi-freeform-display-$this",
            freeFormWidth,
            freeFormHeight,
            dpi,
            null,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION or DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
        )


        displayId = virtualDisplay.display.displayId

        if (textureView == null) textureView = TextureView(context)
        textureView?.id = R.id.texture_view
        textureView?.setOnTouchListener(touchListener)

        //监听TextureView是否初始化完成
        textureView?.surfaceTextureListener=object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureSizeChanged(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {
                var fh = 1;
                var fw = 1;

                if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    if (!screenChange) {
                        fh = (screenHeight * 1.5 * FREEFORM_DEFAULT_PROPORTION).roundToInt()
                        fw = fh / 16 * 9
                    } else {
                        fw = (screenHeight * 1.5 * FREEFORM_DEFAULT_PROPORTION_LANDSCAPE).roundToInt()
                        fh = fw / 16 * 9
                    }
                } else {
                    if (!screenChange) {
                        fh = (screenHeight * FREEFORM_DEFAULT_PROPORTION).roundToInt()
                        fw = fh / 16 * 9
                    } else {
                        fw = (screenHeight * FREEFORM_DEFAULT_PROPORTION_LANDSCAPE).roundToInt()
                        fh = fw / 16 * 9
                    }
                }

                virtualDisplay.resize(fw, fh, dpi)
                surface.setDefaultBufferSize(fw, fh)
                scale = width.toFloat() / fw.toFloat()
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {

            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                surface.release()
                return true
            }

            //SurfaceTexture初始化完成后开始显示界面
            override fun onSurfaceTextureAvailable(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {
                //将VirtualDisplay的surface对象设置为TextureView的Surface，即可在该TextureView上展示内容
                virtualDisplay.surface = Surface(surface)
                var fh: Int = 1
                var fw: Int = 1


                if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    fh = (screenHeight * 1.5 * FREEFORM_DEFAULT_PROPORTION_LANDSCAPE).roundToInt()
                    fw = fh / 16 * 9
                } else {
                    fh = (screenHeight * FREEFORM_DEFAULT_PROPORTION).roundToInt()
                    fw = fh / 16 * 9
                }

                virtualDisplay.resize(fw, fh, dpi)
                surface.setDefaultBufferSize(fw, fh)
                scale = width.toFloat() / fw.toFloat()
                if (autoClose) {
                    kotlin.runCatching {
                        taskListener = TaskListener()
                        taskManager?.registerTaskStackListener(taskListener)
                    }.onSuccess {
                    }
                }
                if (firstStart) {
                    FreeFormHelper.freeFormViewSet.add(this@FreeFormView)
                    FreeFormHelper.displayIdStackSet.push(displayId)
                    if (launcherType.equals("1")) {
                        try {
                            startCommandActivity(displayId)
                        } catch (e: Exception) {
                            if (MiFreeForm.baseViewModel.getControlService() != null && MiFreeForm.baseViewModel.getControlService()!!
                                    .execShell(command + displayId)
                            ) {
                                firstStart = false
                            } else {
                                Toast.makeText(
                                    context,
                                    "命令执行失败，可能的原因：远程服务没有启动、打开的程序不存在或已经停用",
                                    Toast.LENGTH_SHORT
                                ).show()
//                                destroy()
                                destroyWithAnim()
                            }
                        }
                    } else {
                        if (MiFreeForm.baseViewModel.getControlService() != null && MiFreeForm.baseViewModel.getControlService()!!
                                .execShell(command + displayId)
                        ) {
                            firstStart = false
                        } else {
                            Toast.makeText(
                                context,
                                "命令执行失败，可能的原因：远程服务没有启动、打开的程序不存在或已经停用",
                                Toast.LENGTH_SHORT
                            ).show()
//                            destroy()
                            destroyWithAnim()
                        }
                    }
                }
            }
        }

        freeFormRootView.addView(textureView)
    }

    inner class TaskListener : TaskStackListener() {
        override fun onTaskRemovalStarted(taskInfo: ActivityManager.RunningTaskInfo?) {
            val displayId = virtualDisplay.display.displayId

            if (taskInfo?.displayId == displayId && taskInfo?.realActivity.getPackageName()
                    .equals(packageName)
            ) {
                destroyWithAnim()
            }
        }

        override fun onTaskRequestedOrientationChanged(taskId: Int, requestedOrientation: Int) {
            super.onTaskRequestedOrientationChanged(taskId, requestedOrientation)
        }


        override fun onTaskDisplayChanged(taskId: Int, newDisplayId: Int) {
            super.onTaskDisplayChanged(taskId, newDisplayId)

            activityManager?.getTasks(10)?.forEach {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (newDisplayId == 0 && it.displayId == 0 && it.taskId == taskId && it.baseIntent.component?.packageName.equals(
                            packageName
                        )
                    ) {
                        destroyWithAnim()
                    }

                }
            }
        }

    }


    fun getDisplayId(): Int {
        return virtualDisplay.display.displayId
    }

    fun startCommandActivity(display: Int) {
        try {
            val options = prepareActivityOptions(display)
            val intent = Intent()
            intent.setClassName(packageName, launcherActivity)
            intent.addFlags( Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_RETAIN_IN_RECENTS)
            context.startActivity(intent, options.toBundle())
            Log.d(TAG, "startCommandActivity: ${context.applicationInfo.uid}")
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }


    private fun prepareActivityOptions(display: Int): ActivityOptions {
        val options = ActivityOptions.makeBasic()
        options.launchDisplayId = displayId
        return options
    }

    @SuppressLint("UseCompatLoadingForDrawables", "ClickableViewAccessibility")
    private fun initBar() {
        barView = View(context).apply {
            layoutParams =
                if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && !disableLan)
                    ViewGroup.LayoutParams(BAR_HEIGHT, BAR_WIDTH)
                else ViewGroup.LayoutParams(BAR_WIDTH, BAR_HEIGHT)
            alpha = 0.5f
            background = context.getDrawable(R.drawable.corners_bg)
        }
        barLayout = LinearLayout(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            gravity = Gravity.CENTER
            addView(barView)
            id = R.id.bar_layout
            setOnTouchListener(touchListener)
            setOnLongClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                true
            }
        }
        barWindowLayoutParams.apply {
            //启用系统层级的对话框
            //type = if (showModel == 1) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                type = WindowManager.LayoutParams.TYPE_TOAST
            }

            //设置这个在点击外部时响应外部操作，如果设置了FLAG_NOT_FOCUSABLE，悬浮窗不会躲避输入法，但是同时设置FLAG_ALT_FOCUSABLE_IM就都正常了，不设置FLAG_NOT_FOCUSABLE，会产生断触
            flags =
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS// or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED// or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS//
            format = PixelFormat.RGBA_8888
            if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && !disableLan) {
                width = BAR_VIEW_HEIGHT
                height = freeFormHeight
                x = windowLayoutParams.x + freeFormWidth / 2 + BAR_DISTANCE
                y = windowLayoutParams.y
            } else {
                width = freeFormWidth
                height = BAR_VIEW_HEIGHT
                x = windowLayoutParams.x
                y = windowLayoutParams.y + freeFormHeight / 2 + BAR_DISTANCE
            }
        }
        windowManager.addView(barLayout, barWindowLayoutParams)
    }


    private fun initTopBar() {
        topBarView = View(context).apply {
            layoutParams =
                if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && !disableLan)
                    ViewGroup.LayoutParams(BAR_HEIGHT, BAR_WIDTH)
                else ViewGroup.LayoutParams(BAR_WIDTH, BAR_HEIGHT)
            alpha = 0.5f
            background = context.getDrawable(R.drawable.corners_bg)
        }
        topBarLayout = LinearLayout(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            gravity = Gravity.CENTER
            addView(topBarView)
            id = R.id.top_bar_layout
            setOnTouchListener(touchListener)
            setOnLongClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                true
            }
        }
        topBarWindowLayoutParams.apply {
            //启用系统层级的对话框
            //type = if (showModel == 1) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                type = WindowManager.LayoutParams.TYPE_TOAST

            }

            //设置这个在点击外部时响应外部操作，如果设置了FLAG_NOT_FOCUSABLE，悬浮窗不会躲避输入法，但是同时设置FLAG_ALT_FOCUSABLE_IM就都正常了，不设置FLAG_NOT_FOCUSABLE，会产生断触
            flags =
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS// or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED// or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS//
            format = PixelFormat.RGBA_8888


            if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && !disableLan) {
                // 横屏
                width = BAR_VIEW_HEIGHT
                height = freeFormHeight
                x = windowLayoutParams.x - freeFormWidth / 2 - BAR_DISTANCE
                y = windowLayoutParams.y
            } else {
                // 竖屏
                width = freeFormWidth
                height = BAR_VIEW_HEIGHT
                x = windowLayoutParams.x
                y = windowLayoutParams.y - freeFormHeight / 2 - BAR_DISTANCE

            }
        }



        windowManager.addView(topBarLayout, topBarWindowLayoutParams)
    }


    @SuppressLint("ClickableViewAccessibility")
    private fun updateTextureView() {

    }

    @SuppressLint("UseCompatLoadingForDrawables")
    fun onOrientationChanged() {
        initSize()
        if (screenChange) {
            val temp = freeFormWidth
            freeFormWidth = (freeFormHeight * 0.5).toInt()
            freeFormHeight = (temp * 0.5).toInt()
        } else {
            freeFormWidth = (freeFormWidth * MAIN_SCALE_SIZE).toInt()
            freeFormHeight = (freeFormHeight * MAIN_SCALE_SIZE).toInt()
        }

        if (rememberSize) {
            restoreSize()
        }

        windowLayoutParams.apply {
            width = freeFormWidth
            height = freeFormHeight
            if (rememberSize) {
                x=if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE )
                    viewModel.getIntFromSP("posLanX",if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && !disableLan) (screenWidth - realScreenWidth) / 2 else 0)
                else viewModel.getIntFromSP("posX",if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && !disableLan) (screenWidth - realScreenWidth) / 2 else 0)
                y =if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ) viewModel.getIntFromSP("posLanY",0) else viewModel.getIntFromSP("posY",0)
            }else{
                x =
                    if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ) (screenWidth - realScreenWidth) / 2
                    else 0
                y = 0
            }

        }

        updateTextureView()
        windowManager.updateViewLayout(freeFormRootView, windowLayoutParams)
        //----------------Bar----------------
        myGestureListener.setOrientation(context.resources.configuration.orientation)

        barView.layoutParams.apply {
            if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && !disableLan) {
                width = BAR_HEIGHT
                height = BAR_WIDTH
            } else {
                width = BAR_WIDTH
                height = BAR_HEIGHT
            }
        }
        topBarView.layoutParams.apply {
            if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && !disableLan) {
                width = BAR_HEIGHT
                height = BAR_WIDTH
            } else {
                width = BAR_WIDTH
                height = BAR_HEIGHT
            }
        }
        barWindowLayoutParams.apply {
            if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && !disableLan) {
                width = BAR_VIEW_HEIGHT
                height = freeFormHeight
                x =
                    windowLayoutParams.x + freeFormWidth / 2 + BAR_DISTANCE
                y = windowLayoutParams.y
            } else {
                width = freeFormWidth
                height = BAR_VIEW_HEIGHT
                x = windowLayoutParams.x
                y =
                    windowLayoutParams.y + freeFormHeight / 2 + BAR_DISTANCE
            }
        }
        topBarWindowLayoutParams.apply {
            if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && !disableLan) {
                width = BAR_VIEW_HEIGHT
                height = freeFormHeight
                x =
                    windowLayoutParams.x - freeFormWidth / 2 - BAR_DISTANCE
                y = windowLayoutParams.y
            } else {
                width = freeFormWidth
                height = BAR_VIEW_HEIGHT
                x = windowLayoutParams.x
                y =
                    windowLayoutParams.y - freeFormHeight / 2 - BAR_DISTANCE
            }
        }

        windowManager.updateViewLayout(barLayout, barWindowLayoutParams)
        windowManager.updateViewLayout(topBarLayout, topBarWindowLayoutParams)

        if (isSuspend) toSuspend()
    }

    var change = false

    private fun onFreeFormRotationChanged() {


        //交换宽高
        val temp = freeFormWidth
        freeFormWidth = freeFormHeight
        freeFormHeight = temp

        if (freeFormRotate == 1) {
            freeFormWidth = (freeFormWidth * MAIN_SCALE_SIZE).toInt()
            freeFormHeight = (freeFormHeight * MAIN_SCALE_SIZE).toInt()
        } else {
            freeFormWidth = (freeFormWidth / MAIN_SCALE_SIZE).toInt()
            freeFormHeight = (freeFormHeight / MAIN_SCALE_SIZE).toInt()
        }

        Log.d(TAG, "onFreeFormRotationChanged: ${freeFormHeight > screenWidth}")
        if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            if (!screenChange&&freeFormHeight > screenWidth) {
                freeFormWidth = min(
                    max(screenWidth / 2, freeFormWidth),
                    screenWidth
                )
                freeFormHeight = freeFormWidth * 16 / 9
            }
        }

//        tempWidth=freeFormHeight
//        tempHeight=freeFormWidth


//        change=true

        virtualDisplay.resize(
            (freeFormWidth ).toInt(),
            (freeFormHeight).toInt(),
            if (isSuspend) suspendDPI else dpi
        )
        textureView?.layout(
            0,
            0,
            freeFormRootView.right - freeFormRootView.left,
            freeFormRootView.bottom - freeFormRootView.top
        )
        textureView?.surfaceTexture?.setDefaultBufferSize(
            freeFormWidth,
            freeFormHeight
        )

        windowLayoutParams.apply {
            width = freeFormWidth
            height = freeFormHeight
            if (rememberSize) {
                x=if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE )
                    viewModel.getIntFromSP("posLanX",if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && !disableLan) (screenWidth - realScreenWidth) / 2 else 0)
                else viewModel.getIntFromSP("posX",if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && !disableLan) (screenWidth - realScreenWidth) / 2 else 0)
                y =if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ) viewModel.getIntFromSP("posLanY",0) else viewModel.getIntFromSP("posY",0)

            }else{
                x =
                    if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) (screenWidth - realScreenWidth) / 2
                    else 0
                y = 0
            }

        }



        windowManager.updateViewLayout(freeFormRootView, windowLayoutParams)
        //----------------Bar----------------
        barView.layoutParams.apply {
            if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && !disableLan) {
                width = BAR_HEIGHT
                height = BAR_WIDTH
            } else {
                width = BAR_WIDTH
                height = BAR_HEIGHT
            }
        }
        topBarView.layoutParams.apply {
            if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && !disableLan) {
                width = BAR_HEIGHT
                height = BAR_WIDTH
            } else {
                width = BAR_WIDTH
                height = BAR_HEIGHT
            }
        }
        barWindowLayoutParams.apply {
            if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && !disableLan) {
                width = BAR_VIEW_HEIGHT
                height = freeFormHeight
                x =
                    windowLayoutParams.x + freeFormWidth / 2 + BAR_DISTANCE
                y = windowLayoutParams.y
            } else {
                width = freeFormWidth
                height = BAR_VIEW_HEIGHT
                x = windowLayoutParams.x
                y =
                    windowLayoutParams.y + freeFormHeight / 2 + BAR_DISTANCE
            }
        }
        topBarWindowLayoutParams.apply {
            if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && !disableLan) {
                width = BAR_VIEW_HEIGHT
                height = freeFormHeight
                x =
                    windowLayoutParams.x - freeFormWidth / 2 - BAR_DISTANCE
                y = windowLayoutParams.y
            } else {
                width = freeFormWidth
                height = BAR_VIEW_HEIGHT
                x = windowLayoutParams.x
                y =
                    windowLayoutParams.y - freeFormHeight / 2 - BAR_DISTANCE
            }
        }
        windowManager.updateViewLayout(barLayout, barWindowLayoutParams)
        windowManager.updateViewLayout(topBarLayout, topBarWindowLayoutParams)
        change = false
    }

    enum class Pos {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT
    }


    var tempWidth = -1
    var tempHeight = -1

    /**
     * @param pos 方位
     */
    private fun toSuspend(pos: Pos = Pos.TOP_RIGHT) {
        val startPoint = PointF(freeFormWidth.toFloat(), freeFormHeight.toFloat())
        val freeFormRotation = if (freeFormWidth > freeFormHeight) 1 else 0

        if (rememberSize) {
            if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                fwL = freeFormWidth
                fhL=freeFormHeight
            } else {
                fwP=freeFormWidth
                fhP=freeFormHeight
            }
        }

        tempWidth = freeFormWidth
        tempHeight = freeFormHeight

        if (freeFormRotation == 0) {
            freeFormHeight = FREEFORM_SUSPEND_HEIGHT
            freeFormWidth = freeFormHeight / 16 * 9
        } else {
            freeFormWidth = FREEFORM_SUSPEND_HEIGHT
            freeFormHeight = freeFormWidth / 16 * 9
        }



        val endPoint = PointF(freeFormWidth.toFloat(), freeFormHeight.toFloat())

        updateTextureView()


        val anmiH = ValueAnimator.ofObject(PointFEvaluator(), startPoint, endPoint)



        windowManager.updateViewLayout(
            freeFormRootView,
            windowLayoutParams.apply {
                width = freeFormWidth
                height = freeFormHeight
                when (pos) {
                    Pos.TOP_LEFT -> {
                        x =
                            (-realScreenWidth / 2) + freeFormWidth/*(p.x*2/3).toInt()*/ - SUSPEND_DISTANCE
                        y = /*((p.y*2/3).toInt()*/
                            (freeFormHeight - realScreenHeight) / 2 + SUSPEND_DISTANCE
                    }
                    Pos.TOP_RIGHT -> {
                        x = (realScreenWidth - freeFormWidth) / 2 - SUSPEND_DISTANCE
                        y = (freeFormHeight - realScreenHeight) / 2 + SUSPEND_DISTANCE
                    }


                    Pos.BOTTOM_LEFT -> {
                        x = (-realScreenWidth / 2) + freeFormWidth - SUSPEND_DISTANCE
                        y = (realScreenHeight / 2) - freeFormHeight + SUSPEND_DISTANCE
                    }
                    Pos.BOTTOM_RIGHT -> {
                        x = (realScreenWidth - freeFormWidth) / 2 - SUSPEND_DISTANCE
                        y = (realScreenHeight / 2) - freeFormHeight + SUSPEND_DISTANCE
                    }

                }
/*                x = (realScreenWidth - freeFormWidth) / 2 - SUSPEND_DISTANCE
                y =(freeFormHeight - realScreenHeight) / 2 + SUSPEND_DISTANCE*/

            })
        /* anmiH.duration=200
         anmiH.addUpdateListener {
             val p=it.animatedValue as PointF
             GlobalScope.launch(Dispatchers.Main) {

             }
         }

         anmiH.start()*/

        barLayout.visibility = View.GONE
        topBarView.visibility = View.GONE
    }


    private fun destroyWithAnim() {
        if (!isDestroy) {
            isDestroy = true
            GlobalScope.launch(Dispatchers.Main) {
                val anima = freeFormRootView.animate().alpha(0F).setDuration(150)
                anima.setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator?) {
                        super.onAnimationEnd(animation)
                        GlobalScope.launch(Dispatchers.Main) {
                            isDestroy = false
                            destroy()
                        }
                    }
                })
                anima.start()
            }
        }

    }

    var isDestroy = false
    private fun destroy() {
        if (!isDestroy) {
            isDestroy = true

            if (autoClose) {
                try {
                    if (taskListener != null) {
                        taskManager?.unregisterTaskStackListener(taskListener)
                    }
                } catch (e: Exception) {

                }
            }


/*            GlobalScope.launch(Dispatchers.Main + CoroutineExceptionHandler { context, throwable ->
                Log.d(TAG, "destroy: error")
                throwable.printStackTrace()
            }) {*/
            if (topBarLayout.isAttachedToWindow) {
            }
            windowManager.removeViewImmediate(topBarLayout)
            if (barLayout.isAttachedToWindow)
                windowManager.removeViewImmediate(barLayout)
            if (freeFormRootView.isAttachedToWindow)
                windowManager.removeViewImmediate(freeFormRootView)
            virtualDisplay.surface?.release()
            virtualDisplay.release()
            try {
                FreeFormHelper.freeFormViewSet.remove(this@FreeFormView)
                FreeFormHelper.displayIdStackSet.pop()
            } catch (e: Exception) {
                e.printStackTrace()
            }
//            }
        }

//        Handler(Looper.getMainLooper()).post{

//        }
//        GlobalScope.launch(Dispatchers.Main) {
//            val anima = freeFormRootView.animate().alpha(0F).setDuration(150)
//            anima.setListener(object : AnimatorListenerAdapter() {
//                override fun onAnimationEnd(animation: Animator?) {
//                    super.onAnimationEnd(animation)
//                    GlobalScope.launch(Dispatchers.Main) {

//                        }
        //----------------Bar----------------
//                    }
//                }
//            })
//            anima.start()
//        }


    }


    @Throws(HookFailException::class)
    fun resizeFreeForm(movedX: Float, movedY: Float, position: Int) {
        var tempWidth = if (position == 0) freeFormWidth - movedX else freeFormWidth + movedX
        var tempHeight = freeFormHeight + movedY

        val tempWidthScale = tempWidth / screenWidth
        val tempHeightScale = tempHeight / screenHeight
        if (tempWidthScale < MIN_SCALE || tempHeightScale < MIN_SCALE || tempWidthScale > MAX_SCALE || tempHeightScale > MAX_SCALE) return

        //keep equal width-height ratio
        if (tempWidth / tempHeight > WIDTH_HEIGHT_RATIO) tempHeight = tempWidth / WIDTH_HEIGHT_RATIO
        else tempWidth = tempHeight * WIDTH_HEIGHT_RATIO

        freeFormHeight = tempHeight.roundToInt()
        freeFormWidth = tempWidth.roundToInt()

        scale = freeFormHeight / screenHeight.toFloat()

        windowLayoutParams.apply {
            width = freeFormWidth
            height = freeFormHeight
        }

//        controlBarLayoutParams.apply {
//            width = freeFormWidth
//            x = freeformLayoutParams.x
//            y = freeformLayoutParams.y / 2 + controlBarHeight
//        }

        val matrix = Matrix()
        matrix.postScale(scale, scale, 0.0f, 0.0f)
//        textureView!!.setTransform(matrix)

        updateViewLayout()
    }

    @Throws(HookFailException::class)
    fun updateViewLayout() {
        val handler = android.os.Handler(Looper.getMainLooper())
        if (null == handler) {
            //throw HookFailException()
        } else {
            handler.post {
                windowManager.updateViewLayout(freeFormRootView, windowLayoutParams)
                //windowManager.updateViewLayout(controlBarView, controlBarLayoutParams)
            }
        }
    }

    fun commitLatestPos(x:Int,y:Int){
        if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            viewModel.putIntFromSP("posLanX",x)
            viewModel.putIntFromSP("posLanY",y)
        }else{
            viewModel.putIntFromSP("posX",x)
            viewModel.putIntFromSP("posY",y)
        }
    }

    var canMove = true


    inner class TouchListener : View.OnTouchListener {
        private var touchX = 0.0f
        private var touchY = 0.0f
        private var downX = 0.0f
        private var downY = 0.0f

        //滑动到一定范围可以关闭、最大化
        private var canClose = false
        private var canMax = false

        //为了防止在三者在移动过程中相互干扰，做一个判断
        private var nowStatus = -1


        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View?, event: MotionEvent?): Boolean {
            if (!canMove) {
                if (event?.action == MotionEvent.ACTION_UP) {
                    canMove = true
                }
                return true
            }

            //挂起状态不响应正常事件
            if (isSuspend) {

                isSuspend = false
                barLayout.visibility = View.VISIBLE
                topBarView.visibility = View.VISIBLE
                //复用
                onOrientationChanged()
                if (rememberSize) {
//                    restoreSize()
                    restorePos()
                }

                canMove = false
                android.os.Handler(Looper.getMainLooper()).postDelayed({
                    canMove = true
                }, 200)
            } else {
                when (v?.id) {
                    R.id.texture_view -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            RemoteServiceUtils.remoteInjectMotionEvent(event, displayId, scale)

                        } else {


//                            if (motionEvent == null) return

                            val count = event!!.pointerCount
                            val xArray = FloatArray(count)
                            val yArray = FloatArray(count)

                            for (i in 0 until count) {
                                val coords = MotionEvent.PointerCoords()
                                event.getPointerCoords(i, coords)
                                xArray!![i] = coords.x / scale
                                yArray!![i] = coords.y / scale
                            }
                            val pointerProperties: Array<MotionEvent.PointerProperties?> = arrayOfNulls(count)
                            val pointerCoords: Array<MotionEvent.PointerCoords?> = arrayOfNulls(count)
                            for (i in 0 until count) {
                                pointerProperties[i] = MotionEvent.PointerProperties()
                                pointerProperties[i]!!.id = i
                                pointerProperties[i]!!.toolType = MotionEvent.TOOL_TYPE_FINGER

                                pointerCoords[i] = MotionEvent.PointerCoords()
                                pointerCoords[i]!!.apply {
                                    orientation = 0f
                                    pressure = 1f
                                    size = 1f
                                    x = xArray[i]
                                    y = yArray[i]
                                }
                            }

                            val motionEvent = MotionEvent.obtain(
                                SystemClock.uptimeMillis(),
                                SystemClock.uptimeMillis(),
                                event.action,
                                count,
                                pointerProperties,
                                pointerCoords,
                                0,
                                0,
                                1.0f,
                                1.0f,
                                -1,
                                0,
                                InputDevice.SOURCE_TOUCHSCREEN,
                                0
                            )

                        }
                    }
                    R.id.top_bar_layout -> {
                        when (event?.action) {
                            MotionEvent.ACTION_DOWN -> {
                                touchX = event.rawX
                                touchY = event.rawY
                                downX = event.rawX
                                downY = event.rawY
                                if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                                    if (posLan == null) {
                                        posLan = Point(windowLayoutParams.x, windowLayoutParams.y)
                                        posLanTopBarLan = Point(
                                            topBarWindowLayoutParams.x,
                                            topBarWindowLayoutParams.y
                                        )
                                        posBbottomBarLan =
                                            Point(barWindowLayoutParams.x, barWindowLayoutParams.y)
                                    } else {
                                        posLan?.set(windowLayoutParams.x, windowLayoutParams.y)
                                        posLanTopBarLan?.set(
                                            topBarWindowLayoutParams.x,
                                            topBarWindowLayoutParams.y
                                        )
                                        posBbottomBarLan?.set(
                                            barWindowLayoutParams.x,
                                            barWindowLayoutParams.y
                                        )
                                    }
                                } else {
                                    if (posPor == null) {
                                        posPor = Point(windowLayoutParams.x, windowLayoutParams.y)
                                        posLanTopBarPor = Point(
                                            topBarWindowLayoutParams.x,
                                            topBarWindowLayoutParams.y
                                        )
                                        posBbottomBarPor =
                                            Point(barWindowLayoutParams.x, barWindowLayoutParams.y)
                                    } else {
                                        posPor?.set(windowLayoutParams.x, windowLayoutParams.y)
                                        posLanTopBarPor?.set(
                                            topBarWindowLayoutParams.x,
                                            topBarWindowLayoutParams.y
                                        )
                                        posBbottomBarPor?.set(
                                            barWindowLayoutParams.x,
                                            barWindowLayoutParams.y
                                        )
                                    }
                                }
                            }
                            MotionEvent.ACTION_MOVE -> {

                                val nowX = event.rawX
                                val nowY = event.rawY
                                val movedX = nowX - touchX
                                val movedY = nowY - touchY
                                touchX = nowX
                                touchY = nowY

                                if (nowX < 300 && nowY < 300) {
                                    canMove = false
                                    isSuspend = true
                                    toSuspend(Pos.TOP_LEFT)
                                } else if (nowX > realScreenWidth - 300 && nowY < 300) {
                                    canMove = false
                                    isSuspend = true
                                    toSuspend(Pos.TOP_RIGHT)
                                } else if (nowX < 300 && nowY > realScreenHeight - 300) {
                                    canMove = false
                                    isSuspend = true
                                    toSuspend(Pos.BOTTOM_LEFT)
                                } else if (nowX > realScreenWidth - 300 && nowY > realScreenHeight - 300) {
                                    canMove = false
                                    isSuspend = true
                                    toSuspend(Pos.BOTTOM_RIGHT)
                                }

                                barWindowLayoutParams.apply {
                                    x = barWindowLayoutParams.x + movedX.roundToInt()
                                    y = barWindowLayoutParams.y + movedY.roundToInt()
                                }
                                topBarWindowLayoutParams.apply {
                                    x = topBarWindowLayoutParams.x + movedX.roundToInt()
                                    y = topBarWindowLayoutParams.y + movedY.roundToInt()
                                }

                                windowLayoutParams.apply {
                                    x = windowLayoutParams.x + movedX.roundToInt()
                                    y = windowLayoutParams.y + movedY.roundToInt()
                                }
                                windowManager.updateViewLayout(
                                    barLayout,
                                    barWindowLayoutParams
                                )
                                windowManager.updateViewLayout(
                                    topBarLayout,
                                    topBarWindowLayoutParams
                                )
                                windowManager.updateViewLayout(
                                    freeFormRootView,
                                    windowLayoutParams
                                )
                            }
                            MotionEvent.ACTION_UP -> {
                                commitLatestPos(windowLayoutParams.x,windowLayoutParams.y)
                            }
                        }
                    }
                    //上滑：关闭；下滑：最大化；左右角滑：缩放
                    R.id.bar_layout -> {
                        gestureDetector.onTouchEvent(event)
                        when (event?.action) {
                            MotionEvent.ACTION_DOWN -> {
                                touchX = event.rawX
                                touchY = event.rawY
                                downX = event.rawX
                                downY = event.rawY
                            }
                            MotionEvent.ACTION_MOVE -> {
                                val nowX = event.rawX
                                val nowY = event.rawY
                                val movedX = nowX - touchX
                                val movedY = nowY - touchY
                                touchX = nowX
                                touchY = nowY

                                //如果上一次动作还没有完成，不响应其他动作
                                if (nowStatus != -1) curEvent = nowStatus
                                //TODO 横屏调节大小
                                when (curEvent) {
                                    CLOSE -> {
                                        nowStatus = CLOSE

                                        if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && !disableLan) {
                                            canClose = if (downX - nowX > CAN_CLOSE) {
                                                if (!canClose) v.performHapticFeedback(
                                                    HapticFeedbackConstants.LONG_PRESS
                                                )
                                                true
                                            } else {
                                                //回滑时取消关闭
                                                false
                                            }
                                            windowManager.updateViewLayout(
                                                freeFormRootView.apply {
                                                    alpha = max(
                                                        1.0f - (downX - nowX) * 1.0f / CAN_CLOSE,
                                                        0.5f
                                                    )
                                                },
                                                windowLayoutParams
                                            )
                                        } else {
                                            canClose = if (downY - nowY > CAN_CLOSE) {
                                                if (!canClose) v.performHapticFeedback(
                                                    HapticFeedbackConstants.LONG_PRESS
                                                )
                                                true
                                            } else {
                                                false
                                            }
                                            windowManager.updateViewLayout(
                                                freeFormRootView.apply {
                                                    alpha = max(
                                                        1.0f - (downY - nowY) * 1.0f / CAN_CLOSE,
                                                        0.5f
                                                    )
                                                },
                                                windowLayoutParams
                                            )
                                        }
                                    }
                                    MAX -> {
                                        nowStatus = MAX
                                        canMax =
                                            if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                                                if (nowX - downX > CAN_MAX) {
                                                    if (!canMax) v.performHapticFeedback(
                                                        HapticFeedbackConstants.LONG_PRESS
                                                    )
                                                    true
                                                } else {
                                                    false
                                                }
                                            } else {
                                                if (nowY - downY > CAN_MAX) {
                                                    if (!canMax) v.performHapticFeedback(
                                                        HapticFeedbackConstants.LONG_PRESS
                                                    )
                                                    true
                                                } else {
                                                    false
                                                }
                                            }
                                    }
                                    CHANGE_LEFT, CHANGE_RIGHT -> {
                                        nowStatus = curEvent
                                        //小窗内横竖屏状态
                                        val freeFormRotation =
                                            if (freeFormWidth > freeFormHeight) 1 else 0

//                                        if (context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT || disableLan) {
                                        if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                                            if (curEvent == CHANGE_LEFT) freeFormWidth += movedX.roundToInt()
                                            else freeFormWidth += movedX.roundToInt()
                                        }else{
                                            if (curEvent == CHANGE_LEFT) freeFormWidth += movedX.roundToInt()
                                            else freeFormWidth -= movedX.roundToInt()
                                        }

                                        //小窗内为竖屏
                                        if (!screenChange) {
                                            freeFormWidth = min(
                                                max(screenWidth / 2, freeFormWidth),
                                                screenWidth
                                            )
                                            freeFormHeight = freeFormWidth / 9 * 16
                                        } else {
                                            if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                                                freeFormWidth = min(screenWidth*2,max(freeFormWidth,screenWidth))
                                            }else{
                                                freeFormWidth = min(
                                                    max(screenWidth / 2, freeFormWidth),
                                                    screenWidth
                                                )
                                            }

                                            freeFormHeight = freeFormWidth / 16 * 9
                                        }

                                        windowManager.updateViewLayout(
                                            freeFormRootView,
                                            windowLayoutParams.apply {
                                                width = freeFormWidth
                                                height = freeFormHeight
                                            }
                                        )

                                        barWindowLayoutParams.apply {
                                            if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && !disableLan) {
                                                width = BAR_VIEW_HEIGHT
                                                height = freeFormHeight
                                                x =
                                                    windowLayoutParams.x + freeFormWidth / 2 + BAR_DISTANCE
                                                y = windowLayoutParams.y
                                            } else {
                                                width = freeFormWidth
                                                height = BAR_VIEW_HEIGHT
                                                x = windowLayoutParams.x
                                                y =
                                                    windowLayoutParams.y + freeFormHeight / 2 + BAR_DISTANCE
                                            }
                                        }
                                        topBarWindowLayoutParams.apply {
                                            if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && !disableLan) {
                                                width = BAR_VIEW_HEIGHT
                                                height = freeFormHeight
                                                x =
                                                    windowLayoutParams.x - freeFormWidth / 2 - BAR_DISTANCE
                                                y = windowLayoutParams.y
                                            } else {
                                                width = freeFormWidth
                                                height = BAR_VIEW_HEIGHT
                                                x = windowLayoutParams.x
                                                y =
                                                    windowLayoutParams.y - freeFormHeight / 2 - BAR_DISTANCE
                                            }
                                        }
                                        if (barView.visibility == View.VISIBLE) {
                                            barView.visibility = View.GONE
                                        }
                                        if (topBarView.visibility == View.VISIBLE) {
                                            topBarView.visibility = View.GONE
                                        }
                                    }
                                    MOVE -> {
//                                        val f = FrameLayout(context)
                                        if (isSuspend) {
                                            return true
                                        }
                                        windowLayoutParams.apply {
                                            x = windowLayoutParams.x + movedX.roundToInt()
                                            y = windowLayoutParams.y + movedY.roundToInt()
                                        }
                                        barWindowLayoutParams.apply {
                                            x = barWindowLayoutParams.x + movedX.roundToInt()
                                            y = barWindowLayoutParams.y + movedY.roundToInt()
                                        }
                                        topBarWindowLayoutParams.apply {
                                            x = topBarWindowLayoutParams.x + movedX.roundToInt()
                                            y = topBarWindowLayoutParams.y + movedY.roundToInt()
                                        }
                                        windowManager.updateViewLayout(
                                            freeFormRootView,
                                            windowLayoutParams
                                        )
                                        windowManager.updateViewLayout(
                                            barLayout,
                                            barWindowLayoutParams
                                        )
                                        windowManager.updateViewLayout(
                                            topBarLayout,
                                            topBarWindowLayoutParams
                                        )
                                    }
                                }
                            }
                            MotionEvent.ACTION_UP -> {
                                canMove = true
                                when (curEvent) {
                                    CLOSE -> {
                                        if (canClose) destroyWithAnim()
                                        else {
                                            windowManager.updateViewLayout(
                                                freeFormRootView.apply {
                                                    alpha = 1.0f
                                                },
                                                windowLayoutParams
                                            )
                                        }
                                    }
                                    MAX -> {
                                        if (canMax) {

                                            destroy()
                                            val intent =
                                                context.packageManager.getLaunchIntentForPackage(
                                                    packageName
                                                )
                                            intent!!.flags =
                                                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                                            try {
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }
                                    }
                                    CHANGE_LEFT, CHANGE_RIGHT -> {
//                                        if (context.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT || disableLan) {
                                        //这里有需要注意的点：updateTextureView()中不能加入这个，因为该处不需要加x y
                                        updateTextureView()
                                        windowManager.updateViewLayout(
                                            freeFormRootView,
                                            windowLayoutParams.apply {
                                                width = freeFormWidth
                                                height = freeFormHeight
                                            })
//                                        }
/*
                                        if ( disableLan && context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                                            barWindowLayoutParams.x=


                                        }*/

                                        windowManager.updateViewLayout(
                                            barLayout,
                                            barWindowLayoutParams
                                        )
                                        windowManager.updateViewLayout(
                                            topBarLayout,
                                            topBarWindowLayoutParams
                                        )
                                        barView.visibility = View.VISIBLE
                                        topBarView.visibility = View.VISIBLE

                                    }
                                    BACK -> {
                                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                                        } else {
                                            RemoteServiceUtils.remotePressBack(displayId)
                                        }
                                    }
                                    MOVE -> {
                                        commitLatestPos(windowLayoutParams.x,windowLayoutParams.y)
                                    }
                                    DOUBLE -> {
                                        if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && !disableLan) {
                                            //双击切换成挂起状态
                                            isSuspend = true
                                            canMove = true
                                            toSuspend()
                                        }

                                    }
                                }
                                nowStatus = -1
                                curEvent = -1
                            }

                        }
                        //响应长按
                        return false
                    }
                }
            }
            return true
        }
    }

    inner class MyGestureListener(o: Int) : GestureDetector.SimpleOnGestureListener() {

        private var orientation = o

        fun setOrientation(orientation: Int) {
            this.orientation = orientation
        }

        private fun createKeyEvent(action: Int, code: Int): KeyEvent? {
            val `when` = SystemClock.uptimeMillis()
            val ev = KeyEvent(
                `when`, `when`, action, code, 0 /* repeat */,
                0 /* metaState */, KeyCharacterMap.VIRTUAL_KEYBOARD, 0 /* scancode */,
                KeyEvent.FLAG_FROM_SYSTEM or KeyEvent.FLAG_VIRTUAL_HARD_KEY,
                InputDevice.SOURCE_KEYBOARD
            )
            return ev
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            } else {
                RemoteServiceUtils.remotePressBack(displayId)
            }

            return super.onSingleTapUp(e)
        }

        override fun onLongPress(e: MotionEvent) {
            curEvent = MOVE
        }

        override fun onScroll(
            e1: MotionEvent,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            val velocity =
                sqrt(velocityX.toDouble().pow(2.0) + velocityY.toDouble().pow(2.0))
                    .toFloat()
            val x1 = e1.x
            val y1 = e1.y
            val x2 = e2.x
            val y2 = e2.y
            val distanceX = x2 - x1
            val distanceY = y2 - y1
            val distance =
                sqrt((y2 - y1).toDouble().pow(2.0) + (x2 - x1).toDouble().pow(2.0))
                    .toInt()
            if (distance < 80) {
                return true
            }
            if (distanceX > 0) { //向右
                val y3 = distanceX * tan(22.5 * Math.PI / 180)
                val y4 = distanceX * tan((45 + 22.5) * Math.PI / 180)
                val absY = abs(distanceY).toDouble()
                val absX = abs(distanceX).toDouble()
                when {
                    absY < y3 -> { //向右滑
                        curEvent =
                            if (orientation == Configuration.ORIENTATION_LANDSCAPE && !disableLan) MAX
                            else BACK
                    }
                    absY < y4 -> {
                        curEvent = if (distanceY > 0) { //向右下滑
                            CHANGE_LEFT
                        } else { //向右上滑
                            CHANGE_RIGHT
                        }
                    }
                    else -> {
                        curEvent = if (distanceY > 0) { //向下滑
                            if (orientation == Configuration.ORIENTATION_LANDSCAPE && !disableLan) BACK
                            else MAX
                        } else { //向上滑
                            if (orientation == Configuration.ORIENTATION_LANDSCAPE && !disableLan) BACK
                            else CLOSE
                        }
                    }
                }
            } else { //向左
                val y3 = abs(distanceX * tan(22.5 * Math.PI / 180))
                val y4 = abs(distanceX * tan((45 + 22.5) * Math.PI / 180))
                val absY = abs(distanceY).toDouble()
                when {
                    absY < y3 -> { //向左滑
                        curEvent =
                            if (orientation == Configuration.ORIENTATION_LANDSCAPE && !disableLan) CLOSE
                            else BACK
                    }
                    absY < y4 -> {
                        curEvent = if (distanceY > 0) { //向左下滑
                            CHANGE_RIGHT
                        } else { //向左上滑
                            CHANGE_LEFT
                        }
                    }
                    else -> {
                        curEvent = if (distanceY > 0) { //向下滑
                            if (orientation == Configuration.ORIENTATION_LANDSCAPE && !disableLan) BACK
                            else MAX
                        } else { //向上滑
                            if (orientation == Configuration.ORIENTATION_LANDSCAPE && !disableLan) BACK
                            else CLOSE
                        }
                    }
                }
            }
            return true
        }

        override fun onFling(
            e1: MotionEvent,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            return super.onFling(e1, e2, velocityX, velocityY)
        }

        override fun onShowPress(e: MotionEvent) {
            super.onShowPress(e)
        }

        override fun onDown(e: MotionEvent): Boolean {
            return e.pointerCount == 1
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            curEvent = DOUBLE
            return super.onDoubleTap(e)
        }

        override fun onDoubleTapEvent(e: MotionEvent): Boolean {
            return super.onDoubleTapEvent(e)
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            return super.onSingleTapConfirmed(e)
        }

        override fun onContextClick(e: MotionEvent): Boolean {
            return super.onContextClick(e)
        }
    }

}