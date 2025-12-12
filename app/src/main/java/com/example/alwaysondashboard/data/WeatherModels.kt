package com.example.alwaysondashboard.data

import java.time.Instant
import java.util.Locale

enum class TemperatureUnit(val apiValue: String, val symbol: String, val speedSymbol: String) {
    METRIC("metric", "°C", "m/s"),
    IMPERIAL("imperial", "°F", "mph");

    companion object {
        fun fromLocale(locale: Locale = Locale.getDefault()): TemperatureUnit {
            val country = locale.country.uppercase(Locale.US)
            return if (country == "US" || country == "BS" || country == "BZ" || country == "KY") {
                IMPERIAL
            } else {
                METRIC
            }
        }
    }
}

data class WeatherBundle(
    val locationLabel: String,
    val current: CurrentWeather,
    val hourly: List<HourlyWeather>,
    val tomorrow: DailyWeather?
)

data class CurrentWeather(
    val temperature: Double,
    val feelsLike: Double,
    val description: String,
    val icon: String?,
    val humidity: Int?,
    val windSpeed: Double?
)

data class HourlyWeather(
    val time: Instant,
    val temperature: Double,
    val icon: String?,
    val precipitationChance: Double?
)

data class DailyWeather(
    val date: Instant,
    val minTemp: Double,
    val maxTemp: Double,
    val description: String,
    val icon: String?
)
