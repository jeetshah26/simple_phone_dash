package com.example.alwaysondashboard.data

import com.example.alwaysondashboard.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

class WeatherRepository(
    private val service: OpenWeatherService,
    private val locale: Locale = Locale.getDefault()
    ) {

    suspend fun loadWeather(
        latitude: Double,
        longitude: Double,
        units: TemperatureUnit,
        apiKeyOverride: String? = null
    ): Result<WeatherBundle> {
        val apiKey = apiKeyOverride?.takeIf { it.isNotBlank() } ?: BuildConfig.OPEN_WEATHER_API_KEY
        if (apiKey.isEmpty()) {
            return Result.failure(IllegalStateException("Missing OPEN_WEATHER_API_KEY in local.properties"))
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                val current = service.currentWeather(
                    lat = latitude,
                    lon = longitude,
                    units = units.apiValue,
                    apiKey = apiKey
                )
                val forecast = service.forecast(
                    lat = latitude,
                    lon = longitude,
                    units = units.apiValue,
                    apiKey = apiKey
                )
                toDomain(current, forecast, locale)
            }
        }
    }

    companion object {
        fun create(): WeatherRepository {
            val logging = HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BASIC
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .build()

            val moshi = Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()
            val retrofit = Retrofit.Builder()
                .baseUrl("https://api.openweathermap.org/")
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()

            return WeatherRepository(retrofit.create(OpenWeatherService::class.java))
        }
    }
}

private fun toDomain(
    current: CurrentWeatherResponse,
    forecast: ForecastResponse,
    locale: Locale
): WeatherBundle {
    val description = current.weather.firstOrNull()?.description
    val offset = ZoneOffset.ofTotalSeconds(forecast.city.timezone ?: 0)
    val zoneId: ZoneId = offset

    val currentWeather = CurrentWeather(
        temperature = current.main.temp,
        feelsLike = current.main.feelsLike,
        description = description?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
            ?: "Unknown",
        icon = current.weather.firstOrNull()?.icon,
        humidity = current.main.humidity,
        windSpeed = current.wind?.speed
    )
    val sunrise = current.sys?.sunrise?.let { Instant.ofEpochSecond(it) }
    val sunset = current.sys?.sunset?.let { Instant.ofEpochSecond(it) }

    val hourlyDomain = forecast.list.take(12).map {
        HourlyWeather(
            time = Instant.ofEpochSecond(it.dt),
            temperature = it.main.temp,
            icon = it.weather.firstOrNull()?.icon,
            precipitationChance = it.pop
        )
    }

    val tomorrowDate = LocalDate.now(zoneId).plusDays(1)
    val tomorrowItems = forecast.list.filter {
        Instant.ofEpochSecond(it.dt).atZone(zoneId).toLocalDate() == tomorrowDate
    }
    val tomorrow = if (tomorrowItems.isNotEmpty()) {
        val minCandidates = tomorrowItems.mapNotNull { it.main.tempMin }
        val maxCandidates = tomorrowItems.mapNotNull { it.main.tempMax }
        val minTemp = minCandidates.minOrNull() ?: tomorrowItems.minOf { it.main.temp }
        val maxTemp = maxCandidates.maxOrNull() ?: tomorrowItems.maxOf { it.main.temp }
        val desc = tomorrowItems.firstOrNull()?.weather?.firstOrNull()?.description
        DailyWeather(
            date = tomorrowDate.atStartOfDay(zoneId).toInstant(),
            minTemp = minTemp,
            maxTemp = maxTemp,
            description = desc?.replaceFirstChar { ch -> if (ch.isLowerCase()) ch.titlecase(locale) else ch.toString() }
                ?: "N/A",
            icon = tomorrowItems.firstOrNull()?.weather?.firstOrNull()?.icon
        )
    } else null

    return WeatherBundle(
        locationLabel = forecast.city.name ?: "Current location",
        current = currentWeather,
        hourly = hourlyDomain,
        tomorrow = tomorrow,
        sunrise = sunrise,
        sunset = sunset
    )
}

interface OpenWeatherService {
    @GET("data/2.5/weather")
    suspend fun currentWeather(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("units") units: String,
        @Query("appid") apiKey: String
    ): CurrentWeatherResponse

    @GET("data/2.5/forecast")
    suspend fun forecast(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("units") units: String,
        @Query("appid") apiKey: String
    ): ForecastResponse
}

data class WeatherIcon(
    val id: Int,
    val main: String?,
    val description: String?,
    val icon: String?
)

data class WeatherMain(
    val temp: Double,
    @Json(name = "feels_like") val feelsLike: Double,
    @Json(name = "temp_min") val tempMin: Double? = null,
    @Json(name = "temp_max") val tempMax: Double? = null,
    val humidity: Int?
)

data class Wind(val speed: Double?)

data class CurrentWeatherResponse(
    val weather: List<WeatherIcon>,
    val main: WeatherMain,
    val wind: Wind?,
    val sys: CurrentSys?
)

data class ForecastResponse(
    val city: ForecastCity,
    val list: List<ForecastItem>
)

data class ForecastCity(
    val name: String?,
    val timezone: Int?
)

data class ForecastItem(
    val dt: Long,
    val main: WeatherMain,
    val weather: List<WeatherIcon>,
    val pop: Double?
)

data class CurrentSys(
    val sunrise: Long?,
    val sunset: Long?
)
