package br.painellivre.phone

/**
 * **Kotlin puro.** Número digitado no discador interno (issue #3): o que o teclado aceita, como
 * mostrar no formato brasileiro e o `tel:` que vai para o Android.
 */
object DialNumber {

    const val MAX_LEN = 20

    /** Tecla do teclado → novo número. "+" só no começo (toque longo no 0). */
    fun append(current: String, key: Char): String = when {
        current.length >= MAX_LEN -> current
        key in '0'..'9' || key == '*' || key == '#' -> current + key
        key == '+' && current.isEmpty() -> "+"
        else -> current
    }

    fun backspace(current: String): String = current.dropLast(1)

    /** Número colado ou vindo de outro app ("(11) 98765-4321", "tel:+55…"): só dígitos, * e #, + no começo. */
    fun clean(raw: String?): String {
        val s = raw.orEmpty().trim().removePrefix("tel:").removePrefix("TEL:").replace("%23", "#").replace("%2B", "+")
        val sb = StringBuilder()
        for (c in s) {
            when {
                c in '0'..'9' || c == '*' || c == '#' -> sb.append(c)
                c == '+' && sb.isEmpty() -> sb.append(c)
            }
        }
        return sb.toString().take(MAX_LEN)
    }

    /** Pelo menos 3 dígitos (190, 192 e 193 têm 3). */
    fun isDialable(n: String): Boolean = n.count { it in '0'..'9' } >= 3

    /** `tel:` com "#" escapado (senão o Android corta o número no "#"). */
    fun telUri(n: String): String = "tel:" + n.replace("#", "%23")

    /** Formato para mostrar; números incompletos ou códigos (* e #) ficam como digitados. */
    fun format(n: String): String {
        if (n.any { it == '*' || it == '#' }) return n
        if (n.startsWith("+55") && n.length in 13..14) return "+55 " + national(n.drop(3))
        if (n.startsWith("+")) return n
        // 0800/0300/0500/0900 + 7 dígitos.
        if (n.length == 11 && n[0] == '0' && n[2] == '0' && n[3] == '0') return "${n.take(4)} ${n.substring(4, 7)} ${n.drop(7)}"
        // 0 + operadora (2) + DDD (2) + número (8 ou 9): "0 15 (11) 98765-4321".
        if (n.startsWith("0") && n.length in 13..14) return "0 ${n.substring(1, 3)} " + national(n.drop(3))
        if (n.startsWith("0")) return n
        return national(n)
    }

    private fun national(n: String): String = when (n.length) {
        11 -> "(${n.take(2)}) ${n.substring(2, 7)}-${n.drop(7)}"
        10 -> "(${n.take(2)}) ${n.substring(2, 6)}-${n.drop(6)}"
        9 -> "${n.take(5)}-${n.drop(5)}"
        8 -> "${n.take(4)}-${n.drop(4)}"
        else -> n
    }
}

/**
 * **Kotlin puro.** Como ligar na central. Na DiLink o telefone é o celular por Bluetooth (HFP), e a BYD
 * nem sempre instala um app que responda a `ACTION_DIAL` (era o "nenhum aplicativo" da issue #3).
 * O Painel Livre tenta, em ordem, e passa para o próximo se a central recusar.
 *
 * Issue #15: no carro a ligação ia para o app de telefone da BYD (o motorista tinha de discar de
 * novo lá). Por isso os caminhos **diretos** vêm antes: a conta de chamada do Bluetooth no Telecom
 * (`HfpClientConnectionService`, a mesma que o AOSP usa no carro), o `ACTION_CALL` só se quem atende
 * for o próprio Telecom do Android e o cliente HFP. Abrir um app (o `ACTION_CALL` desviado pela BYD,
 * `ACTION_DIAL`, o app do carro) fica de reserva. A tela de chamada em andamento continua sendo a da
 * central: é o Android que a mostra, com qualquer caminho.
 */
object CallPlan {

    enum class Route {
        /** `TelecomManager.placeCall` com a conta (PhoneAccount) do Bluetooth/HFP: liga pelo celular. */
        PLACE_CALL_HFP,
        /** `ACTION_CALL` (precisa de CALL_PHONE): o Telecom do Android liga direto, sem app de telefone. */
        CALL_INTENT,
        /** `BluetoothHeadsetClient.dial` (cliente HFP do Android, por reflexão) no celular conectado. */
        HFP_DIAL,
        /** `TelecomManager.placeCall` com a conta padrão (precisa de CALL_PHONE). */
        PLACE_CALL,
        /** `ACTION_CALL` quando quem responde é um app (o telefone da BYD): pode só abrir o app. */
        CALL_INTENT_APP,
        /** `ACTION_DIAL` com o número: abre o app de telefone já preenchido. */
        DIAL_INTENT,
        /** Abre o app de telefone/Bluetooth do carro (sem o número; o motorista disca lá). */
        CAR_PHONE_APP;

        /** Liga sem passar por outra tela; os outros só abrem um app. */
        val direct get() = this == PLACE_CALL_HFP || this == CALL_INTENT || this == HFP_DIAL || this == PLACE_CALL
    }

    data class Env(
        val hasCallPermission: Boolean,
        val callIntentResolves: Boolean,
        val dialIntentResolves: Boolean,
        val telecomAvailable: Boolean,
        val carPhoneApp: Boolean,
        /** O Telecom tem uma conta de chamada do Bluetooth/HFP (ver [CallAccounts]). */
        val hfpAccount: Boolean = false,
        /** O `ACTION_CALL` é atendido por um app (o telefone da BYD) e não pelo Telecom do Android. */
        val callIntentIsApp: Boolean = false,
        /** O cliente HFP do Android tem um celular conectado. */
        val hfpDevice: Boolean = false,
    )

    fun routes(e: Env): List<Route> = buildList {
        val callTelecom = e.hasCallPermission && e.telecomAvailable
        if (callTelecom && e.hfpAccount) add(Route.PLACE_CALL_HFP)
        if (e.hasCallPermission && e.callIntentResolves && !e.callIntentIsApp) add(Route.CALL_INTENT)
        if (e.hfpDevice) add(Route.HFP_DIAL)
        if (callTelecom) add(Route.PLACE_CALL)
        if (e.hasCallPermission && e.callIntentResolves && e.callIntentIsApp) add(Route.CALL_INTENT_APP)
        if (e.dialIntentResolves) add(Route.DIAL_INTENT)
        if (e.carPhoneApp) add(Route.CAR_PHONE_APP)
    }

    /** Pede CALL_PHONE uma vez, e só se houver como ligar direto com ela. */
    fun shouldAskPermission(e: Env, askedBefore: Boolean): Boolean =
        !e.hasCallPermission && !askedBefore && (e.callIntentResolves || e.telecomAvailable)

    /**
     * Depois de um caminho direto o app confere se o Android tem chamada em andamento. Sem
     * confirmação não tenta outro caminho sozinho (ligaria duas vezes): só avisa o motorista.
     */
    fun afterCheck(route: Route, inCall: Boolean?): Outcome = when {
        !route.direct -> Outcome.OPENED_APP
        inCall == true -> Outcome.CALLING
        inCall == false -> Outcome.NOT_CONFIRMED
        else -> Outcome.UNKNOWN
    }

    enum class Outcome { CALLING, NOT_CONFIRMED, UNKNOWN, OPENED_APP }
}

/** **Kotlin puro.** Contas de chamada do Telecom e quem atende o `ACTION_CALL` (issue #15). */
object CallAccounts {

    data class Account(val componentPackage: String, val componentClass: String, val label: String)

    /** Quem atende `ACTION_CALL` sem abrir app de telefone: o próprio Telecom/telefonia do Android. */
    val SYSTEM_CALL_HANDLERS = setOf("com.android.server.telecom", "com.android.phone", "android")

    fun isBluetooth(a: Account): Boolean {
        val cls = a.componentClass.lowercase()
        val pkg = a.componentPackage.lowercase()
        val label = a.label.lowercase()
        return "hfp" in cls || pkg == "com.android.bluetooth" ||
            "bluetooth" in pkg || "bluetooth" in label || "蓝牙" in label
    }

    /** Conta HFP; se houver várias (dois celulares), a primeira habilitada que o Telecom devolveu. */
    fun pickHfp(accounts: List<Account>): Account? = accounts.firstOrNull(::isBluetooth)

    /** null = ninguém atende; false = Telecom do Android; true = um app (pode só abrir a tela dele). */
    fun callIntentIsApp(resolvedPackage: String?): Boolean =
        resolvedPackage != null && resolvedPackage !in SYSTEM_CALL_HANDLERS
}

/** **Kotlin puro.** Acha, entre os apps instalados, o de telefone do carro (BYD ou outro). */
object PhoneApps {

    data class App(val packageName: String, val label: String)

    private val LABELS = listOf("telefone", "phone", "discador", "dialer", "ligações", "chamadas", "电话", "拨号")
    private val PACKAGES = listOf("dialer", "phone", "contacts", "incall")
    private val BLUETOOTH = listOf("bluetooth", "btphone", "bt.phone", "蓝牙")

    /** Melhor candidato ou null. Nome "Telefone"/"Phone" vale mais que o pacote; Bluetooth por último. */
    fun pick(apps: List<App>, ownPackage: String): App? = apps
        .filter { it.packageName != ownPackage }
        .map { it to score(it) }
        .filter { it.second > 0 }
        .maxByOrNull { it.second }
        ?.first

    fun score(a: App): Int {
        val label = a.label.lowercase()
        val pkg = a.packageName.lowercase()
        var s = 0
        if (LABELS.any { label == it }) s += 100
        else if (LABELS.any { label.contains(it) }) s += 60
        if (PACKAGES.any { pkg.contains(it) }) s += 40
        if (BLUETOOTH.any { label.contains(it) || pkg.contains(it) }) s += 20
        // "Contatos" sozinho não liga; fica atrás de qualquer app de telefone.
        if (s > 0 && pkg.contains("contacts") && LABELS.none { label.contains(it) }) s -= 30
        return s
    }
}
