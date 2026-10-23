package br.painellivre.cluster

import java.util.Locale

/**
 * Código de "tamanho do painel" do AutoContainer: 29 = 8,8", 30 = 12,3", 31 = 10,25".
 *
 * O SealNav manda esse código antes do 16/35 (docs/SEALNAV_RE.md, achados B e D) e o DashCast não
 * manda — é o provável motivo de o SealNav funcionar melhor no Song Plus. Mas mandar o código
 * errado já deixou painéis de 8,8" em "modo simples", então aqui um código só sai quando:
 *  - o motorista escolheu nas Configurações e o painel medido não contradiz; ou
 *  - no automático, o modelo é de painel 12,3" (Song Plus, Seal U) E o painel já foi medido
 *    com geometria de 12,3" (≥ 1800 de largura, proporção ≥ 2). Nunca em 1280×480.
 *
 * Kotlin puro (sem Android) para ser testado na JVM.
 */
object PanelSize {
    const val CODE_8_8 = 29
    const val CODE_12_3 = 30
    const val CODE_10_25 = 31
    val CODES = setOf(CODE_8_8, CODE_12_3, CODE_10_25)

    const val SETTING_AUTO = "auto"
    const val SETTING_OFF = "off"

    data class Geometry(val width: Int, val height: Int) {
        val ratio: Double get() = if (height > 0) width.toDouble() / height else 0.0
        override fun toString() = "${width}x$height"

        companion object {
            fun parse(s: String?): Geometry? {
                val m = Regex("""^(\d+)x(\d+)$""").find(s?.trim() ?: return null) ?: return null
                return Geometry(m.groupValues[1].toInt(), m.groupValues[2].toInt()).takeIf { it.width > 0 && it.height > 0 }
            }
        }
    }

    data class Decision(val code: Int?, val reason: String)

    /** Nomes que identificam um painel de 12,3" (procurados em Build.MODEL/PRODUCT/DEVICE/FINGERPRINT). */
    private val MODELS_12_3 = listOf("song plus", "songplus", "song_plus", "song+", "seal u", "seal_u", "sealu")

    fun matchModel(identity: String): String? {
        val s = identity.lowercase(Locale.ROOT)
        return MODELS_12_3.firstOrNull { s.contains(it) }
    }

    /** O código combina com a geometria medida do display do painel? */
    fun fits(code: Int, g: Geometry): Boolean = when (code) {
        CODE_12_3 -> g.width >= 1800 && g.height < 1000 && g.ratio >= 2.0
        CODE_8_8 -> g.width < 1800 && g.ratio >= 2.0
        CODE_10_25 -> g.height < 1000 && g.ratio >= 2.0
        else -> false
    }

    /**
     * @param setting valor das Configurações: "auto", "off" ou "29"/"30"/"31".
     * @param identity texto com modelo/produto/aparelho/fingerprint da central.
     * @param geometry tamanho do display do painel (o atual ou o último medido), se conhecido.
     */
    fun decide(setting: String, identity: String, geometry: Geometry?): Decision {
        if (setting == SETTING_OFF) return Decision(null, "não enviar (escolhido nas Configurações)")
        if (setting != SETTING_AUTO) {
            val code = setting.toIntOrNull()
            if (code == null || code !in CODES) return Decision(null, "valor inválido \"$setting\" — nada enviado")
            if (geometry != null && !fits(code, geometry)) {
                return Decision(null, "BLOQUEADO: código $code não combina com o painel medido $geometry")
            }
            return Decision(code, "escolhido nas Configurações" + (geometry?.let { ", painel $it" } ?: ", painel ainda não medido"))
        }
        val model = matchModel(identity)
            ?: return Decision(null, "automático: modelo não reconhecido — nada enviado (dá para escolher nas Configurações)")
        if (geometry == null) {
            return Decision(null, "automático: modelo \"$model\", mas o painel ainda não foi medido — envia a partir da próxima ativação")
        }
        if (!fits(CODE_12_3, geometry)) {
            return Decision(null, "BLOQUEADO: modelo \"$model\", mas o painel medido $geometry não é de 12,3\"")
        }
        return Decision(CODE_12_3, "automático: modelo \"$model\" + painel $geometry")
    }
}
