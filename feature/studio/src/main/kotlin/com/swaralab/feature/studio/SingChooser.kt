package com.swaralab.feature.studio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.swaralab.domain.model.SingMode
import com.swaralab.ui.AppCopy
import com.swaralab.ui.CompactChoiceCard
import com.swaralab.ui.SwaraColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SingChooserSheet(
    copy: AppCopy,
    onSelect: (SingMode) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SwaraColors.Card,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = copy.howToSing,
                style = MaterialTheme.typography.headlineSmall,
                color = SwaraColors.Ink,
            )
            Text(
                text = copy.howToSingBody,
                style = MaterialTheme.typography.bodyMedium,
                color = SwaraColors.Muted,
            )
            CompactChoiceCard(
                title = copy.singSolo,
                body = copy.singSoloBody,
                onClick = { onSelect(SingMode.SOLO) },
                accent = SwaraColors.Teal,
                accentWash = SwaraColors.TealWash,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CompactChoiceCard(
                    title = copy.singDuet,
                    body = copy.singDuetBody,
                    onClick = { onSelect(SingMode.DUET) },
                    modifier = Modifier.weight(1f),
                    accent = SwaraColors.Rose,
                    accentWash = SwaraColors.RoseWash,
                )
                CompactChoiceCard(
                    title = copy.singGroup,
                    body = copy.singGroupBody,
                    onClick = { onSelect(SingMode.GROUP) },
                    modifier = Modifier.weight(1f),
                    accent = SwaraColors.Gold,
                    accentWash = SwaraColors.GoldWash,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CompactChoiceCard(
                    title = copy.singFromPhone,
                    body = copy.singFromPhoneBody,
                    onClick = { onSelect(SingMode.FROM_PHONE) },
                    modifier = Modifier.weight(1f),
                    accent = SwaraColors.Teal,
                    accentWash = SwaraColors.TealWash,
                )
                CompactChoiceCard(
                    title = copy.singLyrics,
                    body = copy.singLyricsBody,
                    onClick = { onSelect(SingMode.LYRICS) },
                    modifier = Modifier.weight(1f),
                    accent = SwaraColors.Gold,
                    accentWash = SwaraColors.GoldWash,
                )
            }
        }
    }
}
