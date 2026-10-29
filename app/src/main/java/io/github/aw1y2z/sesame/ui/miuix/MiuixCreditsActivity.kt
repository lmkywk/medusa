package io.github.aw1y2z.sesame.ui.miuix

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aw1y2z.sesame.data.ViewAppInfo
import io.github.aw1y2z.sesame.util.ToastUtil
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 开源致谢：实际使用的上游项目与第三方库，以及自有代码占比。
 * 数字口径写在页面里，避免只给一个没有来由的百分比。
 */
class MiuixCreditsActivity : MiuixBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setAppContent { CreditsScreen(this) }
    }
}

private data class Library(
    val name: String,
    val version: String,
    val license: String,
    val purpose: String,
    val url: String
)

private val libraries = listOf(
    Library(
        "Miuix",
        "0.9.4",
        "Apache-2.0",
        "界面组件（卡片、开关、偏好行）",
        "https://github.com/miuix-kotlin-multiplatform/miuix"
    ),
    Library(
        "Haze",
        "1.7.3",
        "Apache-2.0",
        "底部导航的真实背景模糊",
        "https://github.com/chrisbanes/haze"
    ),
    Library(
        "libxposed",
        "102.0.0",
        "Apache-2.0",
        "模块注入框架（API / 免 ROOT 方案基线）",
        "https://github.com/libxposed/api"
    ),
    Library(
        "OkHttp",
        "4.12.0",
        "Apache-2.0",
        "AI 接口调用与网络请求",
        "https://github.com/square/okhttp"
    ),
    Library(
        "Jackson",
        "2.22",
        "Apache-2.0",
        "配置与数据的 JSON 解析",
        "https://github.com/FasterXML/jackson"
    ),
    Library(
        "XLog",
        "1.11.1",
        "Apache-2.0",
        "本地日志写入与滚动",
        "https://github.com/elvishew/xLog"
    ),
    Library(
        "NanoHTTPD",
        "2.3.1",
        "BSD-3-Clause",
        "本地回环服务（模块与界面通信）",
        "https://github.com/NanoHttpd/nanohttpd"
    ),
    Library(
        "Lombok",
        "1.18.48",
        "MIT",
        "编译期简化样板代码（仅编译使用）",
        "https://github.com/projectlombok/lombok"
    ),
    Library(
        "AndroidX / Jetpack Compose",
        "按 Gradle 锁定版本",
        "Apache-2.0",
        "基础框架与声明式界面",
        "https://developer.android.com/jetpack/androidx"
    ),
    Library(
        "Kotlin",
        "2.4.20",
        "Apache-2.0",
        "开发语言与标准库",
        "https://github.com/JetBrains/kotlin"
    )
)

@Composable
private fun CreditsScreen(activity: MiuixCreditsActivity) {
    Scaffold(
        topBar = {
            LogTopBar(title = "开源致谢", onBack = { activity.finish() })
        },
        containerColor = MiuixTheme.colorScheme.surface
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 24.dp)
        ) {
            Text(
                "美杜莎站在这些公开项目之上。这里只列实际使用的仓库，以及我们自己的代码占比。",
                fontSize = MedusaType.Body,
                lineHeight = 19.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )
            Spacer(Modifier.height(12.dp))

            CreditsTitle("上游基线")
            CardColumn {
                ArrowPreference(
                    title = "Sesame-M",
                    summary = "本应用的功能基线（支付宝任务模型与注入骨架），遵循原项目许可证",
                    onClick = { openUrl(activity, "https://github.com/aw1y2z/Sesame-M") }
                )
            }
            Spacer(Modifier.height(14.dp))

            CreditsTitle("第三方库")
            CardColumn {
                libraries.forEach { library ->
                    ArrowPreference(
                        title = library.name,
                        summary = library.version + " · " + library.license + " · " + library.purpose,
                        onClick = { openUrl(activity, library.url) }
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "以上为直接依赖；Jetpack Compose、Okio 等传递依赖同样遵循各自的开源许可证。",
                fontSize = MedusaType.Caption,
                color = MedusaColors.Muted,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
            Spacer(Modifier.height(14.dp))

            CreditsTitle("自有代码占比")
            CodeShareCard()
            Spacer(Modifier.height(14.dp))

            CreditsTitle("致谢")
            CardColumn {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(
                        "感谢 Sesame-M 原作者与所有上游维护者。没有这些公开的代码与组件，" +
                            "这个项目不会存在。我们把自己的改动也留在同样的开放态度里：" +
                            "上面每一行都可以点进去，看到它原本的样子。",
                        fontSize = MedusaType.Body,
                        lineHeight = 19.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "MEDUSA · 美杜莎 V${ViewAppInfo.getAppVersion()}",
                fontSize = MedusaType.Secondary,
                letterSpacing = 1.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun CodeShareCard() {
    // 统计：当前 Java/Kotlin 源码 99,387 行；Sesame-M 基线快照 66,807 行（净增 32,580 行）。
    // 数字随代码变化，页面标注统计日期，避免给出没有来由的百分比。
    val total = 100831
    val baseline = 66807
    val own = total - baseline
    val share = own * 100f / total

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                String.format(java.util.Locale.US, "%.1f%%", share),
                fontSize = MedusaType.PageTitle,
                fontWeight = FontWeight.Bold,
                color = MedusaColors.GreenDeep
            )
            Text(
                "自有代码",
                fontSize = MedusaType.Body,
                color = MedusaColors.Muted,
                modifier = Modifier.padding(start = 8.dp, bottom = 3.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(Color(0xFFEDF1F6))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(share / 100f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(MedusaColors.GreenDeep)
            )
        }
        Spacer(Modifier.height(10.dp))
        CodeShareRow("当前源码", String.format(java.util.Locale.US, "%,d 行", total))
        CodeShareRow("Sesame-M 基线", String.format(java.util.Locale.US, "%,d 行", baseline))
        CodeShareRow("我们新增 / 重写", String.format(java.util.Locale.US, "%,d 行", own))
        Spacer(Modifier.height(8.dp))
        Text(
            "口径：只统计 app/src/main/java 下的 Java 与 Kotlin 行数（含空行与注释），不含资源、构建脚本与第三方库；统计日期 2026-10-29。",
            fontSize = MedusaType.Caption,
            lineHeight = 16.sp,
            color = Color(0xFFA2ACBC)
        )
    }
}

@Composable
private fun CodeShareRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = MedusaType.Secondary, color = MedusaColors.Muted)
        Text(value, fontSize = MedusaType.Secondary, fontWeight = FontWeight.SemiBold, color = MedusaColors.Ink)
    }
}

@Composable
private fun CreditsTitle(text: String) {
    Text(
        text,
        fontSize = MedusaType.Body,
        fontWeight = FontWeight.Medium,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

private fun openUrl(activity: MiuixCreditsActivity, url: String) {
    try {
        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
        ToastUtil.show(activity, "无法打开链接")
    }
}
