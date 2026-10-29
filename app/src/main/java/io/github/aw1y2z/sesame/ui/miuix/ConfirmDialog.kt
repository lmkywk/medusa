package io.github.aw1y2z.sesame.ui.miuix

import androidx.compose.foundation.background
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = "确定",
    dismissText: String = "取消",
    confirmTone: MedusaButtonTone = MedusaButtonTone.Primary
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .shadow(5.dp, RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp))
                .background(MiuixTheme.colorScheme.surface, RoundedCornerShape(18.dp))
                .padding(18.dp)
        ) {
            Text(
                title,
                fontSize = MedusaType.CardTitle,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text,
                fontSize = MedusaType.Body,
                color = MiuixTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(16.dp))
            MedusaDialogActions(
                confirmText = confirmText,
                onConfirm = onConfirm,
                onDismiss = onDismiss,
                dismissText = dismissText,
                confirmTone = confirmTone
            )
        }
    }
}
