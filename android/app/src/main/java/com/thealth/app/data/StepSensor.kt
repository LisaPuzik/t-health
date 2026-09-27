package com.thealth.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.coroutines.resume

// Шаги напрямую с сенсора телефона (TYPE_STEP_COUNTER).
// Никаких облаков: одно штатное разрешение ACTIVITY_RECOGNITION,
// диалог которого система показывает всегда.
const val STEP_SOURCE = "StepSensor"
const val STEP_GOAL = 10_000

fun hasStepSensor(ctx: Context): Boolean {
    val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return false
    return sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
}

fun hasActivityPermission(ctx: Context): Boolean {
    if (Build.VERSION.SDK_INT < 29) return true
    return ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACTIVITY_RECOGNITION) ==
        PackageManager.PERMISSION_GRANTED
}

// Счётчик шагов с момента загрузки телефона. null — нет сенсора/таймаут.
suspend fun readStepsSinceBoot(ctx: Context): Float? {
    val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return null
    val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return null
    return withTimeoutOrNull(5000) {
        suspendCancellableCoroutine { cont ->
            val listener = object : SensorEventListener {
                override fun onSensorChanged(e: SensorEvent) {
                    sm.unregisterListener(this)
                    if (cont.isActive) cont.resume(e.values.firstOrNull())
                }

                override fun onAccuracyChanged(s: Sensor?, acc: Int) {}
            }
            if (!sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)) {
                if (cont.isActive) cont.resume(null)
            }
            cont.invokeOnCancellation { sm.unregisterListener(listener) }
        }
    }
}

// Правда устройства: шагов сегодня = дельта счётчика от утренней базы.
// Утром база сдвигается сама; после перезагрузки (счётчик упал) — тоже.
suspend fun readTodaySteps(ctx: Context, store: AuthStore): Int? {
    if (!hasActivityPermission(ctx)) return null
    val current = readStepsSinceBoot(ctx) ?: return null
    val today = LocalDate.now().toString()
    val (day, base) = store.stepBaseline()
    if (day != today || base == null || current < base) {
        store.setStepBaseline(today, current)
        return 0
    }
    return (current - base).toInt()
}

sealed interface StepUpload {
    data class Uploaded(val steps: Int, val inserted: Int) : StepUpload
    data object AlreadyDone : StepUpload
    data object NoPermission : StepUpload
    data object NoSensor : StepUpload
    data class Failed(val msg: String) : StepUpload
}

// Один часовой бакет в бэк: идемпотентно (ExternalId sensor-YYYYMMDDHH),
// дневные суммы на бэке (кольцо/стрик/челленджи) сходятся из бакетов.
suspend fun uploadHourBucket(ctx: Context, store: AuthStore, api: ApiService): StepUpload {
    if (!hasActivityPermission(ctx)) return StepUpload.NoPermission
    val current = readStepsSinceBoot(ctx) ?: return StepUpload.NoSensor
    val now = LocalDateTime.now()
    val hour = now.format(DateTimeFormatter.ofPattern("yyyyMMddHH"))
    val todayKey = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
    val (upHour, upVal) = store.stepUpload()
    if (upHour == hour) return StepUpload.AlreadyDone
    // Новый день: вчерашнее значение не тянем, просто помечаем час
    if (upHour == null || !upHour.startsWith(todayKey)) {
        store.setStepUpload(hour, current)
        return StepUpload.AlreadyDone
    }
    val delta = (current - (upVal ?: current)).coerceAtLeast(0f).toInt()
    if (delta <= 0) {
        store.setStepUpload(hour, current)
        return StepUpload.AlreadyDone
    }
    return try {
        val iso = now.atZone(ZoneId.systemDefault()).toOffsetDateTime().toString()
        val r = api.batch(
            BatchRequest(
                listOf(
                    HealthMetricItem("Steps", delta.toDouble(), "count", iso, STEP_SOURCE, "sensor-$hour")
                )
            )
        )
        store.setStepUpload(hour, current)
        store.setLastSyncMs()
        StepUpload.Uploaded(delta, r.inserted)
    } catch (e: Exception) {
        StepUpload.Failed(e.message ?: "сеть")
    }
}
