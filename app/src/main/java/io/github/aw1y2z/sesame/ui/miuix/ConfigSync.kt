package io.github.aw1y2z.sesame.ui.miuix

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import io.github.aw1y2z.sesame.data.AppConfig
import io.github.aw1y2z.sesame.data.ConfigV2
import io.github.aw1y2z.sesame.util.FileUtil
import io.github.aw1y2z.sesame.util.Log
import io.github.aw1y2z.sesame.util.PermissionUtil
import io.github.aw1y2z.sesame.util.ToastUtil

enum class SaveOutcome { NO_CHANGES, SUCCESS, NO_PERMISSION, WRITE_FAILED }

data class SaveResult(val outcome: SaveOutcome, val detail: String? = null) {
    /** 无改动也视为成功：退出页面时不需要因此拦住用户 */
    val ok: Boolean get() = outcome == SaveOutcome.NO_CHANGES || outcome == SaveOutcome.SUCCESS
}

/**
 * 配置落盘的唯一入口：权限检查 → 写盘 → 通知注入进程重载。
 * 所有失败都带可读原因，避免只显示「保存失败」让用户无从下手。
 */
object ConfigSync {

    fun save(context: Context, userId: String?, showNoChanges: Boolean = false): SaveResult {
        if (!ConfigV2.hasFieldChanges()) {
            if (showNoChanges) {
                ToastUtil.show(context, "没有需要保存的改动")
            }
            return SaveResult(SaveOutcome.NO_CHANGES)
        }
        if (!PermissionUtil.checkFilePermissions(context)) {
            return SaveResult(SaveOutcome.NO_PERMISSION, "未授予「所有文件访问权限」")
        }
        if (!FileUtil.isMainDirectoryWritable()) {
            return SaveResult(
                SaveOutcome.NO_PERMISSION,
                FileUtil.getLastWriteError() ?: "共享配置目录不可写"
            )
        }
        return if (ConfigV2.save(userId, true)) {
            ToastUtil.show(
                context,
                if (userId.isNullOrEmpty()) {
                    "已保存到「默认模板」；已有账号的配置不受影响"
                } else {
                    "保存成功，已通知支付宝重载配置"
                }
            )
            notifyRestart(context, userId)
            SaveResult(SaveOutcome.SUCCESS)
        } else {
            SaveResult(SaveOutcome.WRITE_FAILED, FileUtil.getLastWriteError())
        }
    }

    /**
     * 开关/选择项变更后的静默落盘：不弹 Toast、不广播 restart。
     * 注入进程下一轮会重新读取配置；频繁广播会让宿主反复重载，反而让开关看起来“没生效”。
     */
    fun saveQuiet(context: Context, userId: String?): SaveResult {
        // 这是用户刚改完开关/选项后的显式落盘：不再用 hasFieldChanges 提前返回。
        // 300ms 防抖窗口内若配置被别的进程重载，基线会被刷新成新值，
        // 旧逻辑会误判“无改动”而直接跳过，表现为“开关又变回去了”。
        if (!PermissionUtil.checkFilePermissions(context)) {
            return SaveResult(SaveOutcome.NO_PERMISSION, "未授予「所有文件访问权限」")
        }
        if (!FileUtil.isMainDirectoryWritable()) {
            return SaveResult(
                SaveOutcome.NO_PERMISSION,
                FileUtil.getLastWriteError() ?: "共享配置目录不可写"
            )
        }
        return if (ConfigV2.save(userId, true)) {
            SaveResult(SaveOutcome.SUCCESS)
        } else {
            SaveResult(SaveOutcome.WRITE_FAILED, FileUtil.getLastWriteError())
        }
    }

    fun failureMessage(result: SaveResult): String = when (result.outcome) {
        SaveOutcome.NO_PERMISSION ->
            "保存失败：${result.detail ?: "共享目录不可写"}。请在系统设置中授予「所有文件访问权限」后重试。"
        SaveOutcome.WRITE_FAILED ->
            "保存失败：配置写入被拒绝" +
                (result.detail?.takeIf { it.isNotBlank() }?.let { "（$it）" } ?: "") +
                "。请检查文件访问权限与存储空间。"
        else -> ""
    }

    /** 只让注入进程重读配置，不重启 hook；供开关自动保存后即时生效 */
    fun notifyReload(context: Context, userId: String?) {
        if (userId.isNullOrEmpty()) return
        try {
            val reload = Intent("com.eg.android.AlipayGphone.sesame.reloadConfig")
            reload.putExtra("userId", userId)
            context.sendBroadcast(reload)
        } catch (th: Throwable) {
            Log.printStackTrace(th)
        }
    }

    /** 通知注入进程重新读取配置；默认模板不属于任何已登录账号，因此不广播 */
    fun notifyRestart(context: Context, userId: String?) {
        if (userId.isNullOrEmpty()) {
            return
        }
        try {
            // 两条都发：restart 让注入进程按目标账号完整重载；
            // reloadConfig 让当前进程无条件重读自己的账号配置，避免账号标识稍有偏差时完全不生效
            val intent = Intent("com.eg.android.AlipayGphone.sesame.restart")
            intent.putExtra("userId", userId)
            context.sendBroadcast(intent)
            val reload = Intent("com.eg.android.AlipayGphone.sesame.reloadConfig")
            reload.putExtra("userId", userId)
            context.sendBroadcast(reload)
        } catch (th: Throwable) {
            Log.printStackTrace(th)
        }
    }

    /** 全局 AppConfig 开关的保存：失败时明确提示，而不是让开关看起来开着却没落盘 */
    fun saveAppConfig(context: Context): Boolean {
        if (AppConfig.save()) {
            return true
        }
        val detail = FileUtil.getLastWriteError() ?: "无法写入配置文件"
        ToastUtil.show(context, "设置未保存：$detail。请检查「所有文件访问权限」。")
        return false
    }
}

/** 保存失败且原因是权限时弹出的引导框：确认后直接跳到系统授权页 */
@Composable
fun StoragePermissionDialog(show: Boolean, message: String, onDismiss: () -> Unit) {
    if (!show) {
        return
    }
    val context = LocalContext.current
    ConfirmDialog(
        title = "需要文件访问权限",
        text = message,
        confirmText = "去授权",
        dismissText = "稍后",
        onConfirm = {
            onDismiss()
            val activity = context as? AppCompatActivity
            if (activity != null) {
                PermissionUtil.checkOrRequestFilePermissions(activity)
            } else {
                PermissionUtil.openAllFilesAccessSettings(context)
            }
        },
        onDismiss = onDismiss
    )
}
