package io.github.aw1y2z.sesame.ui.miuix

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text

enum class MedusaButtonTone { Primary, Secondary, Danger, Ghost }

/**
 * 统一操作按钮：高度 42dp、14dp 圆角、淡阴影。
 * 主按钮绿底白字，次按钮白底细边，危险按钮浅红底红字，不再使用大面积灰底。
 */
@Composable
fun MedusaActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: MedusaButtonTone = MedusaButtonTone.Primary,
    enabled: Boolean = true,
    height: Dp = 42.dp
) {
    val shape = RoundedCornerShape(14.dp)
    val background = when (tone) {
        MedusaButtonTone.Primary -> if (enabled) MedusaColors.GreenDeep else MedusaColors.DisabledSurface
        MedusaButtonTone.Secondary -> Color.White
        MedusaButtonTone.Danger -> if (enabled) MedusaColors.StoppedSoft else MedusaColors.DisabledSurface
        MedusaButtonTone.Ghost -> Color.Transparent
    }
    val foreground = when (tone) {
        MedusaButtonTone.Primary -> if (enabled) Color.White else MedusaColors.DisabledText
        MedusaButtonTone.Secondary -> if (enabled) MedusaColors.Ink else MedusaColors.DisabledText
        MedusaButtonTone.Danger -> if (enabled) MedusaColors.Stopped else MedusaColors.DisabledText
        MedusaButtonTone.Ghost -> if (enabled) MedusaColors.GreenDeep else MedusaColors.DisabledText
    }
    val borderColor = when (tone) {
        MedusaButtonTone.Secondary -> MedusaColors.Line
        MedusaButtonTone.Danger -> MedusaColors.Stopped.copy(alpha = 0.18f)
        else -> null
    }
    Box(
        modifier
            .height(height)
            .shadow(if (enabled && tone != MedusaButtonTone.Ghost) 3.dp else 0.dp, shape)
            .clip(shape)
            .background(background, shape)
            .then(
                if (borderColor != null) Modifier.border(1.dp, borderColor, shape) else Modifier
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = MedusaType.Body,
            fontWeight = FontWeight.SemiBold,
            color = foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 两列表单使用的紧凑字段卡：标签在上、值在下，右侧统一箭头。 */
@Composable
fun MedusaFieldCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .height(64.dp)
            .clip(shape)
            .background(Color.White, shape)
            .border(1.dp, MedusaColors.Line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            fontSize = MedusaType.Caption,
            color = MedusaColors.Muted
        )
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                fontSize = MedusaType.Body,
                fontWeight = FontWeight.SemiBold,
                color = MedusaColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(4.dp))
            Text("›", fontSize = MedusaType.CardTitle, color = MedusaColors.Muted)
        }
    }
}

/** 弹窗底部操作行：取消在左、主操作在右，等宽、同一高度。 */
@Composable
fun MedusaDialogActions(
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissText: String = "取消",
    confirmTone: MedusaButtonTone = MedusaButtonTone.Primary,
    confirmEnabled: Boolean = true
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MedusaActionButton(
            text = dismissText,
            onClick = onDismiss,
            modifier = Modifier.weight(1f),
            tone = MedusaButtonTone.Secondary,
            height = 40.dp
        )
        MedusaActionButton(
            text = confirmText,
            onClick = onConfirm,
            modifier = Modifier.weight(1f),
            tone = confirmTone,
            enabled = confirmEnabled,
            height = 40.dp
        )
    }
}
