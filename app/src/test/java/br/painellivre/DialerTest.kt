package br.painellivre

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

    private fun env(perm: Boolean = false, call: Boolean = false, dial: Boolean = false, telecom: Boolean = false, car: Boolean = false) =
        CallPlan.Env(perm, call, dial, telecom, car)

    @Test fun noPhoneAppNoCrash() {
        // Exatamente o caso da issue: nada responde ao ACTION_DIAL e não há app do carro.
        assertEquals(emptyList<Route>(), CallPlan.routes(env()))
        assertFalse(CallPlan.shouldAskPermission(env(), askedBefore = false))
    }

    @Test fun routesInOrder() {
        assertEquals(
            listOf(Route.CALL_INTENT, Route.DIAL_INTENT, Route.PLACE_CALL, Route.CAR_PHONE_APP),
            CallPlan.routes(env(perm = true, call = true, dial = true, telecom = true, car = true)),
        )
        // Sem a permissão não tenta ligar direto, mas ainda abre o discador do sistema ou o app do carro.
        assertEquals(listOf(Route.DIAL_INTENT, Route.CAR_PHONE_APP), CallPlan.routes(env(call = true, dial = true, telecom = true, car = true)))
        assertEquals(listOf(Route.PLACE_CALL), CallPlan.routes(env(perm = true, telecom = true)))
        assertEquals(listOf(Route.CAR_PHONE_APP), CallPlan.routes(env(car = true)))
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
