package br.painellivre.car

import java.lang.reflect.InvocationTargetException

/** Leitura parcial: um método recusado não apaga os outros nem vira zero. */
class VehicleReadings(private val invoke: (String) -> Any?) {
    val errors = mutableListOf<String>()

    fun number(method: String, max: Double): Double? = try {
        val value = (invoke(method) as? Number)?.toDouble()
        if (value == null || !value.isFinite() || value !in 0.0..max) {
            errors += "$method: valor indisponível ou inválido"
            null
        } else value
    } catch (t: Exception) {
        errors += "$method: ${failure(t)}"
        null
    }

    companion object {
        fun failure(t: Throwable): String {
            val cause = if (t is InvocationTargetException) t.targetException else t
            return when (cause) {
                is SecurityException -> "permissão BYD recusada; conceder permissões pode não bastar nesta firmware (ajudante uid 2000 pendente)"
                is ClassNotFoundException -> "SDK BYD não encontrado"
                is NoSuchMethodException -> "método ausente nesta versão do SDK BYD"
                else -> "falha de leitura (${cause.javaClass.simpleName})"
            }
        }
    }
}
