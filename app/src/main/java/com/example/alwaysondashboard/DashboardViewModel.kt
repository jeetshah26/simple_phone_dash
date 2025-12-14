package com.example.alwaysondashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.alwaysondashboard.data.CalendarEvent
import com.example.alwaysondashboard.data.CalendarRepository
import com.example.alwaysondashboard.data.LocationRepository
import com.example.alwaysondashboard.data.TemperatureUnit
import com.example.alwaysondashboard.data.WeatherBundle
import com.example.alwaysondashboard.data.WeatherRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ClockState(
    val timeText: String = "--:-- --",
    val dateText: String = "--"
)

data class WeatherUiState(
    val isLoading: Boolean = true,
    val data: WeatherBundle? = null,
    val errorMessage: String? = null,
    val lastUpdatedMillis: Long? = null
)

data class DashboardUiState(
    val clock: ClockState = ClockState(),
    val calendarEvents: List<CalendarEvent> = emptyList(),
    val hasCalendarPermission: Boolean = false,
    val hasLocationPermission: Boolean = false,
    val weather: WeatherUiState = WeatherUiState(isLoading = false, data = null, errorMessage = "Awaiting location permission"),
    val units: TemperatureUnit = TemperatureUnit.fromLocale()
)

class DashboardViewModel(
    private val calendarRepository: CalendarRepository,
    private val locationRepository: LocationRepository,
    private val weatherRepository: WeatherRepository,
    private val locale: Locale = Locale.getDefault()
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var clockJob: Job? = null
    private var customApiKey: String? = null
    private var weatherAutoJob: Job? = null

    init {
        startClock()
    }

    fun onPermissionsUpdated(hasLocation: Boolean, hasCalendar: Boolean) {
        val needWeatherRefresh = hasLocation && !_uiState.value.hasLocationPermission
        val needCalendarRefresh = hasCalendar && !_uiState.value.hasCalendarPermission

        _uiState.update {
            it.copy(
                hasLocationPermission = hasLocation,
                hasCalendarPermission = hasCalendar,
                weather = if (hasLocation) it.weather.copy(errorMessage = null) else it.weather
            )
        }

        if (needWeatherRefresh) {
            refreshWeather()
            startWeatherAutoRefresh()
        }
        if (needCalendarRefresh) {
            refreshCalendar()
        }
    }

    fun refreshCalendar() {
        if (!_uiState.value.hasCalendarPermission) return
        viewModelScope.launch {
            val result = calendarRepository.todaysEvents()
            result.onSuccess { events ->
                _uiState.update { it.copy(calendarEvents = events) }
            }.onFailure { error ->
                _uiState.update { it.copy(calendarEvents = emptyList()) }
                println("Calendar load error: ${error.localizedMessage}")
            }
        }
    }

    private fun startWeatherAutoRefresh() {
        weatherAutoJob?.cancel()
        if (!_uiState.value.hasLocationPermission) return

        weatherAutoJob = viewModelScope.launch {
            while (isActive) {
                delay(15 * 60 * 1000L) // 15 minutes
                refreshWeather()
            }
        }
    }

    fun refreshWeather(forceFreshLocation: Boolean = false) {
        if (!_uiState.value.hasLocationPermission) {
            _uiState.update {
                it.copy(
                    weather = WeatherUiState(
                        isLoading = false,
                        data = null,
                        errorMessage = "Location permission not granted"
                    )
                )
            }
            return
        }

        _uiState.update { it.copy(weather = WeatherUiState(isLoading = true, data = it.weather.data, lastUpdatedMillis = it.weather.lastUpdatedMillis)) }

        viewModelScope.launch {
            val locationResult = locationRepository.getCurrentLocation(forceFresh = forceFreshLocation)
            locationResult.onFailure { error ->
                _uiState.update {
                    it.copy(
                        weather = WeatherUiState(
                            isLoading = false,
                            data = null,
                            errorMessage = error.localizedMessage ?: "Location unavailable"
                        )
                    )
                }
            }.onSuccess { location ->
                val weatherResult = weatherRepository.loadWeather(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    units = _uiState.value.units,
                    apiKeyOverride = customApiKey
                )
                weatherResult.onSuccess { bundle ->
                    _uiState.update {
                        it.copy(
                            weather = WeatherUiState(
                                isLoading = false,
                                data = bundle,
                                errorMessage = null,
                                lastUpdatedMillis = System.currentTimeMillis()
                            )
                        )
                    }
                }.onFailure { error ->
                    _uiState.update {
                        it.copy(
                            weather = WeatherUiState(
                                isLoading = false,
                                data = null,
                                errorMessage = error.localizedMessage ?: "Weather unavailable",
                                lastUpdatedMillis = it.weather.lastUpdatedMillis
                            )
                        )
                    }
                }
            }
        }
    }

    fun updateApiKey(key: String) {
        customApiKey = key.trim().ifEmpty { null }
        refreshWeather()
        startWeatherAutoRefresh()
    }

    fun toggleUnits() {
        val newUnits = if (_uiState.value.units == TemperatureUnit.METRIC) {
            TemperatureUnit.IMPERIAL
        } else {
            TemperatureUnit.METRIC
        }
        val converted = _uiState.value.weather.data?.let { bundle ->
            convertBundleUnits(bundle, _uiState.value.units, newUnits)
        }
        _uiState.update {
            it.copy(
                units = newUnits,
                weather = it.weather.copy(data = converted ?: it.weather.data)
            )
        }
        refreshWeather()
    }

    private fun convertBundleUnits(
        bundle: WeatherBundle,
        from: TemperatureUnit,
        to: TemperatureUnit
    ): WeatherBundle {
        if (from == to) return bundle
        val tempConverter: (Double) -> Double = { value ->
            if (to == TemperatureUnit.IMPERIAL) (value * 9 / 5) + 32 else (value - 32) * 5 / 9
        }
        val windConverter: (Double?) -> Double? = { value ->
            value?.let {
                if (to == TemperatureUnit.IMPERIAL) it * 2.23694 else it / 2.23694
            }
        }

        fun convertCurrent(c: com.example.alwaysondashboard.data.CurrentWeather) =
            c.copy(
                temperature = tempConverter(c.temperature),
                feelsLike = tempConverter(c.feelsLike),
                windSpeed = windConverter(c.windSpeed)
            )

        fun convertHourly(h: com.example.alwaysondashboard.data.HourlyWeather) =
            h.copy(temperature = tempConverter(h.temperature))

        fun convertDaily(d: com.example.alwaysondashboard.data.DailyWeather) =
            d.copy(
                minTemp = tempConverter(d.minTemp),
                maxTemp = tempConverter(d.maxTemp)
            )

        return bundle.copy(
            current = convertCurrent(bundle.current),
            hourly = bundle.hourly.map { convertHourly(it) },
            tomorrow = bundle.tomorrow?.let { convertDaily(it) }
        )
    }

    private fun startClock() {
        clockJob?.cancel()
        clockJob = viewModelScope.launch {
            val timeFormatter = DateTimeFormatter.ofPattern("hh:mm:ss a", locale)
            val dateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d yyyy", locale)
            while (isActive) {
                val now = LocalDateTime.now()
                _uiState.update {
                    it.copy(
                        clock = ClockState(
                            timeText = now.format(timeFormatter),
                            dateText = now.format(dateFormatter)
                        )
                    )
                }
                val millisUntilNextSecond = 1000 - (System.currentTimeMillis() % 1000)
                delay(millisUntilNextSecond)
            }
        }
    }

    override fun onCleared() {
        clockJob?.cancel()
        weatherAutoJob?.cancel()
        super.onCleared()
    }

    companion object {
        fun Factory(appContext: Context): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val weatherRepo = WeatherRepository.create()
                    val locationRepo = LocationRepository.create(appContext)
                    val calendarRepo = CalendarRepository(appContext)
                    return DashboardViewModel(
                        calendarRepository = calendarRepo,
                        locationRepository = locationRepo,
                        weatherRepository = weatherRepo
                    ) as T
                }
            }
        }
    }
}
