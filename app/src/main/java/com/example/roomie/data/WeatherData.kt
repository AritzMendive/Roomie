package com.example.roomie.data // O donde quieras poner tus modelos de datos

import android.annotation.SuppressLint
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class WeatherResponse(
    val coord: Coord? = null,
    val weather: List<Weather>? = null,
    val base: String? = null,
    val main: Main? = null,
    val visibility: Int? = null,
    val wind: Wind? = null,
    val clouds: Clouds? = null,
    val dt: Long? = null,
    val sys: Sys? = null,
    val timezone: Int? = null,
    val id: Int? = null,
    val name: String? = null, // Nombre de la ciudad encontrada
    val cod: Int? = null
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class Weather(
    val id: Int? = null,
    val main: String? = null, // Ej: "Clouds"
    val description: String? = null, // Ej: "nubes dispersas"
    val icon: String? = null // Ej: "03d" <-- Código del icono
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class Main(
    val temp: Double? = null,
    @SerialName("feels_like")
    val feelsLike: Double? = null,
    @SerialName("temp_min")
    val tempMin: Double? = null,
    @SerialName("temp_max")
    val tempMax: Double? = null,
    val pressure: Int? = null,
    val humidity: Int? = null,
    // Pueden existir más campos como sea_level, grnd_level
)

// Define Coord, Wind, Clouds, Sys si necesitas esos datos
@SuppressLint("UnsafeOptInUsageError")
@Serializable data class Coord(val lon: Double? = null, val lat: Double? = null)
@SuppressLint("UnsafeOptInUsageError")
@Serializable data class Wind(val speed: Double? = null, val deg: Int? = null)
@SuppressLint("UnsafeOptInUsageError")
@Serializable data class Clouds(val all: Int? = null)
@SuppressLint("UnsafeOptInUsageError")
@Serializable data class Sys(
    val type: Int? = null,
    val id: Int? = null,
    val country: String? = null,
    val sunrise: Long? = null,
    val sunset: Long? = null
)