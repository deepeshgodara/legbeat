package com.legbeat.wear.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText

@Composable
fun WearCadenceScreen(viewModel: WearCadenceViewModel) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        timeText = {
            if (!state.isAmbient) {
                TimeText()
            }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                // Zone Tag
                if (!state.isAmbient) {
                    val zoneLabel = if (state.isStale || state.rpm == null) "WAITING" else state.zone.label.uppercase()
                    val zoneColor = if (state.isStale || state.rpm == null) Color.Gray else Color(state.zone.colorHex)

                    Box(
                        modifier = Modifier
                            .background(zoneColor.copy(alpha = 0.25f), shape = RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = zoneLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = zoneColor,
                            letterSpacing = 1.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // High-Contrast Maximum-Legibility Cadence Display (74sp)
                // Displays "--" if no cadence signal received for more than 5 seconds
                val displayValue = if (state.isStale || state.rpm == null) "--" else state.rpm.toString()
                val fontColor = if (state.isAmbient) {
                    Color.White
                } else if (state.isStale || state.rpm == null) {
                    Color(0xFF888888)
                } else {
                    Color(0xFFFFD700) // Electric Yellow high-contrast
                }

                Text(
                    text = displayValue,
                    fontSize = if (state.isAmbient) 64.sp else 74.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = fontColor,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "RPM",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (state.isAmbient) Color.Gray else Color.LightGray,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}
