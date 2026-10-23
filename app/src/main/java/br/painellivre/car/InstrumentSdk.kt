package br.painellivre.car

import android.content.Context
import br.painellivre.cluster.PanelMenu
import java.lang.reflect.Method

/**
 * Leitura (somente leitura!) do painel pelo SDK BYD da firmware (`BYDAutoInstrumentDevice`), sob o
 * uid do app, para o menu de navegação do painel (issue #9). Por nome de método e por reflexão,
 * como o [VehicleData]: sem o SDK (emulador) só devolve erro.
 *
 * Não usamos `service call autoservice 5 …` (o caminho do DiPlay): o número da transação muda com a
 * firmware e, na DiLink 3, poderia cair num setter.
 */
object InstrumentSdk {

    private const val DEVICE_CLASS = "android.hardware.bydauto.instrument.BYDAutoInstrumentDevice"
    private const val FEATURES_CLASS = "android.hardware.bydauto.BYDAutoFeatureIds\$Instrument"

    data class Reading(val raw: Int?, val error: String?, val detail: String) {
        val mode get() = PanelMenu.modeOf(raw)
    }

    private var device: Any? = null
    private var getMethod: Method? = null

    @Synchronized
    private fun device(ctx: Context): Any = device
        ?: Class.forName(DEVICE_CLASS).getMethod("getInstance", Context::class.java)
            .invoke(null, ctx.applicationContext)!!.also { device = it }

    /** `INSTRUMENT_NAVI_TYPE` pelo `get(device, feature)` genérico do SDK. */
    @Synchronized
    fun naviType(ctx: Context): Reading = try {
        val dev = device(ctx)
        val type = runCatching { dev.javaClass.getMethod("getDevicetype").invoke(dev) as Int }.getOrNull()
            ?: PanelMenu.INSTRUMENT_DEVICE
        val field = runCatching { Class.forName(FEATURES_CLASS).getField(PanelMenu.NAVI_TYPE_FIELD).getInt(null) }.getOrNull()
        val feature = PanelMenu.featureId(field)
        val m = getMethod ?: findGet(dev.javaClass).also { getMethod = it }
        val raw = m.invoke(dev, type, feature) as? Int
        Reading(raw, null, "device=$type feature=0x${Integer.toHexString(feature)} (constante do SDK: ${field?.let { "0x" + Integer.toHexString(it) } ?: "ausente"})")
    } catch (t: Throwable) {
        Reading(null, VehicleReadings.failure(t), "")
    }

    /** Getters candidatos ([PanelMenu.DEBUG_GETTERS]): nome → valor ou erro. Só "get…" sem parâmetro. */
    @Synchronized
    fun debugGetters(ctx: Context): Map<String, String> {
        val dev = try { device(ctx) } catch (t: Throwable) { return mapOf("SDK" to VehicleReadings.failure(t)) }
        return PanelMenu.DEBUG_GETTERS.filter(PanelMenu::isReadOnlyGetter).associateWith { name ->
            try {
                dev.javaClass.getMethod(name).invoke(dev).toString()
            } catch (t: Throwable) {
                "erro: " + VehicleReadings.failure(t)
            }
        }
    }

    private fun findGet(cls: Class<*>): Method {
        var c: Class<*>? = cls
        while (c != null) {
            runCatching {
                return c.getDeclaredMethod("get", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                    .apply { isAccessible = true }
            }
            c = c.superclass
        }
        throw NoSuchMethodException("get(int,int)")
    }
}
