package io.github.aw1y2z.sesame.ui.miuix

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text

/** 全应用统一的药丸开关：扁、开启时用绿色渐变底和一点点阴影。 */
@Composable
fun MedusaPillSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier
            .width(40.dp)
            .height(20.dp)
            .shadow(if (checked) 2.dp else 0.dp, shape, clip = false)
            .clip(shape)
            .background(if (checked) MedusaColors.GreenDeep else Color(0xFFD7DEE8))
            .clickable { onCheckedChange(!checked) }
            .padding(2.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

/**
 * 带标题/说明的开关行，替换库的 SwitchPreference，让配置页与计划页、域页的药丸一致。
 * 传入 onClick 时点标题区触发它（日志页：点标题进详情，点药丸只切开关）；否则点整行切开关。
 */
@Composable
fun MedusaSwitchRow(
    title: String,
    checked: Boolean,
    summary: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable { if (onClick != null) onClick() else onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = MedusaType.CardTitle,
                fontWeight = FontWeight.Medium,
                color = MedusaColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!summary.isNullOrBlank()) {
                Text(
                    text = summary,
                    fontSize = MedusaType.Body,
                    color = MedusaColors.Muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        MedusaPillSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
