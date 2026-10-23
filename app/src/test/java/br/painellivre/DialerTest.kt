package br.painellivre

import br.painellivre.phone.CallAccounts
import br.painellivre.phone.CallPlan
import br.painellivre.phone.CallPlan.Route
import br.painellivre.phone.DialNumber
import br.painellivre.phone.PhoneApps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Issue #3: discador interno quando a central não tem app de telefone. */
class DialerTest {

    private fun type(keys: String) = keys.fold("") { n, k -> DialNumber.append(n, k) }

    @Test fun keypadAcceptsOnlyPhoneKeys() {
        assertEquals("11987654321", type("11987654321"))
        assertEquals("*100#", type("*100#"))
        assertEquals("119", type("1a1-9 "))
        assertEquals("+5511", type("+55+11")) // "+" só no começo
        assertEquals("1", DialNumber.backspace("12"))
        assertEquals("", DialNumber.backspace(""))
    }

    @Test fun numberHasAMaximumLength() {
        val long = type("1".repeat(40))
        assertEquals(DialNumber.MAX_LEN, long.length)
        assertEquals(long, DialNumber.append(long, '2'))
    }

    @Test fun cleansPastedNumbers() {
        assertEquals("11987654321", DialNumber.clean("(11) 98765-4321"))
        assertEquals("+5511987654321", DialNumber.clean("tel:+55 11 98765-4321"))
        assertEquals("*100#", DialNumber.clean("tel:*100%23"))
        assertEquals("", DialNumber.clean(null))
        assertEquals("5511", DialNumber.clean("55+11"))
    }

    @Test fun dialableNeedsThreeDigits() {
        assertTrue(DialNumber.isDialable("190"))
        assertTrue(DialNumber.isDialable("*100#"))
        assertFalse(DialNumber.isDialable("19"))
        assertFalse(DialNumber.isDialable("+"))
        assertFalse(DialNumber.isDialable("*#"))
    }

    @Test fun telUriEscapesHash() {
        assertEquals("tel:11987654321", DialNumber.telUri("11987654321"))
        assertEquals("tel:*100%23", DialNumber.telUri("*100#"))
        assertEquals("tel:+5511987654321", DialNumber.telUri("+5511987654321"))
    }

    @Test fun brazilianFormatting() {
        assertEquals("(11) 98765-4321", DialNumber.format("11987654321"))
        assertEquals("(11) 3333-4444", DialNumber.format("1133334444"))
        assertEquals("98765-4321", DialNumber.format("987654321"))
        assertEquals("3333-4444", DialNumber.format("33334444"))
        assertEquals("+55 (11) 98765-4321", DialNumber.format("+5511987654321"))
        assertEquals("0800 123 4567", DialNumber.format("08001234567"))
        assertEquals("0 15 (11) 98765-4321", DialNumber.format("01511987654321"))
        assertEquals("190", DialNumber.format("190"))
        assertEquals("*100#", DialNumber.format("*100#"))
        assertEquals("+1 555", DialNumber.format("+1 555")) // estrangeiro fica como está
        assertEquals("1198", DialNumber.format("1198")) // incompleto fica como está
        assertEquals("", DialNumber.format(""))
    }

    private fun env(
        perm: Boolean = false, call: Boolean = false, dial: Boolean = false, telecom: Boolean = false, car: Boolean = false,
        hfpAccount: Boolean = false, callIsApp: Boolean = false, hfpDevice: Boolean = false,
    ) = CallPlan.Env(perm, call, dial, telecom, car, hfpAccount, callIsApp, hfpDevice)

    @Test fun noPhoneAppNoCrash() {
        // Exatamente o caso da issue: nada responde ao ACTION_DIAL e não há app do carro.
        assertEquals(emptyList<Route>(), CallPlan.routes(env()))
        assertFalse(CallPlan.shouldAskPermission(env(), askedBefore = false))
    }

    @Test fun routesInOrder() {
        // Issue #15: ligar direto (Telecom) vem antes de abrir o discador ou o app do carro.
        assertEquals(
            listOf(Route.CALL_INTENT, Route.PLACE_CALL, Route.DIAL_INTENT, Route.CAR_PHONE_APP),
            CallPlan.routes(env(perm = true, call = true, dial = true, telecom = true, car = true)),
        )
        // Sem a permissão não tenta ligar direto, mas ainda abre o discador do sistema ou o app do carro.
        assertEquals(listOf(Route.DIAL_INTENT, Route.CAR_PHONE_APP), CallPlan.routes(env(call = true, dial = true, telecom = true, car = true)))
        assertEquals(listOf(Route.PLACE_CALL), CallPlan.routes(env(perm = true, telecom = true)))
        assertEquals(listOf(Route.CAR_PHONE_APP), CallPlan.routes(env(car = true)))
    }

    @Test fun bluetoothAccountComesFirstAndCarAppCallIntentIsOnlyAFallback() {
        // Caso do carro: o ACTION_CALL é atendido pelo app de telefone da BYD e havia conta HFP.
        assertEquals(
            listOf(Route.PLACE_CALL_HFP, Route.HFP_DIAL, Route.PLACE_CALL, Route.CALL_INTENT_APP, Route.DIAL_INTENT, Route.CAR_PHONE_APP),
            CallPlan.routes(env(perm = true, call = true, dial = true, telecom = true, car = true, hfpAccount = true, callIsApp = true, hfpDevice = true)),
        )
        // Sem CALL_PHONE: o cliente HFP ainda liga direto (só precisa de BLUETOOTH).
        assertEquals(listOf(Route.HFP_DIAL, Route.DIAL_INTENT), CallPlan.routes(env(dial = true, hfpAccount = true, hfpDevice = true, telecom = true)))
        // Conta HFP sem Telecom não vale.
        assertEquals(emptyList<Route>(), CallPlan.routes(env(perm = true, hfpAccount = true)))
    }

    @Test fun directRoutesAreTheOnesThatDoNotOpenAnotherApp() {
        assertEquals(setOf(Route.PLACE_CALL_HFP, Route.CALL_INTENT, Route.HFP_DIAL, Route.PLACE_CALL), Route.values().filter { it.direct }.toSet())
    }

    @Test fun afterCheckNeverRetriesAlone() {
        assertEquals(CallPlan.Outcome.CALLING, CallPlan.afterCheck(Route.PLACE_CALL_HFP, true))
        assertEquals(CallPlan.Outcome.NOT_CONFIRMED, CallPlan.afterCheck(Route.HFP_DIAL, false))
        assertEquals(CallPlan.Outcome.UNKNOWN, CallPlan.afterCheck(Route.CALL_INTENT, null)) // sem READ_PHONE_STATE
        assertEquals(CallPlan.Outcome.OPENED_APP, CallPlan.afterCheck(Route.DIAL_INTENT, false))
        assertEquals(CallPlan.Outcome.OPENED_APP, CallPlan.afterCheck(Route.CALL_INTENT_APP, true))
    }

    @Test fun findsTheBluetoothCallAccount() {
        val sim = CallAccounts.Account("com.android.phone", "com.android.services.telephony.TelephonyConnectionService", "SIM 1")
        val sip = CallAccounts.Account("com.android.phone", "com.android.services.telephony.sip.SipConnectionService", "SIP")
        val hfp = CallAccounts.Account("com.android.bluetooth", "com.android.bluetooth.hfpclient.connserv.HfpClientConnectionService", "Meu celular")
        val byd = CallAccounts.Account("com.byd.btphone", "com.byd.btphone.BtConnectionService", "Bluetooth")
        assertEquals(hfp, CallAccounts.pickHfp(listOf(sim, sip, hfp)))
        assertEquals(byd, CallAccounts.pickHfp(listOf(sim, byd)))
        assertNull(CallAccounts.pickHfp(listOf(sim, sip)))
        assertNull(CallAccounts.pickHfp(emptyList()))
    }

    @Test fun knowsWhenCallIntentGoesToAnApp() {
        assertFalse(CallAccounts.callIntentIsApp(null))
        assertFalse(CallAccounts.callIntentIsApp("com.android.server.telecom"))
        assertFalse(CallAccounts.callIntentIsApp("com.android.phone"))
        assertTrue(CallAccounts.callIntentIsApp("com.byd.btphone"))
    }

    @Test fun asksCallPermissionOnceAndOnlyIfUseful() {
        assertTrue(CallPlan.shouldAskPermission(env(call = true), askedBefore = false))
        assertTrue(CallPlan.shouldAskPermission(env(telecom = true), askedBefore = false))
        assertFalse(CallPlan.shouldAskPermission(env(call = true), askedBefore = true))
        assertFalse(CallPlan.shouldAskPermission(env(perm = true, call = true), askedBefore = false))
        assertFalse(CallPlan.shouldAskPermission(env(dial = true, car = true), askedBefore = false))
    }

    @Test fun findsTheCarPhoneApp() {
        val apps = listOf(
            PhoneApps.App("com.byd.music", "Música"),
            PhoneApps.App("com.byd.bluetooth", "Bluetooth"),
            PhoneApps.App("com.byd.btphone", "Telefone"),
            PhoneApps.App("com.android.contacts", "Contatos"),
            PhoneApps.App("br.painellivre", "Painel Livre Telefone"),
        )
        assertEquals("com.byd.btphone", PhoneApps.pick(apps, "br.painellivre")?.packageName)
        assertEquals("com.byd.bluetooth", PhoneApps.pick(apps.filter { it.packageName != "com.byd.btphone" }, "br.painellivre")?.packageName)
        assertEquals("com.android.dialer", PhoneApps.pick(listOf(PhoneApps.App("com.android.dialer", "Phone"), PhoneApps.App("com.android.contacts", "Contatos")), "x")?.packageName)
        assertNull(PhoneApps.pick(listOf(PhoneApps.App("com.byd.music", "Música"), PhoneApps.App("br.painellivre", "Telefone")), "br.painellivre"))
        assertEquals("com.byd.phone", PhoneApps.pick(listOf(PhoneApps.App("com.byd.phone", "电话")), "x")?.packageName)
    }
}
