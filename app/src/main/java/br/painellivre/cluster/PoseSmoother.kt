package br.painellivre.cluster

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Posição do carro a cada quadro (ideia do SealNav, docs/SEALNAV_RE.md achado F). O GPS da central
 * entrega ~1 fix por segundo; entre um e outro o carro segue em frente na velocidade medida
 * (no máximo [maxCoastMs]), e cada fix novo é absorvido aos poucos em [blendMs] em vez de dar
 * um pulo. Saltos grandes (> [teleportMeters], GPS voltando de um túnel) vão direto; parado,
 * variações menores que [stillJitterMeters] são ignoradas.
 *
 * Tempo em milissegundos de um relógio monotônico (SystemClock.uptimeMillis no app).
 * Kotlin puro (sem Android) para ser testado na JVM.
 */
class PoseSmoother(
    private val maxCoastMs: Long = 1_500,
    private val blendMs: Long = 600,
    private val teleportMeters: Double = 150.0,
    private val stillJitterMeters: Double = 8.0,
) {
    data class Pose(val lat: Double, val lon: Double, val bearing: Double, val speed: Double)

    private data class Fix(val lat: Double, val lon: Double, val bearing: Double, val speed: Double, val at: Long)

    private var fix: Fix? = null
    private var offLat = 0.0
    private var offLon = 0.0
    private var offBearing = 0.0
    private var offAt = Long.MIN_VALUE / 4

    val hasFix: Boolean get() = fix != null
    val lastFixAt: Long? get() = fix?.at

    fun onFix(lat: Double, lon: Double, bearing: Double, speed: Double, now: Long) {
        val prev = fix
        if (prev != null && speed < MIN_COAST_SPEED && prev.speed < MIN_COAST_SPEED &&
            distanceMeters(prev.lat, prev.lon, lat, lon) < stillJitterMeters
        ) {
            // Parado: o GPS "passeia" alguns metros; não vale mexer o carro (nem gastar quadros).
            fix = prev.copy(at = now)
            return
        }
        val shown = poseAt(now)
        val b = normalize(bearing)
        fix = Fix(lat, lon, b, max(0.0, speed), now)
        if (shown != null && distanceMeters(shown.lat, shown.lon, lat, lon) < teleportMeters) {
            offLat = shown.lat - lat
            offLon = shown.lon - lon
            offBearing = angleDiff(b, shown.bearing)
            offAt = now
        } else {
            offLat = 0.0; offLon = 0.0; offBearing = 0.0
            offAt = Long.MIN_VALUE / 4
        }
    }

    fun poseAt(now: Long): Pose? {
        val f = fix ?: return null
        val dt = (now - f.at).coerceIn(0, maxCoastMs) / 1000.0
        val dist = if (f.speed >= MIN_COAST_SPEED) f.speed * dt else 0.0
        val (pLat, pLon) = project(f.lat, f.lon, f.bearing, dist)
        val k = blendWeight(now)
        return Pose(pLat + offLat * k, pLon + offLon * k, normalize(f.bearing + offBearing * k), f.speed)
    }

    /** Ainda muda de um quadro para o outro? Falso com o carro parado e a correção já absorvida. */
    fun isAnimating(now: Long): Boolean {
        val f = fix ?: return false
        return (f.speed >= MIN_COAST_SPEED && now - f.at < maxCoastMs) || blendWeight(now) > 0.0
    }

    private fun blendWeight(now: Long): Double {
        val t = (now - offAt).toDouble() / blendMs
        if (t >= 1.0) return 0.0
        if (t <= 0.0) return 1.0
        return 1.0 - t * t * (3 - 2 * t) // smoothstep: começa e termina devagar
    }

    companion object {
        /** Abaixo disso (m/s) o carro é tratado como parado: não anda sozinho entre fixes. */
        const val MIN_COAST_SPEED = 0.8
        private const val EARTH_R = 6_371_000.0

        fun normalize(deg: Double): Double {
            val d = deg % 360.0
            return if (d < 0) d + 360.0 else d
        }

        /** Menor ângulo (graus, com sinal) para ir de [from] até [to], em (-180, 180]. */
        fun angleDiff(from: Double, to: Double): Double {
            var d = (to - from) % 360.0
            if (d > 180.0) d -= 360.0
            if (d <= -180.0) d += 360.0
            return d
        }

        fun project(lat: Double, lon: Double, bearingDeg: Double, meters: Double): Pair<Double, Double> {
            if (meters == 0.0) return lat to lon
            val b = Math.toRadians(bearingDeg)
            val dLat = meters * cos(b) / EARTH_R
            val dLon = meters * sin(b) / (EARTH_R * cos(Math.toRadians(lat)))
            return (lat + Math.toDegrees(dLat)) to (lon + Math.toDegrees(dLon))
        }

        fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val x = Math.toRadians(lon2 - lon1) * cos(Math.toRadians((lat1 + lat2) / 2))
            val y = Math.toRadians(lat2 - lat1)
            return sqrt(x * x + y * y) * EARTH_R
        }
    }
}
