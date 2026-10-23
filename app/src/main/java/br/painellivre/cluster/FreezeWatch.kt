package br.painellivre.cluster

/**
 * Vigia de congelamento da tela do painel (ideia do SealNav, docs/SEALNAV_RE.md achado G). A cada
 * ~1,5 s recebe uma [Sample] e devolve uma ação, em escada:
 *  1. sem primeiro quadro em 10 s → reabrir a tela (uma vez);
 *  2. thread principal parada > 5 s → só registra;
 *  3. carro parado → nada (o MapLibre não desenha quando nada muda, é normal);
 *  4. andando e sem quadros > 5 s → força redesenho; sem resposta em 1,5 s → de novo;
 *  5. ainda nada → recria a superfície do mapa (quem chama limita com [Budget]).
 *
 * Kotlin puro (sem Android) para ser testado na JVM.
 */
class FreezeWatch(
    private val firstFrameTimeoutMs: Long = 10_000,
    private val mainStallMs: Long = 5_000,
    private val noFrameMs: Long = 5_000,
    private val retryMs: Long = 1_500,
    private val rearmMs: Long = 30_000,
) {
    enum class Action { NONE, RELAUNCH, MAIN_STALLED, REPAINT, RECREATE_SURFACE }

    data class Sample(
        val now: Long,
        val createdAt: Long,
        val firstFrameAt: Long?,
        val lastFrameAt: Long,
        val mainBeatAt: Long,
        /** O carro está andando (a câmera muda a cada quadro, então deveriam sair quadros). */
        val moving: Boolean,
    )

    private var relaunchAsked = false
    private var stallReported = false
    private var stage = 0
    private var lastActionAt = 0L

    fun evaluate(s: Sample): Action {
        if (s.firstFrameAt == null) {
            if (!relaunchAsked && s.now - s.createdAt >= firstFrameTimeoutMs) {
                relaunchAsked = true
                return Action.RELAUNCH
            }
            return Action.NONE
        }
        if (s.now - s.mainBeatAt >= mainStallMs) {
            if (stallReported) return Action.NONE
            stallReported = true
            return Action.MAIN_STALLED
        }
        stallReported = false
        if (!s.moving || s.now - s.lastFrameAt < noFrameMs) {
            stage = 0
            return Action.NONE
        }
        if (stage >= 3) {
            // Já recriou e não voltou: espera um bom tempo antes de recomeçar a escada.
            if (s.now - lastActionAt < rearmMs) return Action.NONE
            stage = 0
        }
        if (stage > 0 && s.now - lastActionAt < retryMs) return Action.NONE
        stage++
        lastActionAt = s.now
        return if (stage <= 2) Action.REPAINT else Action.RECREATE_SURFACE
    }

    /** Limite de ações caras que vale entre telas recriadas: no máximo [max] a cada [windowMs]. */
    class Budget(private val max: Int, private val windowMs: Long) {
        private val times = ArrayDeque<Long>()

        @Synchronized
        fun tryTake(now: Long): Boolean {
            while (times.isNotEmpty() && now - times.first() >= windowMs) times.removeFirst()
            if (times.size >= max) return false
            times.addLast(now)
            return true
        }
    }
}

/**
 * Convivência com outros apps no painel (ideia do SealNav, docs/SEALNAV_RE.md achado E): se outro app
 * cobre a tela do painel, o Painel Livre volta uma vez; se for coberto [maxTakeovers] vezes em
 * [windowMs], sai da frente até o motorista pedir o painel de novo ([reset]).
 */
class YieldPolicy(private val maxTakeovers: Int = 2, private val windowMs: Long = 30_000) {
    private val takeovers = ArrayDeque<Long>()

    var yielded = false
        private set

    /** Outro app cobriu a tela do painel. Devolve true se é para retomar. */
    @Synchronized
    fun onTakeover(now: Long): Boolean {
        if (yielded) return false
        takeovers.addLast(now)
        while (takeovers.isNotEmpty() && now - takeovers.first() > windowMs) takeovers.removeFirst()
        if (takeovers.size >= maxTakeovers) {
            yielded = true
            return false
        }
        return true
    }

    @Synchronized
    fun reset() {
        takeovers.clear()
        yielded = false
    }
}
