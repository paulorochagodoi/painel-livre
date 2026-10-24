package br.painellivre.nav

import br.painellivre.ui.aura.AuraColors

/**
 * **Kotlin puro.** Jeito do mapa (tela central e painel): ruas mais largas, rota em `accent` com
 * contorno e a seta do carro no estilo do Waze. Tudo aqui é número e regra; quem aplica no MapLibre é
 * o [MapStyler] (Android).
 *
 * Ruas: os estilos do OpenFreeMap (positron, dark, liberty, fiord) desenham as vias em camadas de
 * linha da fonte `transportation`, com largura que já cresce com o zoom (`interpolate` no zoom).
 * Multiplicamos os valores de cada parada pelo fator da via: a proporção ao zoom do estilo continua,
 * só que mais larga. Ferrovia, trilha, pier e as camadas "subtle" (zoom de país) ficam como estão.
 */
object MapLook {

    const val SOURCE_LAYER = "transportation"

    /** Vias principais e secundárias (motorway, trunk, primary, secondary, tertiary, "major", links). */
    const val MAJOR_FACTOR = 1.6
    /** Ruas do bairro e de serviço: também mais largas, um pouco menos que as principais. */
    const val MINOR_FACTOR = 1.4

    private val SKIP = listOf("rail", "path", "pedestrian", "pier", "ferry", "aeroway", "subtle", "hatching", "dashline", "transit")
    private val MAJOR = listOf("motorway", "trunk", "primary", "secondary", "tertiary", "major", "link")
    private val MINOR = listOf("minor", "street", "service", "track")

    /** Fator de largura da camada [layerId] (null = não é rua ou não deve mudar). */
    fun roadFactor(layerId: String, sourceLayer: String?): Double? {
        if (sourceLayer != SOURCE_LAYER) return null
        val id = layerId.lowercase()
        if (SKIP.any { it in id }) return null
        if (MINOR.any { it in id }) return MINOR_FACTOR
        if (MAJOR.any { it in id }) return MAJOR_FACTOR
        return null
    }

    /**
     * Largura escalada de uma expressão do estilo (como o MapLibre devolve em `toArray()`: listas ou
     * arrays aninhados, números e textos). `interpolate`/`step` têm as paradas multiplicadas (o zoom
     * precisa continuar no topo da expressão); outras expressões sem zoom viram `["*", fator, expr]`.
     * Null = não dá para escalar sem quebrar o estilo (fica como está).
     */
    fun scaleWidth(expr: Any?, factor: Double): Any? {
        val list = asList(expr) ?: return (expr as? Number)?.let { it.toDouble() * factor }
        val op = list.firstOrNull() as? String ?: return null
        fun value(v: Any?): Any? = when (v) {
            is Number -> v.toDouble() * factor
            else -> if (containsZoom(v)) null else listOf("*", factor, normalize(v))
        }
        return when (op) {
            "interpolate", "interpolate-hcl", "interpolate-lab" -> {
                if (list.size < 5 || (list.size - 3) % 2 != 0) return null
                list.mapIndexed { i, v -> if (i >= 4 && (i - 4) % 2 == 0) value(v) ?: return null else normalize(v) }
            }
            "step" -> {
                if (list.size < 3 || list.size % 2 != 1) return null
                list.mapIndexed { i, v -> if (i >= 2 && i % 2 == 0) value(v) ?: return null else normalize(v) }
            }
            else -> if (containsZoom(list)) null else listOf("*", factor, normalize(list))
        }
    }

    /** JSON da expressão (para `Expression.raw`). */
    fun toJson(v: Any?): String = when (v) {
        null -> "null"
        is String -> "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        is Double -> if (v == Math.floor(v) && !v.isInfinite() && kotlin.math.abs(v) < 1e15) v.toLong().toString() else v.toString()
        is Float -> toJson(v.toDouble())
        is Number, is Boolean -> v.toString()
        else -> asList(v)?.joinToString(",", "[", "]") { toJson(it) } ?: "\"$v\""
    }

    private fun asList(v: Any?): List<Any?>? = when (v) {
        is List<*> -> v
        is Array<*> -> v.toList()
        else -> null
    }

    private fun normalize(v: Any?): Any? = asList(v)?.map(::normalize) ?: v

    private fun containsZoom(v: Any?): Boolean {
        val l = asList(v) ?: return false
        return l.firstOrNull() == "zoom" || l.any(::containsZoom)
    }

    // ── Rota ────────────────────────────────────────────────────────────────

    /** Rota (linha e contorno) por zoom: sempre mais larga que a via mais larga escalada. */
    val ROUTE_STOPS = listOf(10.0 to 4.0, 14.0 to 8.0, 16.0 to 13.0, 18.0 to 22.0, 20.0 to 34.0)
    /** Contorno: a linha + esta borda de cada lado. */
    const val ROUTE_CASING_EDGE = 3.0

    fun routeCasingStops() = ROUTE_STOPS.map { (z, w) -> z to w + 2 * ROUTE_CASING_EDGE }

    data class RouteColors(val line: Int, val casing: Int)

    /** Rota em `accent`; contorno claro no tema claro e escuro no escuro (separa das ruas do mapa). */
    fun routeColors(c: AuraColors) = RouteColors(line = c.accent, casing = if (c.dark) c.bg else c.surface)

    // ── Seta do carro (estilo Waze) ─────────────────────────────────────────

    data class ArrowColors(val fill: Int, val outline: Int, val shadow: Int)

    /** Seta cheia em `accent`, contorno claro e sombra leve, nos dois temas. */
    fun arrowColors(c: AuraColors) = ArrowColors(fill = c.accent, outline = 0xFFFFFFFF.toInt(), shadow = 0x55000000)

    /**
     * Pontos da seta numa caixa 1 × 1 (x para a direita, y para baixo, ponta para cima): larga como a
     * do Waze, com a base recortada. Ordem: ponta, base direita, recorte, base esquerda.
     */
    val ARROW = listOf(0.5 to 0.06, 0.94 to 0.9, 0.5 to 0.72, 0.06 to 0.9)
    /** Contorno e sombra em fração do tamanho da seta. */
    const val ARROW_OUTLINE = 0.085
    const val ARROW_SHADOW_DY = 0.04
    /** Cantos arredondados (fração do tamanho), como a seta do Waze. */
    const val ARROW_CORNER = 0.08

    /** Seta na busca da tela central, em dp do desenho Aura (era 44). */
    const val CENTRAL_ARROW_DP = 60
}
