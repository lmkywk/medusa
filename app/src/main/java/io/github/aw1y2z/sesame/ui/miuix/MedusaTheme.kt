package io.github.aw1y2z.sesame.ui.miuix

import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.TextStyles
import top.yukonga.miuix.kmp.theme.defaultTextStyles
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * 浅色主题：主色绿色。整套 primary 家族一起覆盖，否则禁用开关、日志筛选 chip、
 * 下拉选中项会残留 Miuix 默认蓝。error 保持红色。
 */
internal val MedusaLightColors: Colors = lightColorScheme().copy(
    primary = MedusaColors.GreenDeep,
    onPrimary = Color.White,
    primaryVariant = MedusaColors.GreenDeep,
    onPrimaryVariant = Color(0xFFE6F7EE),
    primaryContainer = Color(0xFFE6F7EE),
    onPrimaryContainer = MedusaColors.GreenDeep,
    disabledPrimary = Color(0xFFB8E8CE),
    disabledOnPrimary = Color(0xFFF3FBF7),
    disabledPrimaryButton = Color(0xFFB8E8CE),
    disabledOnPrimaryButton = Color.White,
    disabledPrimarySlider = Color(0xFFB8E8CE),
    tertiaryContainer = Color(0xFFE6F7EE),
    onTertiaryContainer = MedusaColors.GreenDeep,
    sliderKeyPoint = MedusaColors.GreenDeep.copy(alpha = 0.30f),
    sliderKeyPointForeground = MedusaColors.GreenDeep,
)

private val BaseTextStyles = defaultTextStyles()

/**
 * 把 Miuix 组件自带的字号收进五档：偏好行标题 headline1、摘要 body2、SmallTitle subtitle、
 * TextButton button、TextField main 都会跟着变。未使用的 title2/3/4 保留库默认。
 */
internal val MedusaTextStyles: TextStyles = BaseTextStyles.copy(
    main = BaseTextStyles.main.copy(fontSize = MedusaType.Body),
    paragraph = BaseTextStyles.paragraph.copy(fontSize = MedusaType.Body),
    body1 = BaseTextStyles.body1.copy(fontSize = MedusaType.Body),
    body2 = BaseTextStyles.body2.copy(fontSize = MedusaType.Body),
    button = BaseTextStyles.button.copy(fontSize = MedusaType.CardTitle),
    footnote1 = BaseTextStyles.footnote1.copy(fontSize = MedusaType.Secondary),
    footnote2 = BaseTextStyles.footnote2.copy(fontSize = MedusaType.Caption),
    headline1 = BaseTextStyles.headline1.copy(fontSize = MedusaType.CardTitle),
    headline2 = BaseTextStyles.headline2.copy(fontSize = MedusaType.CardTitle),
    subtitle = BaseTextStyles.subtitle.copy(fontSize = MedusaType.Body),
    title1 = BaseTextStyles.title1.copy(fontSize = MedusaType.PageTitle),
)
