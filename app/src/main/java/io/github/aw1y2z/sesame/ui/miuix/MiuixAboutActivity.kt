package io.github.aw1y2z.sesame.ui.miuix

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aw1y2z.sesame.R
import io.github.aw1y2z.sesame.data.ViewAppInfo
import io.github.aw1y2z.sesame.util.ToastUtil
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 关于应用二级页：品牌头图 + 版本信息 + 使用说明 + 开源致谢。 */
class MiuixAboutActivity : MiuixBaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setAppContent {
            AboutScreen(this)
        }
    }
}

@Composable
fun AboutScreen(activity: MiuixAboutActivity) {
    Scaffold(
        topBar = {
            LogTopBar(
                title = "关于",
                onBack = { activity.finish() }
            )
        },
        containerColor = MiuixTheme.colorScheme.surface
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 24.dp)
        ) {
            BrandAboutHeader()
            Spacer(Modifier.height(16.dp))

            CardColumn {
                AboutRow("版本", "V${ViewAppInfo.getAppVersion()}")
                AboutRow("模块框架", "libxposed API 102")
                AboutRow("基线", "Sesame-M 1.5.3")
                AboutRow("免 ROOT 方案", "NPatch 1.0.9 内嵌")
            }
            Spacer(Modifier.height(16.dp))

            AboutSectionTitle("使用说明")
            CardColumn {
                HintRow("1", "安装美杜莎模块 App，用于查看状态与调整配置。")
                HintRow("2", "安装内置模块的支付宝免 ROOT 包；已 root 用户也可用 LSPosed 启用本模块。")
                HintRow("3", "打开支付宝，模块自动加载。回到美杜莎首页可确认「运行中」。")
                HintRow("4", "在「配置」中按需开启森林、庄园、海洋、会员等任务。")
            }
            Spacer(Modifier.height(16.dp))

            AboutSectionTitle("开源与致谢")
            CardColumn {
                ArrowPreference(
                    title = "开源致谢与代码构成",
                    summary = "上游 Sesame-M · 第三方库清单 · 自有代码占比",
                    onClick = {
                        activity.startActivity(Intent(activity, MiuixCreditsActivity::class.java))
                    }
                )
            }
            Spacer(Modifier.height(16.dp))

            AboutSectionTitle("使用须知")
            CardColumn {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(
                        text = "本模块仅供学习与技术交流，不得用于商业用途。自动化操作可能触发支付宝风控，请自行评估账号与资金风险；因使用本模块造成的任何后果由使用者自行承担。",
                        fontSize = MedusaType.Body,
                        lineHeight = 19.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = "MEDUSA · 美杜莎 V${ViewAppInfo.getAppVersion()}",
                fontSize = MedusaType.Secondary,
                letterSpacing = 1.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun BrandAboutHeader() {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF241241), Color(0xFF4A2378), Color(0xFF0C6E62))
                )
            )
            .padding(horizontal = 20.dp, vertical = 22.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(78.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.medusa_logo),
                    contentDescription = null,
                    modifier = Modifier
                        .size(70.dp)
                        .clip(CircleShape)
                )
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = "美杜莎",
                    fontSize = MedusaType.PageTitle,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "MEDUSA · V${ViewAppInfo.getAppVersion()}",
                    fontSize = MedusaType.Secondary,
                    letterSpacing = 2.sp,
                    color = Color(0xFFE8C879)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "支付宝自动化模块",
                    fontSize = MedusaType.Body,
                    color = Color.White.copy(alpha = 0.78f)
                )
            }
        }
    }
}

@Composable
private fun AboutSectionTitle(text: String) {
    Text(
        text = text,
        fontSize = MedusaType.Body,
        fontWeight = FontWeight.Medium,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = MedusaType.CardTitle,
            color = MiuixTheme.colorScheme.onBackground
        )
        Text(
            text = value,
            fontSize = MedusaType.Body,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
    }
}

@Composable
private fun HintRow(index: String, text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(Color(0xFF5B2E91).copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = index,
                fontSize = MedusaType.Secondary,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF5B2E91)
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            fontSize = MedusaType.Body,
            lineHeight = 19.sp,
            color = MiuixTheme.colorScheme.onBackground
        )
    }
}

private fun openWebUrl(activity: MiuixBaseActivity, url: String) {
    try {
        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
        ToastUtil.show(activity, "无法打开链接")
    }
}
