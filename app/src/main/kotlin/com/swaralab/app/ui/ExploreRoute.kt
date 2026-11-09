package com.swaralab.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swaralab.domain.model.SessionKind
import com.swaralab.domain.usecase.StartSession
import com.swaralab.ui.BoothCanvas
import com.swaralab.ui.CompactChoiceCard
import com.swaralab.ui.LocalAppCopy
import com.swaralab.ui.PageHero
import com.swaralab.ui.SwaraColors

@Composable
fun ExploreRoute(
    startSession: StartSession,
    onOpen: (String, String) -> Unit,
    onImport: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val viewModel: StartViewModel = viewModel(factory = StartViewModel.factory(startSession))
    val copy = LocalAppCopy.current
    LaunchedEffect(viewModel) {
        viewModel.opened.collect { opened -> onOpen(opened.id, opened.sing) }
    }

    BoothCanvas(intensity = 0.55f, modifier = modifier) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PageHero(
            title = copy.tabNew,
            body = copy.startLine,
            accent = SwaraColors.Teal,
        )
        CompactChoiceCard(
            title = copy.openStage,
            body = copy.tapToSing,
            onClick = { viewModel.start(SessionKind.SONG) },
            accent = SwaraColors.Teal,
            accentWash = SwaraColors.TealWash,
        )
        CompactChoiceCard(
            title = copy.singFromPhone,
            body = copy.singFromPhoneBody,
            onClick = onImport,
            accent = SwaraColors.Gold,
            accentWash = SwaraColors.GoldWash,
        )
        CompactChoiceCard(
            title = copy.warmupTitle,
            body = copy.warmupBody,
            onClick = { viewModel.start(SessionKind.PRACTICE) },
            accent = SwaraColors.Gold,
            accentWash = SwaraColors.GoldWash,
        )
    }
    }
}
