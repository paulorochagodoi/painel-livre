package br.painellivre.phone

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * O que a central oferece para ligar pelo celular (issue #15): a conta HFP no Telecom e o cliente
 * HFP do Bluetooth. Tudo defensivo — a BYD pode ter trocado a pilha Bluetooth, e as chamadas
 * escondidas do Android podem ser recusadas; nesse caso só devolve nulo e o [CallPlan] segue.
 */
object CallPaths {

    /** `BluetoothProfile.HEADSET_CLIENT` (escondida no SDK público). */
    private const val HEADSET_CLIENT = 16

    data class Hfp(val handle: PhoneAccountHandle, val account: CallAccounts.Account)

    /** Contas que podem ligar (precisa de READ_PHONE_STATE); a do Bluetooth, se houver. */
    @SuppressLint("MissingPermission")
    fun accounts(ctx: Context): Pair<List<Pair<PhoneAccountHandle, CallAccounts.Account>>, String?> = try {
        val tm = ctx.getSystemService(TelecomManager::class.java) ?: return emptyList<Pair<PhoneAccountHandle, CallAccounts.Account>>() to "sem Telecom"
        tm.callCapablePhoneAccounts.map { h ->
            val label = runCatching { tm.getPhoneAccount(h)?.label?.toString() }.getOrNull().orEmpty()
            h to CallAccounts.Account(h.componentName.packageName, h.componentName.className, label)
        } to null
    } catch (t: Throwable) {
        emptyList<Pair<PhoneAccountHandle, CallAccounts.Account>>() to t.javaClass.simpleName
    }

    fun hfpAccount(list: List<Pair<PhoneAccountHandle, CallAccounts.Account>>): Hfp? {
        val pick = CallAccounts.pickHfp(list.map { it.second }) ?: return null
        return list.first { it.second == pick }.let { Hfp(it.first, it.second) }
    }

    /** Proxy do cliente HFP e o primeiro celular conectado, ou nulo (sem suporte, sem celular, recusado). */
    suspend fun hfpClient(ctx: Context): Pair<BluetoothProfile, BluetoothDevice>? = withTimeoutOrNull(2_000) {
        suspendCancellableCoroutine { cont ->
            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter == null) { cont.resume(null); return@suspendCancellableCoroutine }
            val ok = try {
                adapter.getProfileProxy(ctx.applicationContext, object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                        val dev = try { proxy.connectedDevices.firstOrNull() } catch (t: Throwable) { null }
                        if (cont.isActive) cont.resume(dev?.let { proxy to it })
                    }
                    override fun onServiceDisconnected(profile: Int) {}
                }, HEADSET_CLIENT)
            } catch (t: Throwable) {
                false
            }
            if (!ok && cont.isActive) cont.resume(null)
        }
    }

    /** `BluetoothHeadsetClient.dial(device, número)`; devolve a chamada ou null se a pilha recusou. */
    fun hfpDial(proxy: BluetoothProfile, device: BluetoothDevice, number: String): Any? =
        proxy.javaClass.getMethod("dial", BluetoothDevice::class.java, String::class.java).invoke(proxy, device, number)

    fun closeHfp(proxy: BluetoothProfile) {
        runCatching { BluetoothAdapter.getDefaultAdapter()?.closeProfileProxy(HEADSET_CLIENT, proxy) }
    }

    /** Chamada em andamento no Android (READ_PHONE_STATE); null se não deu para saber. */
    @SuppressLint("MissingPermission")
    fun inCall(ctx: Context): Boolean? = try {
        ctx.getSystemService(TelecomManager::class.java)?.isInCall
    } catch (t: Throwable) {
        null
    }
}
