package br.painellivre.car

import android.content.Context
import br.painellivre.cluster.PanelMenu
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * Leitura (somente leitura!) do painel pelo SDK BYD da firmware (`BYDAutoInstrumentDevice`), sob o
 * uid do app, para o menu de navegação do painel (issue #9). Por nome de método e por reflexão,
 * como o [VehicleData]: sem o SDK (emulador) só devolve erro.
 *
 * Quando o SDK recusa a permissão (DiLink 3: as BYDAUTO_*_GET não saem por `pm grant`), quem lê é
 * o [br.painellivre.cluster.PanelMenuReader] pelo ADB local, com o único comando conferido no carro.
 */
object InstrumentSdk {

    private const val DEVICE_CLASS = "android.hardware.bydauto.instrument.BYDAutoInstrumentDevice"
    private const val FEATURES_CLASS = "android.hardware.bydauto.BYDAutoFeatureIds\$Instrument"

    /**
     * [denied] = o SDK recusou por permissão (SecurityException): é o caso em que vale tentar o ADB.
     * [source] = de onde veio a leitura ("SDK" ou "ADB").
     */
    data class Reading(
        val raw: Int?,
        val error: String?,
        val detail: String,
        val denied: Boolean = false,
        val source: String = "SDK",
    ) {
        val mode get() = if (error != null) PanelMenu.Mode.UNKNOWN else PanelMenu.modeOf(raw)
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
        val cause = if (t is InvocationTargetException) t.targetException else t
        Reading(null, VehicleReadings.failure(t), "", denied = cause is SecurityException)
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
