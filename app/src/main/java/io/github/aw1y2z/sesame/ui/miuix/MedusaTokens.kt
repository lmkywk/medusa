package io.github.aw1y2z.sesame.ui.miuix

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

internal object MedusaColors {
    val PageBg = Color(0xFFF4F8FD)
    val Ink = Color(0xFF11162A)
    val Muted = Color(0xFF7B8598)
    val Green = Color(0xFF2ED47A)
    val GreenDeep = Color(0xFF16A968)
    val Purple = Color(0xFF7B61FF)
    val Gold = Color(0xFFF5A623)
    val Blue = Color(0xFF2F9BFF)
    val Line = Color(0xFFE7EDF6)
    val Card = Color(0xFFFFFFFF)
    val HeroStart = Color(0xFF111047)
    val HeroMid = Color(0xFF171A5C)
    val HeroBlue = Color(0xFF0D3D69)
    val HeroTeal = Color(0xFF0B5D58)
    val Danger = Color(0xFFD64545)
    val Warning = Color(0xFFF0A020)

    // 3.0 语义状态：只表达状态，不引入新的强调色
    val Success = Green
    val SuccessSoft = Color(0xFFE6F7EE)
    val Cooldown = Warning
    val CooldownSoft = Color(0xFFFFF4E5)
    val Stopped = Danger
    val StoppedSoft = Color(0xFFFDEEEE)
    val DisabledText = Color(0xFFB9C2D0)
    val DisabledSurface = Color(0xFFF2F5FA)
    val OnDarkSecondary = Color(0x99FFFFFF)
    val OnDarkCaption = Color(0x80FFFFFF)
}

/** 全应用五档字号；只放这五个值，新增字号先回到这里定档。 */
internal object MedusaType {
    val PageTitle: TextUnit = 28.sp
    val CardTitle: TextUnit = 16.sp
    val Body: TextUnit = 13.sp
    val Secondary: TextUnit = 11.sp
    val Caption: TextUnit = 9.5.sp
}

internal val MedusaHeroBrush = Brush.linearGradient(
    listOf(
        MedusaColors.HeroStart,
        MedusaColors.HeroMid,
        MedusaColors.HeroBlue,
        MedusaColors.HeroTeal
    )
)
