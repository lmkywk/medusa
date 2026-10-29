package io.github.aw1y2z.sesame.ui.miuix

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.core.view.WindowCompat
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import io.github.aw1y2z.sesame.R
import io.github.aw1y2z.sesame.data.AppConfig
import io.github.aw1y2z.sesame.data.ConfigPreload
import io.github.aw1y2z.sesame.data.Model
import io.github.aw1y2z.sesame.data.RunType
import io.github.aw1y2z.sesame.data.ViewAppInfo
import io.github.aw1y2z.sesame.model.normal.base.BaseModel
import io.github.aw1y2z.sesame.util.DiagnosticExport
import io.github.aw1y2z.sesame.util.FileUtil
import io.github.aw1y2z.sesame.util.LanguageUtil
import io.github.aw1y2z.sesame.util.Log
import io.github.aw1y2z.sesame.util.PermissionUtil
import io.github.aw1y2z.sesame.util.RiskGuard
import io.github.aw1y2z.sesame.notify.MedusaNoticeCenter
import io.github.aw1y2z.sesame.util.ProfileStore
import io.github.aw1y2z.sesame.util.ToastUtil
import io.github.aw1y2z.sesame.util.idMap.UserIdMap
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MiuixMainActivity : MiuixBaseActivity() {

    var runTypeText by mutableStateOf("")

    /** 可观察的激活状态：HomeTab 等页面订阅它，onServiceBind/广播更新时自动重组刷新 */
    var uiRunType by mutableStateOf<RunType>(RunType.DISABLE)
    var hasPermission by mutableStateOf(false)
    /** 从计划编辑页返回时自增，计划列表据此重新读取 */
    var planRevision by mutableIntStateOf(0)

    /** 权限已授予且共享目录真正可写：保存/日志/导出都依赖它 */
    var storageWritable by mutableStateOf(false)

    /** 注入进程回报的当前登录账号与模块开关：配置页和一键启用都以此为准 */
    var currentAccountId by mutableStateOf<String?>(null)
    var moduleEnabled by mutableStateOf<Boolean?>(null)

    /** 支付宝昵称/头像：首页头图与配置页账号卡共用 */
    var alipayProfile by mutableStateOf<ProfileStore.Profile?>(null)

    /** 统计版本号：load / 广播刷新后自增，首页数据看板据此重新读取 */
    var statisticsVersion by mutableStateOf(0)

    /** 首次进入且未授权时弹出一次权限向导；本进程只弹一次，避免每次返回都打断 */
    var showPermissionOnboarding by mutableStateOf(false)
    private var permissionOnboardingShown = false

    private val handler = Handler(Looper.getMainLooper())
    private var isClick = false
    // 标记是否已通过系统设置页请求过权限，用于 onResume 中检测用户是否已授权
    var hasRequestedPermission by mutableStateOf(false)

    /** 激活探测已重试次数,上限见 MAX_RUN_TYPE_PROBE_TIMES */
    private var runTypeProbeTimes = 0

    private lateinit var titleRunner: Runnable

    init {
        /**
         * 激活探测:仅当真实状态仍为 DISABLE 时才显示未激活,并在超时前周期性重试,
         * 避免覆盖晚到的 onServiceBind 激活信号,也避免 XposedService 绑定较慢时误报未激活
         */
        titleRunner = Runnable {
            if (ViewAppInfo.getRunType() == RunType.DISABLE) {
                runTypeProbeTimes++
                updateSubTitle(RunType.DISABLE)
                if (runTypeProbeTimes < MAX_RUN_TYPE_PROBE_TIMES) {
                    sendQueryBroadcast()
                    handler.postDelayed(titleRunner, 3000)
                }
            } else {
                runTypeProbeTimes = 0
            }
        }
    }

    companion object {
        /** 最多探测次数:每次间隔 3 秒,共约 15 秒,覆盖 XposedService 冷启动绑定晚于 Activity 的情况 */
        private const val MAX_RUN_TYPE_PROBE_TIMES = 5

        /**
         * 设备显示名:优先读市场名(如 Xiaomi 13)。
         * 小米/红米及多数云手机、模拟器的 ro.product.model 只是内部型号编号(如 2211133C),
         * 多设备会显示相同,须用 ro.product.marketname 才有人类可读名称。
         */
        fun getDeviceDisplayName(): String {
            try {
                val clazz = Class.forName("android.os.SystemProperties")
                val get = clazz.getMethod("get", String::class.java)
                val market = get.invoke(null, "ro.product.marketname") as? String
                if (!market.isNullOrBlank()) {
                    return market
                }
            } catch (e: Throwable) {
                // 取不到市场名就退回内部型号：显示会退化（小米/红米尤其明显），异常要留痕
                Log.debug("getDeviceDisplayName: " + e)
            }
            return Build.MODEL ?: Build.DEVICE ?: ""
        }
    }

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            Log.i("view broadcast action:" + action + " intent:" + intent)
            if (action != null) {
                when (action) {
                    "io.github.aw1y2z.sesame.status" -> {
                        // 模块已被 LSPosed 启用并注入支付宝，标记为已激活
                        ViewAppInfo.setRunTypeByCode(RunType.MODEL.getCode())
                        runTypeProbeTimes = 0
                        handler.removeCallbacks(titleRunner)
                        updateSubTitle(RunType.MODEL)
                        // 注入进程会带上当前账号和启用开关；没有附加项时保持原值，避免旧版本互相覆盖
                        if (!PreviewMode.enabled) {
                            intent.getStringExtra("userId")?.takeIf { it.isNotEmpty() }?.let {
                                currentAccountId = it
                            }
                        }
                        if (intent.hasExtra("enabled")) {
                            moduleEnabled = intent.getBooleanExtra("enabled", false)
                        }
                        if (isClick) {
                            ToastUtil.show(context, "模块运行状态已刷新")
                            isClick = false
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // runType 被模块置为 MODEL（onModuleLoaded）时立即刷新界面，无需手动加载配置
        ViewAppInfo.setRunTypeListener {
            runOnUiThread {
                handler.removeCallbacks(titleRunner)
                updateSubTitle(ViewAppInfo.getRunType())
            }
        }
        ViewAppInfo.checkRunType()
        updateSubTitle(ViewAppInfo.getRunType())
        val intentFilter = IntentFilter()
        intentFilter.addAction("io.github.aw1y2z.sesame.status")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(broadcastReceiver, intentFilter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(broadcastReceiver, intentFilter)
        }
        setAppContent(bubbleBottomPadding = 86.dp) {
            MainScreen(this)
        }
        if (!isNotificationPermissionGranted()) {
            requestNotificationPermission()
        }
    }

    override fun onResume() {
        super.onResume()
        // 子手势被系统打断时可能没走到 onDragCancel，回到前台先解锁切页。
        PageSwipeGate.locked = false
        planRevision++
        // 激活状态探测独立于存储权限：防止 titleRunner 重复累积，先清空再启动
        if (RunType.DISABLE == ViewAppInfo.getRunType()) {
            handler.removeCallbacks(titleRunner)
            runTypeProbeTimes = 0
            sendQueryBroadcast()
            handler.postDelayed(titleRunner, 3000)
        } else {
            // 已激活也要刷新一次：注入进程会回报当前账号与模块启用开关
            sendQueryBroadcast()
        }
        checkPermissionAndRefresh()
    }

    /**
     * 检查文件权限：已授权则每次都重读统计。
     * <p>权限早已授予时也要刷新——否则从配置页或后台返回首页，统计仍是旧值（只有退出重进才更新）。
     */
    private fun checkPermissionAndRefresh() {
        hasRequestedPermission = false
        hasPermission = PermissionUtil.checkFilePermissions(this)
        storageWritable = hasPermission && FileUtil.isMainDirectoryWritable()
        // 首次启动必须主动提示：没有这个权限，外部 App 既存不了设置，也无法与支付宝内的模块同步
        if (!hasPermission && !permissionOnboardingShown) {
            permissionOnboardingShown = true
            showPermissionOnboarding = true
        }
        if (hasPermission) {
            DemoSeed.ensure()
            if (currentAccountId == null) {
                currentAccountId = FileUtil.readCurrentAccountId()
            }
            alipayProfile = ProfileStore.load()
            refreshStatistics()
        }
    }

    /** 当前登录账号：优先用注入进程回报值，其次用共享目录里的标记文件 */
    fun resolveCurrentAccount(): String? {
        if (currentAccountId == null && PermissionUtil.checkFilePermissions(this)) {
            currentAccountId = FileUtil.readCurrentAccountId()
        }
        return currentAccountId
    }

    fun requestStoragePermission() {
        hasRequestedPermission = true
        PermissionUtil.checkOrRequestFilePermissions(this)
    }

    fun isNotificationPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            true
        } else {
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isNotificationPermissionGranted()) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
        } else if (!isNotificationPermissionGranted()) {
            ToastUtil.show(this, "请在系统设置中允许美杜莎发送通知")
        }
    }

    /** 通知监听服务由系统绑定；这里只读系统里已授权的监听包名。 */
    fun isNotificationListenerEnabled(): Boolean {
        return try {
            val enabled = Settings.Secure.getString(
                contentResolver, "enabled_notification_listeners"
            ) ?: return false
            enabled.split(":").any { flat ->
                ComponentName.unflattenFromString(flat)?.packageName == packageName
            }
        } catch (t: Throwable) {
            false
        }
    }

    fun openNotificationListenerSettings() {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (t: Throwable) {
            try {
                startActivity(Intent("android.settings.NOTIFICATION_LISTENER_SETTINGS"))
            } catch (e: Throwable) {
                ToastUtil.show(this, "无法打开通知使用权设置，请到系统设置手动开启")
            }
        }
    }

    /**
     * 一键启用当前账号的模块：直接改 ConfigV2 里的 BaseModel.enable 并落盘，
     * 再广播让支付宝重载。用户不必再翻「配置 → 账号 → 基础」找开关。
     */
    fun enableCurrentModule() {
        val userId = resolveCurrentAccount()
        if (userId == null) {
            ToastUtil.show(this, "尚未获取到当前账号，请先打开并登录一次支付宝")
            sendQueryBroadcast()
            return
        }
        if (!PermissionUtil.isSharedStorageWritable(this)) {
            ToastUtil.show(this, "需要「所有文件访问权限」才能写入配置")
            requestStoragePermission()
            return
        }
        try {
            ConfigPreload.prepare(userId)
            Model.getModel(BaseModel::class.java).enableField.setObjectValue(true)
            val result = ConfigSync.save(this, userId)
            if (result.ok) {
                moduleEnabled = true
            } else if (result.outcome == SaveOutcome.NO_PERMISSION) {
                requestStoragePermission()
            } else {
                ToastUtil.show(this, ConfigSync.failureMessage(result))
            }
        } catch (t: Throwable) {
            Log.printStackTrace(t)
            ToastUtil.show(this, "启用失败：${t.message ?: "配置写入异常"}")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            hasPermission = granted
            if (granted) refreshStatistics()
        }
    }

    /** 通知首页重新读取统计：只递增可观察版本号，数据由首页按当前账号读取。 */
    fun refreshStatistics() {
        if (!hasPermission) return
        statisticsVersion++
    }

    fun updateSubTitle(runType: RunType) {
        uiRunType = runType
        runTypeText = when (runType) {
            RunType.DISABLE -> ViewAppInfo.getAppTitle() + "【" + getString(R.string.disable) + "】"
            RunType.MODEL -> ViewAppInfo.getAppTitle() + "【" + getString(R.string.activated) + "】"
            RunType.PACKAGE -> ViewAppInfo.getAppTitle() + "【" + getString(R.string.loading) + "】"
        }
    }

    fun sendStatus() {
        try {
            isClick = true
            sendBroadcast(Intent("com.eg.android.AlipayGphone.sesame.status"))
        } catch (th: Throwable) {
            Log.i("view sendBroadcast status err:")
            Log.printStackTrace(th)
        }
    }

    /** 向支付宝进程查询本模块注入状态（不弹 Toast），由 titleRunner 周期性调用 */
    fun sendQueryBroadcast() {
        try {
            sendBroadcast(Intent("com.eg.android.AlipayGphone.sesame.status"))
        } catch (th: Throwable) {
            Log.i("view sendBroadcast status err:")
            Log.printStackTrace(th)
        }
    }

    /** 通知支付宝进程重载共享配置（日志开关等），使开关在注入进程中即时生效 */
    /**
     * 让注入进程整体重启（重新初始化并重挂 hook）。
     * 适用于改完必须重新初始化的开关，比如 RPC bridge 需要重挂；
     * 只是重载 AppConfig 的 broadcastReloadConfig() 不够用。
     */
    fun broadcastRestart() {
        try {
            val intent = Intent("com.eg.android.AlipayGphone.sesame.restart")
            // 带上当前账号：注入进程会据此判断是否需要重载；不带也能重载，但带上是更明确的定向同步
            resolveCurrentAccount()?.takeIf { it.isNotEmpty() }?.let { intent.putExtra("userId", it) }
            sendMedusaBroadcast(intent)
        } catch (t: Throwable) {
            Log.printStackTrace(t)
        }
    }

    fun broadcastReloadConfig() {
        try {
            sendMedusaBroadcast(Intent("com.eg.android.AlipayGphone.sesame.reloadConfig"))
        } catch (th: Throwable) {
            Log.i("view sendBroadcast reloadConfig err:")
            Log.printStackTrace(th)
        }
    }

    fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            ToastUtil.show(this, "无法打开链接")
        }
    }

    fun toggleLanguage() {
        val appConfig = AppConfig.INSTANCE
        appConfig.languageSimplifiedChinese = !appConfig.languageSimplifiedChinese
        if (ConfigSync.saveAppConfig(this)) {
            LanguageUtil.setLocal(this)
            recreate()
        }
    }

    fun isIconHidden(): Boolean {
        val alias = ComponentName(this, "io.github.aw1y2z.sesame.ui.MainActivityAlias")
        return packageManager.getComponentEnabledSetting(alias) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }

    fun toggleHideIcon() {
        val alias = ComponentName(this, "io.github.aw1y2z.sesame.ui.MainActivityAlias")
        val state = packageManager.getComponentEnabledSetting(alias)
        val newState = if (state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        }
        packageManager.setComponentEnabledSetting(alias, newState, PackageManager.DONT_KILL_APP)
    }

    override fun onPause() {
        super.onPause()
        // 离开前台即停止状态轮询，避免后台无谓广播与泄漏
        handler.removeCallbacks(titleRunner)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(titleRunner)
        try {
            unregisterReceiver(broadcastReceiver)
        } catch (e: Exception) {
            // 未注册成功时注销会抛，属正常路径，留痕即可
            Log.debug("unregisterReceiver: " + e)
        }
    }
}

@Composable
fun MainScreen(activity: MiuixMainActivity) {
    val view = LocalView.current
    // 页序按底栏：首页 → 计划 → 配置 → 日志；selectedTab 仍沿用旧的内部编号。
    val navPages = remember { listOf(0, 2, 1, 3) }
    val pageByTab = remember { mapOf(0 to 0, 2 to 1, 1 to 2, 3 to 3) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { navPages.size })
    val hazeState = remember { HazeState() }
    val scope = rememberCoroutineScope()
    val configScroll = rememberScrollState()
    val planScroll = rememberScrollState()
    val logsScroll = rememberScrollState()

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val tab = navPages.getOrElse(page) { 0 }
            if (tab != selectedTab) {
                selectedTab = tab
                if (tab == 0) activity.refreshStatistics()
            }
        }
    }

    // 首页 Hero 是深色渐变，状态栏图标必须用白色；其它页恢复深色图标
    LaunchedEffect(selectedTab) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = selectedTab != 0
    }

    Scaffold(
        containerColor = Color(0xFFF4F8FD),
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { _ ->
        Box(Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().hazeSource(hazeState),
                beyondViewportPageCount = 0,
                userScrollEnabled = !PageSwipeGate.locked
            ) { page ->
                when (navPages.getOrElse(page) { 0 }) {
                    0 -> Box(
                        Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                    ) {
                        HomeV2Tab(activity)
                    }
                    1 -> Column(
                        Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                            .statusBarsPadding()
                            .padding(bottom = 88.dp)
                    ) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(configScroll)
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            ConfigTab(activity)
                        }
                    }
                    2 -> Column(
                        Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                            .statusBarsPadding()
                            .padding(bottom = 88.dp)
                    ) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(planScroll)
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            PlanTabV2(activity)
                        }
                    }
                    3 -> Column(
                        Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                            .statusBarsPadding()
                            .padding(bottom = 88.dp)
                    ) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(logsScroll)
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            DataTab(activity)
                        }
                    }
                }
            }
            MedusaNavBar(
                selectedTab = selectedTab,
                onSelect = { index ->
                    selectedTab = index
                    if (index == 0) activity.refreshStatistics()
                    scope.launch {
                        pagerState.animateScrollToPage(pageByTab[index] ?: 0)
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter),
                hazeState = hazeState
            )
        }
    }
    // 首次启动主动弹一次：只靠首屏卡片，用户很容易直接划过，随后保存/同步全部失败
    StoragePermissionDialog(
        show = activity.showPermissionOnboarding,
        message = "美杜莎需要「所有文件访问权限」才能保存设置、读取日志，并把设置同步到支付宝内的模块。请点「去授权」，在系统页面开启后返回。",
        onDismiss = { activity.showPermissionOnboarding = false }
    )
}

/** 前台轻提示：四类语义、无 emoji、同一位置排队；与系统通知共用事件源。 */
@Composable
internal fun MedusaBubbleOverlay(onOpen: () -> Unit = {}, modifier: Modifier = Modifier) {
    val notice = MedusaNoticeCenter.current
    val isToast = notice != null &&
            (notice.type == MedusaNoticeCenter.TYPE_TOAST || notice.title.isBlank())
    LaunchedEffect(notice?.id) {
        if (notice != null) {
            delay(if (isToast) 2200L else 2600L)
            MedusaNoticeCenter.dismiss()
        }
    }
    if (notice == null) return
    val toastText = notice.detail
    val toastDanger = isToast && (toastText.contains("失败") || toastText.contains("错误")
            || toastText.contains("异常") || toastText.contains("不可用"))
    val toastAttention = isToast && !toastDanger && (toastText.contains("暂停")
            || toastText.contains("注意") || toastText.contains("需") || toastText.contains("等待"))
    val tone = when {
        isToast && toastDanger -> MedusaColors.Danger
        isToast && toastAttention -> MedusaColors.Gold
        notice.type == "RISK" -> MedusaColors.Danger
        notice.type == "ATTENTION" -> MedusaColors.Gold
        notice.type == "RUN" -> MedusaColors.Blue
        else -> MedusaColors.GreenDeep
    }
    val icon = when {
        isToast && (toastDanger || toastAttention) -> Icons.Filled.Warning
        isToast -> Icons.Filled.CheckCircle
        notice.type == "RISK" -> Icons.Filled.Security
        notice.type == "ATTENTION" -> Icons.Filled.Warning
        notice.type == "RUN" -> Icons.Filled.PlayArrow
        else -> Icons.Filled.CheckCircle
    }
    val shape = RoundedCornerShape(if (isToast) 15.dp else 17.dp)
    Row(
        modifier
            .fillMaxWidth()
            .shadow(if (isToast) 14.dp else 18.dp, shape)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.97f))
            .border(1.dp, Color.White.copy(alpha = 0.92f), shape)
            .clickable {
                if (!isToast) onOpen()
                MedusaNoticeCenter.dismiss()
            }
            .padding(
                start = if (isToast) 10.dp else 12.dp,
                top = if (isToast) 8.dp else 10.dp,
                bottom = if (isToast) 8.dp else 10.dp,
                end = if (isToast) 5.dp else 7.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(if (isToast) 28.dp else 34.dp)
                .clip(RoundedCornerShape(if (isToast) 10.dp else 12.dp))
                .background(tone.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = tone,
                modifier = Modifier.size(if (isToast) 15.dp else 18.dp)
            )
        }
        Spacer(Modifier.width(if (isToast) 8.dp else 10.dp))
        if (isToast) {
            Text(
                text = notice.detail,
                fontSize = MedusaType.Body,
                fontWeight = FontWeight.Medium,
                color = MedusaColors.Ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        } else {
            Column(Modifier.weight(1f)) {
                Text(
                    text = notice.title,
                    fontSize = MedusaType.Body,
                    fontWeight = FontWeight.SemiBold,
                    color = MedusaColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = notice.detail,
                    fontSize = MedusaType.Secondary,
                    color = MedusaColors.Muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Spacer(Modifier.width(if (isToast) 4.dp else 6.dp))
        Box(
            Modifier
                .size(if (isToast) 24.dp else 28.dp)
                .clip(RoundedCornerShape(999.dp))
                .clickable { MedusaNoticeCenter.dismiss() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "关闭",
                tint = MedusaColors.Muted,
                modifier = Modifier.size(if (isToast) 12.dp else 14.dp)
            )
        }
    }
}

/** 支付宝头像：网络图异步加载，失败时退回昵称首字，再退回品牌徽标 */
@Composable
private fun HomeAvatar(avatarUrl: String?, name: String?, onDark: Boolean = true) {
    var bitmap by remember(avatarUrl) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(avatarUrl) {
        bitmap = if (avatarUrl.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    URL(avatarUrl).openStream().use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                }.getOrNull()
            }
        }
    }
    Box(
        Modifier
            .size(58.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (onDark) Color.White.copy(alpha = 0.10f)
                else MedusaColors.GreenDeep.copy(alpha = 0.12f)
            ),
        contentAlignment = Alignment.Center
    ) {
        val image = bitmap
        when {
            image != null -> Image(
                bitmap = image,
                contentDescription = "支付宝头像",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            !name.isNullOrBlank() -> Text(
                text = name.take(1),
                // 例外：头像首字是图形化字号，24sp 与 58dp 头像绑定，不参与五档
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = if (onDark) Color.White else MedusaColors.GreenDeep
            )
            else -> Image(
                painter = painterResource(R.drawable.medusa_logo),
                contentDescription = "美杜莎",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun DataTab(activity: MiuixMainActivity) {
    val context = LocalContext.current
    val userId = activity.currentAccountId
    var refresh by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var metrics by remember { mutableStateOf<List<DailyMetricsStore.DayMetrics>>(emptyList()) }
    var medians by remember { mutableStateOf<Map<String, Pair<Int, Int>>>(emptyMap()) }
    var aiList by remember { mutableStateOf<List<org.json.JSONObject>>(emptyList()) }

    LaunchedEffect(userId, refresh) {
        loading = true
        withContext(Dispatchers.IO) {
            metrics = DailyMetricsStore.load(userId, 14)
            medians = DailyMetricsStore.durationMedians(userId, 14)
            aiList = DailyMetricsStore.aiSummaries(7)
        }
        loading = false
    }

    fun openLog(type: LogType?) {
        context.startActivity(
            Intent(context, MiuixLogViewerActivity::class.java).apply {
                if (type != null) putExtra(LogType.EXTRA_LOG_TYPE, type.name)
            }
        )
    }

    Text(
        text = "数据",
        fontSize = MedusaType.PageTitle,
        fontWeight = FontWeight.Bold,
        color = MiuixTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
    )

    val todayKey = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
    val today = metrics.firstOrNull { it.day == todayKey } ?: metrics.lastOrNull()

    SmallTitle(text = "今天")
    CardColumn {
        if (today == null) {
            Text(
                if (loading) "加载中…" else "今天还没有运行记录；计划跑过一轮后这里会有数字。",
                fontSize = MedusaType.Body,
                color = MedusaColors.Muted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
            )
        } else {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DataMetric("任务完成", "${today.done}/${today.total}", MedusaColors.GreenDeep, Modifier.weight(1f))
                DataMetric("运行", "${today.completedRuns}", MedusaColors.Ink, Modifier.weight(1f))
                DataMetric("风控", "${today.riskRuns}", if (today.riskRuns > 0) MedusaColors.Warning else MedusaColors.Ink, Modifier.weight(1f))
                DataMetric("用时", "${today.minutes} 分", MedusaColors.Ink, Modifier.weight(1f))
            }
        }
        ArrowPreference(
            title = "运行记录",
            summary = "每一次执行的开始时间、结果与用时",
            onClick = {
                context.startActivity(
                    Intent(context, MiuixRunHistoryActivity::class.java)
                        .putExtra(MiuixRunHistoryActivity.EXTRA_USER_ID, userId)
                )
            }
        )
    }
    Spacer(Modifier.height(16.dp))

    SmallTitle(text = "近 14 天完成度")
    CardColumn {
        Heatmap(metrics = metrics, loading = loading)
    }
    Spacer(Modifier.height(16.dp))

    SmallTitle(text = "每域耗时")
    CardColumn {
        DurationBars(medians = medians, loading = loading)
    }
    Spacer(Modifier.height(16.dp))

    SmallTitle(text = "AI 摘要")
    CardColumn {
        if (aiList.isEmpty()) {
            Text(
                if (loading) "加载中…" else "AI 未启用，或还没有生成过每日摘要。",
                fontSize = MedusaType.Body,
                color = MedusaColors.Muted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
            )
        } else {
            aiList.take(3).forEach { summary ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(
                        summary.optString("day", "") + if (summary.optBoolean("ok", false)) "" else " · 未生成",
                        fontSize = MedusaType.Caption,
                        color = MedusaColors.Muted
                    )
                    Text(
                        summary.optString("headline", ""),
                        fontSize = MedusaType.Body,
                        color = MedusaColors.Ink,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    val suggestion = DailyMetricsStore.suggestionText(summary)
                    if (suggestion.isNotEmpty()) {
                        Text(
                            suggestion,
                            fontSize = MedusaType.Caption,
                            color = MedusaColors.Muted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
        ArrowPreference(
            title = "打开 AI 分析",
            summary = "接口、模型与每日摘要设置",
            onClick = { context.startActivity(Intent(context, MiuixAiActivity::class.java)) }
        )
    }
    Spacer(Modifier.height(16.dp))

    SmallTitle(text = "记录开关")
    CardColumn {
        var forest by remember { mutableStateOf(AppConfig.INSTANCE.enableForestLog ?: true) }
        LogSwitchRow("森林记录", forest, onClick = { openLog(LogType.FOREST) }) {
            forest = it
            AppConfig.INSTANCE.enableForestLog = it
            ConfigSync.saveAppConfig(context)
            activity.broadcastReloadConfig()
            if (!it) FileUtil.clearLog("forest")
        }
        var farm by remember { mutableStateOf(AppConfig.INSTANCE.enableFarmLog ?: true) }
        LogSwitchRow("庄园记录", farm, onClick = { openLog(LogType.FARM) }) {
            farm = it
            AppConfig.INSTANCE.enableFarmLog = it
            ConfigSync.saveAppConfig(context)
            activity.broadcastReloadConfig()
            if (!it) FileUtil.clearLog("farm")
        }
        var goldenBeans by remember { mutableStateOf(AppConfig.INSTANCE.enableGoldenBeansLog ?: true) }
        LogSwitchRow("金豆记录", goldenBeans, onClick = { openLog(LogType.GOLDENBEANS) }) {
            goldenBeans = it
            AppConfig.INSTANCE.enableGoldenBeansLog = it
            ConfigSync.saveAppConfig(context)
            activity.broadcastReloadConfig()
            if (!it) FileUtil.clearLog("goldenbeans")
        }
        var other by remember { mutableStateOf(AppConfig.INSTANCE.enableOtherLog ?: true) }
        LogSwitchRow("其他记录", other, onClick = { openLog(LogType.OTHER) }) {
            other = it
            AppConfig.INSTANCE.enableOtherLog = it
            ConfigSync.saveAppConfig(context)
            activity.broadcastReloadConfig()
            if (!it) FileUtil.clearLog("other")
        }
        var debug by remember { mutableStateOf(AppConfig.INSTANCE.enableDebugLog ?: false) }
        LogSwitchRow("抓包记录", debug, onClick = { openLog(LogType.DEBUG) }) {
            debug = it
            AppConfig.INSTANCE.enableDebugLog = it
            ConfigSync.saveAppConfig(context)
            activity.broadcastReloadConfig()
        }
        var error by remember { mutableStateOf(AppConfig.INSTANCE.enableViewErrorLog ?: true) }
        LogSwitchRow("查看异常日志", error, onClick = { openLog(LogType.ERROR) }) {
            error = it
            AppConfig.INSTANCE.enableViewErrorLog = it
            ConfigSync.saveAppConfig(context)
            activity.broadcastReloadConfig()
            if (!it) FileUtil.clearLog("error")
        }
        var runtime by remember { mutableStateOf(AppConfig.INSTANCE.enableViewRuntimeLog ?: true) }
        LogSwitchRow("查看运行日志", runtime, onClick = { openLog(LogType.RUNTIME) }) {
            runtime = it
            AppConfig.INSTANCE.enableViewRuntimeLog = it
            ConfigSync.saveAppConfig(context)
            activity.broadcastReloadConfig()
            if (!it) FileUtil.clearLog("runtime")
        }
    }
    Spacer(Modifier.height(16.dp))

    SmallTitle(text = "日志与诊断")
    CardColumn {
        ArrowPreference(
            title = "完整日志",
            summary = "按分类查看当天日志，支持检索",
            onClick = { openLog(null) }
        )
        ArrowPreference(
            title = "导出日志包",
            summary = "全部分类日志打成一个 ZIP 放到 Download 目录",
            onClick = {
                Thread({
                    try {
                        val file = DiagnosticExport.export()
                        activity.runOnUiThread {
                            if (file != null) {
                                ToastUtil.show(context, "已导出到：\n" + file.path)
                            } else {
                                ToastUtil.show(context, "没有可导出的日志")
                            }
                        }
                    } catch (t: Throwable) {
                        Log.printStackTrace(t)
                        activity.runOnUiThread {
                            ToastUtil.show(context, "导出失败：" + (t.message ?: t.javaClass.simpleName))
                        }
                    }
                }, "Medusa-DiagnosticExport").start()
            }
        )
    }
    Spacer(Modifier.height(16.dp))

    SmallTitle(text = "数据管理")
    CardColumn {
        ArrowPreference(
            title = "配置文件",
            summary = if (userId.isNullOrEmpty()) "未识别账号，先登录支付宝"
            else "导入 / 导出 / 删除整份配置（当前账号）",
            onClick = {
                if (userId.isNullOrEmpty()) {
                    ToastUtil.show(context, "尚未识别到账号，请先打开并登录支付宝")
                } else {
                    context.startActivity(
                        Intent(context, MiuixSettingsActivity::class.java)
                            .putExtra(MiuixSettingsActivity.EXTRA_USER_ID, userId)
                    )
                }
            }
        )
        ArrowPreference(
            title = "备份与恢复",
            summary = "全量备份 · 域配置分类 · 文件夹识别",
            onClick = {
                context.startActivity(
                    Intent(context, MiuixBackupActivity::class.java).apply {
                        if (userId != null) putExtra(MiuixBackupActivity.EXTRA_USER_ID, userId)
                    }
                )
            }
        )
    }
    Spacer(Modifier.height(10.dp))
    Text(
        "口径：完成度来自每日任务账本（已完成 / 需完成，已剔除黑名单）；耗时只统计成功运行，满 2 次才给中位数；人工操作用时尚未接入。颜色越深表示完成越多，空白表示当天没有数据。",
        fontSize = MedusaType.Caption,
        lineHeight = 16.sp,
        color = Color(0xFFA2ACBC),
        modifier = Modifier.padding(horizontal = 6.dp)
    )
    Spacer(Modifier.height(80.dp))
}

@Composable
private fun DataMetric(label: String, value: String, tone: Color, modifier: Modifier) {
    Column(modifier) {
        Text(value, fontSize = MedusaType.CardTitle, fontWeight = FontWeight.Bold, color = tone)
        Text(label, fontSize = MedusaType.Caption, color = MedusaColors.Muted, modifier = Modifier.padding(top = 2.dp))
    }
}

/** 近 14 天 × 域热力图：颜色越深完成度越高；空白 = 当天没有数据。 */
@Composable
private fun Heatmap(metrics: List<DailyMetricsStore.DayMetrics>, loading: Boolean) {
    val formatter = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
    val days = (13 downTo 0).map { offset ->
        val calendar = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -offset) }
        formatter.format(calendar.time)
    }
    val byDay = metrics.associateBy { it.day }
    val domains = DailyMetricsStore.domainsInOrder().filter { domain ->
        metrics.any { day -> day.domains.any { it.domain == domain } }
    }
    if (domains.isEmpty()) {
        Text(
            if (loading) "加载中…" else "还没有可统计的账本数据。",
            fontSize = MedusaType.Body,
            color = MedusaColors.Muted,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
        )
        return
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
        domains.forEach { domain ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    DailyMetricsStore.labelOf(domain),
                    fontSize = MedusaType.Caption,
                    color = MedusaColors.Muted,
                    modifier = Modifier.width(34.dp)
                )
                days.forEach { day ->
                    val entry = byDay[day]?.domains?.firstOrNull { it.domain == domain }
                    val color = if (entry == null) Color(0xFFF2F4F7)
                    else MedusaColors.GreenDeep.copy(alpha = 0.10f + 0.72f * entry.ratio)
                    Box(
                        Modifier
                            .weight(1f)
                            .height(16.dp)
                            .padding(horizontal = 1.5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(color)
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("近 14 天", fontSize = MedusaType.Caption, color = Color(0xFFA2ACBC))
            Spacer(Modifier.weight(1f))
            Text("越深越完整", fontSize = MedusaType.Caption, color = Color(0xFFA2ACBC))
        }
    }
}

/** 每域耗时中位数：条形按最长域归一。 */
@Composable
private fun DurationBars(medians: Map<String, Pair<Int, Int>>, loading: Boolean) {
    if (medians.isEmpty()) {
        Text(
            if (loading) "加载中…" else "成功运行满 2 次后才会显示中位数。",
            fontSize = MedusaType.Body,
            color = MedusaColors.Muted,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
        )
        return
    }
    val max = medians.values.maxOfOrNull { it.first }?.coerceAtLeast(1) ?: 1
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        medians.entries.sortedByDescending { it.value.first }.take(6).forEach { (domain, value) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    DailyMetricsStore.labelOf(domain),
                    fontSize = MedusaType.Caption,
                    color = MedusaColors.Muted,
                    modifier = Modifier.width(34.dp)
                )
                Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(99.dp)).background(Color(0xFFEDF1F6))) {
                    Box(
                        Modifier
                            .fillMaxWidth(value.first.toFloat() / max)
                            .height(8.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(MedusaColors.GreenDeep.copy(alpha = 0.75f))
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "${value.first} 分 · ${value.second} 次",
                    fontSize = MedusaType.Caption,
                    color = MedusaColors.Muted
                )
            }
        }
    }
}

/**
 * 日志条目行：**点按整行**进入对应日志详情，右侧开关控制是否记录。
 * 行样式与配置页一致（MedusaSwitchRow），点标题进日志、点药丸只切开关。
 */
@Composable
fun LogSwitchRow(title: String, checked: Boolean, onClick: () -> Unit, onCheckedChange: (Boolean) -> Unit) {
    MedusaSwitchRow(
        title = title,
        checked = checked,
        onClick = onClick,
        onCheckedChange = onCheckedChange
    )
}

/** 打开日志查看器(显示指定日志类型的全部条目) */
fun openLog(activity: MiuixMainActivity, logType: LogType) {
    if (!activity.hasPermission) {
        // 没有文件权限时进查看器只会读到空/崩溃，先在入口拦住并引导授权
        ToastUtil.show(activity, "无法读取日志：" + PermissionUtil.getStorageStatusText(activity))
        activity.requestStoragePermission()
        return
    }
    try {
        activity.startActivity(
            Intent(activity, MiuixLogViewerActivity::class.java)
                .putExtra(LogType.EXTRA_LOG_TYPE, logType.name)
        )
    } catch (t: Throwable) {
        Log.printStackTrace(t)
        ToastUtil.show(activity, "打开日志失败：${t.message ?: "未知错误"}")
    }
}

internal fun openGroupSettings(activity: MiuixMainActivity, groupCode: String) {
    if (!activity.storageWritable) {
        ToastUtil.show(activity, "请先授予「所有文件访问权限」再编辑配置")
        activity.requestStoragePermission()
        return
    }
    val userId = activity.resolveCurrentAccount()
    activity.startActivity(
        Intent(activity, MiuixGroupFieldsActivity::class.java).apply {
            putExtra(MiuixGroupFieldsActivity.EXTRA_GROUP_CODE, groupCode)
            if (userId != null) {
                putExtra(MiuixGroupFieldsActivity.EXTRA_USER_ID, userId)
            }
        }
    )
}

internal fun openDomainSettings(activity: MiuixMainActivity, domainKey: String, groupCode: String, title: String) {
    if (!activity.storageWritable) {
        ToastUtil.show(activity, "请先授予「所有文件访问权限」再编辑配置")
        activity.requestStoragePermission()
        return
    }
    val userId = activity.resolveCurrentAccount()
    activity.startActivity(
        Intent(activity, MiuixDomainActivity::class.java).apply {
            putExtra(MiuixDomainActivity.EXTRA_DOMAIN_KEY, domainKey)
            putExtra(MiuixDomainActivity.EXTRA_DOMAIN_TITLE, title)
            putExtra(MiuixDomainActivity.EXTRA_GROUP_CODE, groupCode)
            if (userId != null) {
                putExtra(MiuixDomainActivity.EXTRA_USER_ID, userId)
            }
        }
    )
}

@Composable
fun ConfigTab(activity: MiuixMainActivity) {
    val context = LocalContext.current
    val currentId = activity.currentAccountId
    var manualPaused by remember { mutableStateOf(RiskGuard.isManualPause()) }
    var riskPaused by remember { mutableStateOf(RiskGuard.isGlobalPaused()) }

    LaunchedEffect(Unit) {
        while (true) {
            manualPaused = RiskGuard.isManualPause()
            riskPaused = RiskGuard.isGlobalPaused()
            delay(2000L)
        }
    }

    fun setMaster(on: Boolean) {
        if (on) RiskGuard.resumeManual() else RiskGuard.pauseAll("手动关闭自动化")
        activity.broadcastReloadConfig()
        manualPaused = RiskGuard.isManualPause()
        riskPaused = RiskGuard.isGlobalPaused()
        ToastUtil.show(context, if (on) "自动化已开启，按计划执行" else "自动化已关闭，计划与状态读取都会停下")
    }

    Text(
        text = "配置",
        fontSize = MedusaType.PageTitle,
        fontWeight = FontWeight.Bold,
        color = MiuixTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
    )

    SmallTitle(text = "账号与设备")
    AccountSummaryCard(activity)
    Spacer(Modifier.height(10.dp))
    CardColumn {
        ArrowPreference(
            title = "文件访问权限",
            summary = PermissionUtil.getStorageStatusText(context) +
                if (activity.storageWritable) "" else "（点击去授权）",
            onClick = {
                try {
                    activity.requestStoragePermission()
                } catch (e: Exception) {
                    ToastUtil.show(context, "申请权限失败")
                }
            }
        )
        ArrowPreference(
            title = "关于应用",
            summary = ViewAppInfo.getAppVersion(),
            onClick = { context.startActivity(Intent(context, MiuixAboutActivity::class.java)) }
        )
    }
    Spacer(Modifier.height(16.dp))

    CardColumn {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "自动化总开关",
                        fontSize = MedusaType.CardTitle,
                        fontWeight = FontWeight.Bold,
                        color = MedusaColors.Ink
                    )
                    Text(
                        text = when {
                            manualPaused -> "已关闭 · 计划不执行，状态也不读取"
                            riskPaused -> "已开启 · 风控暂停中，冷却结束自动继续"
                            else -> "已开启 · 按计划自动执行"
                        },
                        fontSize = MedusaType.Secondary,
                        color = when {
                            manualPaused -> MedusaColors.Muted
                            riskPaused -> MedusaColors.Warning
                            else -> MedusaColors.GreenDeep
                        },
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
                MedusaPillSwitch(checked = !manualPaused, onCheckedChange = { setMaster(it) })
            }
            Text(
                "关闭只停止自动化，不修改任何计划，也不清掉 1009 冷却与待验证状态。",
                fontSize = MedusaType.Caption,
                color = Color(0xFFA2ACBC),
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
    Spacer(Modifier.height(16.dp))

    SmallTitle(text = "AI")
    CardColumn {
        val aiEnabled = AppConfig.INSTANCE.aiSummaryEnabled == true
        ArrowPreference(
            title = "AI 分析（只读）",
            summary = when {
                !aiEnabled -> "关闭 · 每日摘要与异常归因，只发聚合数字"
                else -> "已开启 · ${io.github.aw1y2z.sesame.util.ai.AiClient.describeReadiness()} · 每日摘要与期末汇总"
            },
            onClick = { context.startActivity(Intent(context, MiuixAiActivity::class.java)) }
        )
    }
    Spacer(Modifier.height(16.dp))

    SmallTitle(text = "运行策略")
    val planFlowMs = AppConfig.INSTANCE.planFlowMs ?: AppConfig.DEFAULT_PLAN_FLOW_MS
    val planFlowJitter = AppConfig.INSTANCE.planFlowJitterPercent ?: AppConfig.DEFAULT_PLAN_FLOW_JITTER
    CardColumn {
        var wholeDomain by remember { mutableStateOf(AppConfig.INSTANCE.wholeDomainFallback ?: true) }
        BooleanSwitch(
            "整域兜底巡检",
            wholeDomain,
            summary = "已开启但没排到计划的域，每日兜底时补跑一轮；原计划照常执行"
        ) {
            AppConfig.INSTANCE.wholeDomainFallback = it
            ConfigSync.saveAppConfig(context)
            activity.broadcastReloadConfig()
            wholeDomain = it
        }
        ArrowPreference(
            title = "每日兜底时间",
            summary = "22:30 由调度器统一补跑未完成项（固定，不占计划位）",
            onClick = {
                AlertDialog.Builder(context)
                    .setTitle("每日兜底")
                    .setMessage("每天 22:30–23:59 之间，调度器会把当天未完成的计划补跑一次；开启「整域兜底巡检」后，还会为已开启但没排到计划的域补一轮。")
                    .setPositiveButton("知道了", null)
                    .show()
            }
        )
        ArrowPreference(
            title = "计划流阈值",
            summary = if (planFlowMs <= 0) "已关闭（写请求不限速）"
            else "$planFlowMs 毫秒 · 抖动 $planFlowJitter%",
            onClick = { context.startActivity(Intent(context, MiuixPlanFlowActivity::class.java)) }
        )
        IntSettingRow(
            title = "手动节律建议（按目标）",
            value = AppConfig.INSTANCE.manualCooldownTarget ?: 60,
            unit = "分钟"
        ) {
            AppConfig.INSTANCE.manualCooldownTarget = it
            ConfigSync.saveAppConfig(context)
            activity.broadcastReloadConfig()
        }
        ArrowPreference(
            title = "1009 后",
            summary = "立即停止当前运行并等待验证（固定）",
            onClick = {
                AlertDialog.Builder(context)
                    .setTitle("1009 后")
                    .setMessage("检测到 1009 / 安全验证时，会立即停止当前运行并进入待验证状态；请在支付宝完成滑块后，回首页点“已验证”恢复。")
                    .setPositiveButton("知道了", null)
                    .show()
            }
        )
    }
    Spacer(Modifier.height(16.dp))

    SmallTitle(text = "通知与系统")
    CardColumn {
        // 这几项是 AppConfig（模块级，不分账号）；原先放在账号配置里，现归全域设置。
        var showToast by remember { mutableStateOf(AppConfig.INSTANCE.showToast ?: true) }
        BooleanSwitch(
            "应用内气泡提示",
            showToast,
            summary = "前台显示轻提示；后台重要事件仍发系统通知"
        ) {
            AppConfig.INSTANCE.showToast = it
            ConfigSync.saveAppConfig(context)
            showToast = it
            activity.broadcastReloadConfig()
        }
        var enableOnGoing by remember { mutableStateOf(AppConfig.INSTANCE.enableOnGoing ?: false) }
        BooleanSwitch(
            "常驻通知栏",
            enableOnGoing,
            summary = "运行状态通知保持常驻，避免被误划掉"
        ) {
            AppConfig.INSTANCE.enableOnGoing = it
            ConfigSync.saveAppConfig(context)
            enableOnGoing = it
            activity.broadcastReloadConfig()
        }
        var closeCaptchaDialog by remember { mutableStateOf(AppConfig.INSTANCE.closeCaptchaDialog ?: true) }
        BooleanSwitch(
            "自动关闭网络提示弹窗",
            closeCaptchaDialog,
            summary = "仅关闭 VPN/代理/人气太旺提示；安全验证与人脸识别保留"
        ) {
            AppConfig.INSTANCE.closeCaptchaDialog = it
            ConfigSync.saveAppConfig(context)
            closeCaptchaDialog = it
            activity.broadcastReloadConfig()
        }
        var noticeFilter by remember { mutableStateOf(AppConfig.INSTANCE.noticeFilterMarketing ?: true) }
        BooleanSwitch(
            "过滤支付宝营销通知",
            noticeFilter,
            summary = "只拦营销/活动/推荐/福利/优惠；支付、转账、安全、验证保留"
        ) {
            AppConfig.INSTANCE.noticeFilterMarketing = it
            ConfigSync.saveAppConfig(context)
            noticeFilter = it
        }
        var onlyMedusa by remember { mutableStateOf(AppConfig.INSTANCE.noticeOnlyMedusa ?: false) }
        BooleanSwitch(
            "仅保留美杜莎",
            onlyMedusa,
            summary = "高级：取消支付宝其余通知；可能漏掉支付/安全提醒，默认关闭"
        ) {
            AppConfig.INSTANCE.noticeOnlyMedusa = it
            ConfigSync.saveAppConfig(context)
            onlyMedusa = it
        }
        ArrowPreference(
            title = "通知使用权",
            summary = if (activity.isNotificationListenerEnabled()) {
                "已授权 · 营销过滤生效；内容只在本机判断"
            } else {
                "未授权 · 点此开启通知访问，才能过滤支付宝营销通知"
            },
            onClick = { activity.openNotificationListenerSettings() }
        )
        ArrowPreference(
            title = "系统通知权限",
            summary = if (activity.isNotificationPermissionGranted()) {
                "已允许美杜莎发通知"
            } else {
                "未允许 · 点此授权，否则通知栏不会显示美杜莎提醒"
            },
            onClick = { activity.requestNotificationPermission() }
        )
    }
    Spacer(Modifier.height(16.dp))

    SmallTitle(text = "高级与工具")
    CardColumn {
        ArrowPreference(
            title = "基础字段",
            summary = "启动 · 唤醒 · RPC",
            onClick = { openGroupSettings(activity, "BASE") }
        )
        ArrowPreference(
            title = "AI答题字段",
            summary = "自定义 AI 答题配置",
            onClick = { openGroupSettings(activity, "ANSWER_AI") }
        )
        ArrowPreference(
            title = "好友统计",
            onClick = { context.startActivity(Intent(context, MiuixFriendStatsActivity::class.java)) }
        )
        ArrowPreference(
            title = "扩展功能",
            onClick = { context.startActivity(Intent(context, MiuixExtensionsActivity::class.java)) }
        )
        ArrowPreference(
            title = "系统状态",
            summary = "能力 · 调度 · 风控 · 数据",
            onClick = { context.startActivity(Intent(context, MiuixSystemStatusActivity::class.java)) }
        )
        ArrowPreference(
            title = "开源致谢",
            summary = "上游项目 · 第三方库 · 代码构成",
            onClick = { context.startActivity(Intent(context, MiuixCreditsActivity::class.java)) }
        )
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun AccountSummaryCard(activity: MiuixMainActivity) {
    val context = LocalContext.current
    val userId = activity.currentAccountId
    val entity = remember(userId) {
        if (userId.isNullOrEmpty()) null else runCatching {
            UserIdMap.loadSelf(userId)
            UserIdMap.get(userId)
        }.getOrNull()
    }
    val name = activity.alipayProfile?.name?.takeIf { it.isNotBlank() }
        ?: entity?.showName?.takeIf { it.isNotBlank() }
    val account = entity?.account?.takeIf { it.isNotBlank() }
    val loggedIn = !userId.isNullOrEmpty()
    val status = when {
        !loggedIn -> "未登录"
        activity.storageWritable -> "已登录"
        else -> "待授权"
    }
    val statusTone = when {
        !loggedIn -> MedusaColors.Warning
        activity.storageWritable -> MedusaColors.GreenDeep
        else -> MedusaColors.Warning
    }

    val shape = RoundedCornerShape(20.dp)
    val cardModifier = Modifier
        .fillMaxWidth()
        .shadow(8.dp, shape)
        .clip(shape)
        .background(Color.White)
        .let { base ->
            if (loggedIn) base else base.clickable {
                activity.sendQueryBroadcast()
                ToastUtil.show(context, "已请求刷新；请确认支付宝已登录且文件权限已授予")
            }
        }

    Row(
        cardModifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HomeAvatar(activity.alipayProfile?.avatarUrl, name ?: "美杜莎", onDark = false)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name ?: "尚未识别到账号",
                    fontSize = MedusaType.CardTitle,
                    fontWeight = FontWeight.SemiBold,
                    color = MedusaColors.Ink,
                    maxLines = 1
                )
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(6.dp).background(statusTone, CircleShape))
                Spacer(Modifier.width(4.dp))
                Text(
                    text = status,
                    fontSize = MedusaType.Caption,
                    color = MedusaColors.Muted,
                    maxLines = 1
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = account ?: userId ?: "打开支付宝登录后点此刷新",
                fontSize = MedusaType.Body,
                color = MedusaColors.Muted,
                maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (loggedIn) {
                    "UID $userId · " + if (activity.storageWritable) "配置已同步" else "文件权限待授权"
                } else {
                    "配置由支付宝账号同步"
                },
                fontSize = MedusaType.Caption,
                color = MedusaColors.Muted,
                maxLines = 1
            )
        }
    }
}

@Composable
fun BooleanSwitch(title: String, checked: Boolean, summary: String? = null, onCheckedChange: (Boolean) -> Unit) {
    MedusaSwitchRow(
        title = title,
        summary = summary,
        checked = checked,
        onCheckedChange = onCheckedChange
    )
}

/** 3.0：可调分钟数，0=关闭；只用于节律建议，不限制执行。 */
@Composable
private fun IntSettingRow(title: String, value: Int, unit: String, onValue: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var current by remember { mutableIntStateOf(value) }
    var text by remember { mutableStateOf(value.toString()) }
    ArrowPreference(
        title = title,
        summary = if (current <= 0) "关闭（0=关闭）" else "$current $unit（0=关闭）",
        onClick = { expanded = !expanded }
    )
    if (expanded) {
        top.yukonga.miuix.kmp.basic.TextField(
            value = text,
            onValueChange = { raw ->
                val filtered = raw.filter { it.isDigit() }.take(4)
                text = filtered
                filtered.toIntOrNull()?.let {
                    current = it
                    onValue(it)
                }
            },
            label = "",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun CardColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    // Card 只传 modifier：preference 行直接作为子项，行的左右缩进交给行自身的 insideMargin。
    // 之前给 Card 传 insideMargin 会把所有行整体往里缩，行自带的方形按压高亮就成了"悬在卡片里的方框"；
    // 让行顶满卡片宽度后，高亮是一条通栏色带，圆角由卡片自身裁剪处理。
    Card(modifier = modifier.fillMaxWidth()) {
        content()
    }
}
