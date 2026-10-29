package io.github.aw1y2z.sesame.ui.miuix

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import io.github.aw1y2z.sesame.data.ViewAppInfo
import io.github.aw1y2z.sesame.notify.MedusaNoticeCenter
import io.github.aw1y2z.sesame.util.LanguageUtil
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

open class MiuixBaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageUtil.setLocal(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ViewAppInfo.init(applicationContext)
        setupSystemBars()
    }

    /**
     * 所有 miuix 页面统一用此方法设置内容。
     * 当前版本只做浅色：首页/计划/设置页是写死的浅色自绘卡片，若允许组件跟随系统深色，
     * 同屏会出现「深色 Miuix 卡片 + 浅色自绘卡片」的分裂，且标题白字在浅底上直接看不见。
     */
    protected fun setAppContent(bubbleBottomPadding: Dp = 76.dp, content: @Composable () -> Unit) {
        setContent {
            val controller = remember {
                ThemeController(
                    colorSchemeMode = ColorSchemeMode.Light,
                    lightColors = MedusaLightColors,
                )
            }
            MiuixTheme(controller, textStyles = MedusaTextStyles) {
                Box(Modifier.fillMaxSize()) {
                    content()
                    MedusaBubbleOverlay(
                        onOpen = {},
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(start = 16.dp, end = 16.dp, bottom = bubbleBottomPadding)
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 所有模块页面共用白色气泡；用活动计数避免 A→B 切换时 B 先 resume、A 后 pause 被误判为后台。
        MedusaNoticeCenter.onActivityResumed()
    }

    override fun onPause() {
        MedusaNoticeCenter.onActivityPaused()
        super.onPause()
    }

    private fun setupSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
    }
}
