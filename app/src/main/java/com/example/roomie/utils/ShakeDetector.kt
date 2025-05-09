package com.example.roomie.utils // O el paquete que prefieras

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.sqrt

class ShakeDetector(context: Context) : SensorEventListener {

    private var sensorManager: SensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var accelerometer: Sensor? = null

    private var shakeListener: OnShakeListener? = null

    // Umbrales y tiempos para la detección de sacudidas
    // Estos valores pueden necesitar ajuste fino mediante pruebas
    private var lastShakeTime: Long = 0
    private var lastX: Float = 0f
    private var lastY: Float = 0f
    private var lastZ: Float = 0f
    private var shakeCount: Int = 0

    companion object {
        private const val SHAKE_THRESHOLD_GRAVITY = 2.7F // Umbral de fuerza G
        private const val SHAKE_SLOP_TIME_MS = 500 // Tiempo entre sacudidas para contar como una secuencia
        private const val SHAKE_COUNT_RESET_TIME_MS = 3000 // Tiempo para resetear el contador si no hay más sacudidas
        private const val MIN_SHAKES_TO_TRIGGER = 2 // Número mínimo de "sacudidas" en secuencia para activar
    }

    init {
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    fun setOnShakeListener(listener: OnShakeListener?) {
        this.shakeListener = listener
    }

    interface OnShakeListener {
        fun onShake()
    }

    fun start() {
        accelerometer?.also { acc ->
            sensorManager.registerListener(this, acc, SensorManager.SENSOR_DELAY_GAME) // SENSOR_DELAY_UI o SENSOR_DELAY_GAME
            lastShakeTime = System.currentTimeMillis()
            shakeCount = 0
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No es necesario para este caso
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val currentTime = System.currentTimeMillis()

            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val gX = x / SensorManager.GRAVITY_EARTH
            val gY = y / SensorManager.GRAVITY_EARTH
            val gZ = z / SensorManager.GRAVITY_EARTH

            // Fuerza G aproximada
            val gForce = sqrt(gX * gX + gY * gY + gZ * gZ)
            Log.d("ShakeDetector", "gForce: $gForce, x: ${event.values[0]}, y: ${event.values[1]}, z: ${event.values[2]}")
            if (gForce > SHAKE_THRESHOLD_GRAVITY) {
                Log.d("ShakeDetector", "Threshold PASSED! gForce: $gForce")
                if (currentTime - lastShakeTime >= SHAKE_SLOP_TIME_MS) {
                    // Si ha pasado mucho tiempo desde la última sacudida, reiniciar el contador
                    if (currentTime - lastShakeTime > SHAKE_COUNT_RESET_TIME_MS) {
                        shakeCount = 0
                    }
                    shakeCount++
                    lastShakeTime = currentTime

                    if (shakeCount >= MIN_SHAKES_TO_TRIGGER) {
                        shakeListener?.onShake()
                        shakeCount = 0 // Resetear después de activar
                    }
                }
            }

            lastX = x
            lastY = y
            lastZ = z
        }
    }
}