package br.painellivre.nav.native

/** Evita recalcular por ruído, fix velho ou a cada atualização do GPS. Relógio monotônico. */
class RouteFixPolicy {
    private var deviatedAt: Long? = null
    var lastRequest: Long? = null
        private set
    fun requested(now: Long) { lastRequest = now }
    fun reset() { deviatedAt = null; lastRequest = null }
    fun shouldRecalculate(offRoute: Double, now: Long, busy: Boolean): Boolean {
        if (offRoute <= 60) { deviatedAt = null; return false }
        if (deviatedAt == null) deviatedAt = now
        return !busy && now - deviatedAt!! >= 8_000 &&
            (lastRequest == null || now - lastRequest!! >= 30_000)
    }
    companion object {
        fun usable(ageNanos: Long, accuracy: Float?) = ageNanos in 0..60_000_000_000L &&
            (accuracy == null || accuracy.isFinite() && accuracy in 0f..50f)
    }
}
