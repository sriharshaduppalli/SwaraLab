package com.swaralab.app.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.swaralab.ui.AppCopy
import com.swaralab.ui.BoothCanvas
import com.swaralab.ui.LocalAppCopy
import com.swaralab.ui.SwaraColors
import com.swaralab.ui.boothHaptic
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val title: String,
    val body: String,
    val kind: Int,
)

@Composable
fun OnboardingRoute(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val copy = LocalAppCopy.current
    val pages = remember(copy) { onboardingPages(copy) }
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val page = pagerState.currentPage
    val view = LocalView.current

    BoothCanvas(intensity = 0.8f, modifier = modifier) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            ) { index ->
                val shown = pages[index]
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(140.dp)
                            .padding(0.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            shape = CircleShape,
                            color = SwaraColors.Teal.copy(alpha = 0.15f),
                        ) {}
                        OnboardMark(kind = shown.kind)
                    }
                    Spacer(Modifier.height(48.dp))
                    Text(
                        shown.title,
                        style = MaterialTheme.typography.headlineLarge,
                        textAlign = TextAlign.Center,
                        color = SwaraColors.Ink,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        shown.body,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = SwaraColors.Muted,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                pages.indices.forEach { index ->
                    val wide by animateDpAsState(
                        targetValue = if (index == page) 22.dp else 8.dp,
                        animationSpec = tween(280, easing = FastOutSlowInEasing),
                        label = "dot-$index",
                    )
                    Box(
                        modifier = Modifier
                            .height(24.dp)
                            .width(wide.coerceAtLeast(24.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            modifier = Modifier
                                .height(8.dp)
                                .width(wide)
                                .animateContentSize(),
                            shape = CircleShape,
                            color = if (index == page) SwaraColors.Teal else SwaraColors.Line,
                        ) {}
                    }
                }
            }
            Spacer(Modifier.height(40.dp))
            Surface(
                onClick = {
                    view.boothHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                    if (page < pages.lastIndex) {
                        scope.launch { pagerState.animateScrollToPage(page + 1) }
                    } else {
                        onFinished()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                color = SwaraColors.Teal,
                shadowElevation = 6.dp,
            ) {
                Text(
                    text = if (page == pages.lastIndex) copy.onboardStart else copy.onboardNext,
                    modifier = Modifier.padding(vertical = 16.dp),
                    textAlign = TextAlign.Center,
                    color = SwaraColors.Ink,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun OnboardMark(kind: Int) {
    val float = rememberInfiniteTransition(label = "onboard-mark")
    val lift by float.animateFloat(
        initialValue = 0f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(2_200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "mark-lift",
    )
    val teal = SwaraColors.Teal
    val gold = SwaraColors.Gold
    val rose = SwaraColors.Rose
    Canvas(Modifier.size(120.dp).padding(bottom = lift.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        drawCircle(teal.copy(alpha = 0.16f), radius = size.minDimension * 0.46f)
        drawCircle(
            color = gold.copy(alpha = 0.22f),
            radius = size.minDimension * 0.28f,
            center = Offset(cx + 18.dp.toPx(), cy - 10.dp.toPx()),
        )
        when (kind) {
            0 -> {
                drawCircle(
                    color = teal,
                    radius = 10.dp.toPx(),
                    center = Offset(cx - 28.dp.toPx(), cy + 8.dp.toPx()),
                )
                drawLine(
                    color = teal,
                    start = Offset(cx - 20.dp.toPx(), cy + 8.dp.toPx()),
                    end = Offset(cx + 36.dp.toPx(), cy - 28.dp.toPx()),
                    strokeWidth = 6.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawCircle(
                    color = gold,
                    radius = 8.dp.toPx(),
                    center = Offset(cx + 36.dp.toPx(), cy - 28.dp.toPx()),
                )
            }
            1 -> {
                drawCircle(color = teal.copy(alpha = 0.9f), radius = 16.dp.toPx())
                drawCircle(
                    color = rose.copy(alpha = 0.55f),
                    radius = 28.dp.toPx(),
                    style = Stroke(width = 4.dp.toPx()),
                )
            }
            else -> {
                val left = Offset(cx - 18.dp.toPx(), cy - 22.dp.toPx())
                drawPath(
                    path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(left.x, left.y)
                        lineTo(left.x + 48.dp.toPx(), cy)
                        lineTo(left.x, cy + 22.dp.toPx())
                        close()
                    },
                    color = teal,
                )
            }
        }
    }
}

private fun onboardingPages(copy: AppCopy): List<OnboardingPage> = listOf(
    OnboardingPage(copy.singFromPhone, copy.singFromPhoneBody, 0),
    OnboardingPage(copy.sing, copy.howToSingBody, 1),
    OnboardingPage(copy.mixSave, copy.onboardShareBody, 2),
)
