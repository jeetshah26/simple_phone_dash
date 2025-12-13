package com.example.alwaysondashboard.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.alwaysondashboard.ClockState
import com.example.alwaysondashboard.DashboardViewModel
import com.example.alwaysondashboard.data.CalendarEvent
import com.example.alwaysondashboard.data.HourlyWeather
import com.example.alwaysondashboard.data.TemperatureUnit
import com.example.alwaysondashboard.data.WeatherBundle
import com.example.alwaysondashboard.WeatherUiState
import com.example.alwaysondashboard.ui.theme.AlwaysOnDashboardTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private data class DashboardMetrics(
    val cardPadding: androidx.compose.ui.unit.Dp,
    val sectionSpacing: androidx.compose.ui.unit.Dp,
    val clockSize: androidx.compose.ui.unit.TextUnit,
    val ampmSize: androidx.compose.ui.unit.TextUnit,
    val dateSize: androidx.compose.ui.unit.TextUnit,
    val headingSize: androidx.compose.ui.unit.TextUnit,
    val tempSize: androidx.compose.ui.unit.TextUnit,
    val chipWidth: androidx.compose.ui.unit.Dp,
    val chipHeight: androidx.compose.ui.unit.Dp,
    val hourlySpacing: androidx.compose.ui.unit.Dp
)

private val CompactMetrics = DashboardMetrics(
    cardPadding = 12.dp,
    sectionSpacing = 8.dp,
    clockSize = 48.sp,
    ampmSize = 18.sp,
    dateSize = 16.sp,
    headingSize = 16.sp,
    tempSize = 44.sp,
    chipWidth = 64.dp,
    chipHeight = 72.dp,
    hourlySpacing = 0.dp
)

private val DefaultMetrics = DashboardMetrics(
    cardPadding = 16.dp,
    sectionSpacing = 12.dp,
    clockSize = 64.sp,
    ampmSize = 20.sp,
    dateSize = 18.sp,
    headingSize = 18.sp,
    tempSize = 52.sp,
    chipWidth = 72.dp,
    chipHeight = 80.dp,
    hourlySpacing = 0.dp
)

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val metrics = if (config.smallestScreenWidthDp < 600) CompactMetrics else DefaultMetrics
    val isNarrowScreen = config.screenWidthDp < 700
    val leftWeight = if (isNarrowScreen) 0.45f else 0.4f
    val rightWeight = 1f - leftWeight
    val systemDark = isSystemInDarkTheme()
    var useDarkTheme by rememberSaveable { mutableStateOf(systemDark) }

    var showApiDialog by rememberSaveable { mutableStateOf(false) }
    var apiInput by rememberSaveable { mutableStateOf("") }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { result ->
            val hasLocation = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            viewModel.onPermissionsUpdated(
                hasLocation = hasLocation,
                hasCalendar = uiState.hasCalendarPermission
            )
        }
    )
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            viewModel.onPermissionsUpdated(
                hasLocation = uiState.hasLocationPermission,
                hasCalendar = granted
            )
        }
    )

    LaunchedEffect(Unit) {
        val hasLocation = hasLocationPermission(context)
        val hasCalendar = hasCalendarPermission(context)
        viewModel.onPermissionsUpdated(hasLocation, hasCalendar)
        if (hasLocation) {
            viewModel.refreshWeather()
        }
        if (hasCalendar) {
            viewModel.refreshCalendar()
        }
    }

    AlwaysOnDashboardTheme(useDarkTheme = useDarkTheme) {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val leftModifier = Modifier
                        .weight(leftWeight)
                        .fillMaxHeight()
                    val rightModifier = Modifier
                        .weight(rightWeight)
                        .fillMaxHeight()

                    Column(
                        modifier = leftModifier
                    ) {
                        ClockPanel(
                            clock = uiState.clock,
                            metrics = metrics,
                            isDarkTheme = useDarkTheme,
                            onToggleTheme = { useDarkTheme = !useDarkTheme },
                            onSettingsClick = { showApiDialog = true }
                        )
                        Spacer(modifier = Modifier.height(metrics.sectionSpacing))
                        CalendarPanel(
                            events = uiState.calendarEvents,
                            hasPermission = uiState.hasCalendarPermission,
                            onRequestPermission = {
                                calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                            },
                            metrics = metrics,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Column(
                        modifier = rightModifier
                    ) {
                        WeatherPanel(
                            state = uiState.weather,
                            units = uiState.units,
                            onRefresh = { viewModel.refreshWeather(forceFreshLocation = true) },
                            onToggleUnits = { viewModel.toggleUnits() },
                            onRequestPermission = {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            },
                            hasLocationPermission = uiState.hasLocationPermission,
                            metrics = metrics,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }

    if (showApiDialog) {
        AlertDialog(
            onDismissRequest = { showApiDialog = false },
            title = { Text("Set OpenWeather API key") },
            text = {
                TextField(
                    value = apiInput,
                    onValueChange = { apiInput = it },
                    placeholder = { Text("Enter API key") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateApiKey(apiInput)
                    showApiDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ClockPanel(
    clock: ClockState,
    metrics: DashboardMetrics,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(metrics.cardPadding)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(metrics.sectionSpacing / 1.5f)
            ) {
                val parts = clock.timeText.trim().split(" ")
                val mainTime = if (parts.size >= 2) parts.dropLast(1).joinToString(" ") else clock.timeText
                val ampm = if (parts.size >= 2) parts.last() else ""
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (ampm.isNotEmpty()) {
                        Text(
                            text = mainTime,
                            modifier = Modifier.alignByBaseline(),
                            style = MaterialTheme.typography.displayLarge.copy(fontSize = metrics.clockSize),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = ampm,
                            modifier = Modifier.alignByBaseline(),
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = metrics.ampmSize),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = mainTime,
                            style = MaterialTheme.typography.displayLarge.copy(fontSize = metrics.clockSize),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = clock.dateText,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = metrics.dateSize),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier.align(Alignment.BottomEnd),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = onToggleTheme,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "Toggle theme",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarPanel(
    events: List<CalendarEvent>,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    metrics: DashboardMetrics,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(metrics.cardPadding)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(metrics.sectionSpacing)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Today's Events",
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = metrics.headingSize),
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = Icons.Default.Event,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            if (!hasPermission) {
                PermissionPrompt(
                    message = "Allow calendar access to show today's events.",
                    onRequestPermission = onRequestPermission,
                    icon = Icons.Default.Event
                )
            } else if (events.isEmpty()) {
                Text(
                    text = "No events scheduled.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(metrics.sectionSpacing),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(events) { event ->
                        CalendarEventRow(event, metrics)
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarEventRow(event: CalendarEvent, metrics: DashboardMetrics) {
    val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")
    val start = Instant.ofEpochMilli(event.startMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
    val end = Instant.ofEpochMilli(event.endMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = metrics.headingSize),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (event.allDay) "All day" else "${start.format(timeFormatter)} - ${end.format(timeFormatter)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WeatherPanel(
    state: WeatherUiState,
    units: TemperatureUnit,
    onRefresh: () -> Unit,
    onToggleUnits: () -> Unit,
    onRequestPermission: () -> Unit,
    hasLocationPermission: Boolean,
    metrics: DashboardMetrics,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(metrics.cardPadding),
            verticalArrangement = Arrangement.spacedBy(metrics.sectionSpacing + 2.dp)
        ) {
            when {
                state.data != null -> WeatherContent(
                    bundle = state.data,
                    units = units,
                    metrics = metrics,
                    onRefresh = onRefresh,
                    onToggleUnits = onToggleUnits,
                    isLoading = state.isLoading
                )

                state.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                !hasLocationPermission -> PermissionPrompt(
                    message = "Allow location access to show weather.",
                    onRequestPermission = onRequestPermission,
                    icon = Icons.Default.LocationOn
                )

                state.errorMessage != null -> ErrorPrompt(
                    message = state.errorMessage,
                    onRetry = onRefresh
                )
            }
        }
    }
}

@Composable
private fun WeatherContent(
    bundle: WeatherBundle,
    units: TemperatureUnit,
    metrics: DashboardMetrics,
    onRefresh: () -> Unit,
    onToggleUnits: () -> Unit,
    isLoading: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(metrics.sectionSpacing * 0.4f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = bundle.locationLabel,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = metrics.headingSize),
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = bundle.current.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(
                modifier = Modifier.padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                IconButton(onClick = onToggleUnits, modifier = Modifier.size(32.dp)) {
                    Icon(imageVector = Icons.Default.SwapHoriz, contentDescription = "Swap units")
                }
                IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh")
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "${bundle.current.temperature.toInt()}${units.symbol}",
                    style = MaterialTheme.typography.displayMedium.copy(fontSize = metrics.tempSize),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Feels like ${bundle.current.feelsLike.toInt()}${units.symbol}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (bundle.hourly.isNotEmpty()) {
            Text(
                text = "Next hours",
                style = MaterialTheme.typography.titleSmall.copy(fontSize = metrics.headingSize),
                fontWeight = FontWeight.SemiBold
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(metrics.hourlySpacing)
            ) {
                items(bundle.hourly) { hour ->
                    HourlyChip(hour, units, metrics)
                }
            }
        }

        bundle.tomorrow?.let { tomorrow ->
            Card {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(metrics.cardPadding * 0.5f),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "Tomorrow",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleSmall.copy(fontSize = metrics.headingSize)
                        )
                        Text(
                            text = tomorrow.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "${tomorrow.maxTemp.toInt()} / ${tomorrow.minTemp.toInt()}${units.symbol}",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = metrics.headingSize),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        RunningConditions(bundle = bundle, units = units, metrics = metrics, isLoading = isLoading)
    }
}

@Composable
private fun HourlyChip(hour: HourlyWeather, units: TemperatureUnit, metrics: DashboardMetrics) {
    val time = hour.time.atZone(ZoneId.systemDefault()).toLocalTime()
    val formatter = DateTimeFormatter.ofPattern("ha")
    Card {
        Column(
            modifier = Modifier
                .padding(6.dp)
                .size(width = metrics.chipWidth, height = metrics.chipHeight),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = time.format(formatter))
            Text(
                text = "${hour.temperature.toInt()}${units.symbol}",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = metrics.headingSize),
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Precip ${((hour.precipitationChance ?: 0.0) * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PermissionPrompt(
    message: String,
    onRequestPermission: () -> Unit,
    icon: ImageVector
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = onRequestPermission) {
            Icon(
                imageVector = icon,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Grant permission")
        }
    }
}

@Composable
private fun ErrorPrompt(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = onRetry) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Retry")
        }
    }
}

@Composable
private fun RunningConditions(
    bundle: WeatherBundle,
    units: TemperatureUnit,
    metrics: DashboardMetrics,
    isLoading: Boolean
) {
    val status = remember(bundle, units) { runningStatus(bundle, units) }
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(metrics.cardPadding * 0.5f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Running conditions",
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = metrics.headingSize),
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = status.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    imageVector = status.icon,
                    contentDescription = status.label,
                    tint = status.color,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

private data class RunningStatus(val label: String, val color: Color, val icon: ImageVector)

private fun runningStatus(bundle: WeatherBundle, units: TemperatureUnit): RunningStatus {
    val feelsLike = bundle.current.feelsLike
    val humidity = bundle.current.humidity ?: 0
    val tempF = if (units == TemperatureUnit.IMPERIAL) feelsLike else (feelsLike * 9 / 5) + 32

    return when {
        tempF >= 88 || tempF <= 20 || humidity >= 85 -> RunningStatus(
            label = "Extreme conditions: indoor suggested",
            color = Color(0xFFB91C1C),
            icon = Icons.Default.PanTool
        )
        tempF in 75.0..87.9 || humidity in 70..84 -> RunningStatus(
            label = "Caution: hydrate, go easy",
            color = Color(0xFFF59E0B),
            icon = Icons.Default.DirectionsRun
        )
        else -> RunningStatus(
            label = "No restrictions",
            color = Color(0xFF16A34A),
            icon = Icons.Default.DirectionsRun
        )
    }
}

private fun hasLocationPermission(context: android.content.Context): Boolean {
    val coarse = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val fine = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    return coarse || fine
}

private fun hasCalendarPermission(context: android.content.Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_CALENDAR
    ) == PackageManager.PERMISSION_GRANTED
}
