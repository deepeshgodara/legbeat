package com.legbeat.presentation

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.legbeat.analytics.repository.RideRepository
import com.legbeat.core.model.Ride
import com.legbeat.presentation.theme.ElectricMint
import com.legbeat.presentation.theme.ElectricYellow
import com.legbeat.service.CadenceTrackingService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    rideRepository: RideRepository,
    onStartRide: () -> Unit,
    onRideSelected: (String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val rides by rideRepository.getAllRides().collectAsState(initial = emptyList())
    val isTracking by CadenceTrackingService.isTracking.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.DirectionsBike,
                            contentDescription = null,
                            tint = ElectricYellow,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("LegBeat", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
        ) {
            // Hero Start/Resume Tracking Button
            Button(
                onClick = {
                    if (!isTracking) {
                        val startIntent = Intent(context, CadenceTrackingService::class.java).apply {
                            action = CadenceTrackingService.ACTION_START
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(startIntent)
                        } else {
                            context.startService(startIntent)
                        }
                    }
                    onStartRide()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricYellow,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = if (isTracking) "RESUME ACTIVE RIDE" else "START TRACKING RIDE",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Past Rides",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (rides.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.DirectionsBike,
                            contentDescription = null,
                            tint = Color.DarkGray,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No rides recorded yet",
                            color = Color.Gray,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Put your phone in your pocket and tap Start to record your cadence",
                            color = Color.DarkGray,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(rides, key = { it.id }) { ride ->
                        RideHistoryCard(ride = ride, onClick = { onRideSelected(ride.id) })
                    }
                }
            }
        }
    }
}

@Composable
fun RideHistoryCard(
    ride: Ride,
    onClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
    val dateString = dateFormat.format(Date(ride.startTimeMs))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(dateString, style = MaterialTheme.typography.bodySmall, color = Color.LightGray)

                // Health Connect Status Badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (ride.healthConnectSynced) Icons.Default.CheckCircle else Icons.Default.Sync,
                        contentDescription = null,
                        tint = if (ride.healthConnectSynced) ElectricMint else Color.Gray,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (ride.healthConnectSynced) "Health Synced" else "Local Only",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (ride.healthConnectSynced) ElectricMint else Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("AVG CADENCE", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text(
                        "${ride.avgCadence} RPM",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = ElectricYellow
                    )
                }

                Column {
                    Text("MAX CADENCE", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text(
                        "${ride.maxCadence} RPM",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White
                    )
                }

                Column {
                    Text("DURATION", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Text(
                        formatDurationMin(ride.durationMs),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White
                    )
                }
            }
        }
    }
}

private fun formatDurationMin(durationMs: Long): String {
    val totalSec = durationMs / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "${min}m ${sec}s"
}
