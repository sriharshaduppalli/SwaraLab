package com.swaralab.app.ui

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.swaralab.ui.AppCopy
import com.swaralab.ui.BoothRoom
import com.swaralab.ui.LocalAppCopy
import com.swaralab.ui.SwaraColors
import com.swaralab.ui.studioAtmosphere

private data class OnboardingPage(
    val mark: String,
    val title: String,
    val body: String,
)

@Composable
fun OnboardingRoute(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val copy = LocalAppCopy.current
    val pages = remember(copy) { onboardingPages(copy) }
    var page by remember { mutableIntStateOf(0) }
    val current = pages[page]

    Column(
        modifier
            .fillMaxSize()
            .studioAtmosphere(BoothRoom.HOME)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(120.dp)
                .background(SwaraColors.TealWash, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(current.mark, style = MaterialTheme.typography.displayLarge, color = SwaraColors.Teal)
        }
        Spacer(Modifier.height(28.dp))
        Text(
            current.title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            color = SwaraColors.Ink,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            current.body,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = SwaraColors.Muted,
        )
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pages.indices.forEach { index ->
                Box(
                    Modifier
                        .size(8.dp)
                        .background(
                            if (index == page) SwaraColors.Teal else SwaraColors.Line,
                            CircleShape,
                        ),
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        Surface(
            onClick = {
                if (page < pages.lastIndex) page++ else onFinished()
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = SwaraColors.Teal,
        ) {
            Text(
                text = if (page == pages.lastIndex) copy.onboardStart else copy.onboardNext,
                modifier = Modifier.padding(vertical = 14.dp),
                textAlign = TextAlign.Center,
                color = SwaraColors.Ink,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

private fun onboardingPages(copy: AppCopy): List<OnboardingPage> = listOf(
    OnboardingPage("♪", copy.singFromPhone, copy.singFromPhoneBody),
    OnboardingPage("●", copy.sing, copy.howToSingBody),
    OnboardingPage("▶", copy.mixSave, copy.onboardShareBody),
)
