package br.painellivre.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import br.painellivre.R
import br.painellivre.phone.CallPlan
import br.painellivre.phone.DialNumber
import br.painellivre.phone.PhoneApps
import br.painellivre.ui.cockpit.BackdropDrawable
import br.painellivre.ui.cockpit.Palette
import br.painellivre.util.AppLog
import br.painellivre.util.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Discador interno (issue #3). A DiLink nem sempre tem um app que responda a `ACTION_DIAL` — era o
 * "nenhum aplicativo para isso". Aqui o motorista digita o número e o app tenta ligar pelos caminhos
 * de [CallPlan], do mais direto ao app de telefone do carro. O número nunca vai para o log.
 */
class DialerActivity : AppCompatActivity() {

    private var number = ""
    private var palette = Palette.DAY
    private var carPhone: PhoneApps.App? = null
    private val density get() = resources.displayMetrics.density
    private val store by lazy { getSharedPreferences("dialer", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dialer)
        palette = Palette.forSetting(Prefs(this).theme)
        number = savedInstanceState?.getString(STATE_NUMBER) ?: DialNumber.clean(intent?.dataString)
        findViewById<ImageView>(R.id.backdrop).setImageDrawable(BackdropDrawable(palette.dark))
        buildKeys()
        palette.apply(findViewById(R.id.root), density)
        styleCallButton()

        findViewById<View>(R.id.dialClose).setOnClickListener { finish() }
        findViewById<View>(R.id.dialBackspace).apply {
            setOnClickListener { setNumber(DialNumber.backspace(number)) }
            setOnLongClickListener { setNumber(""); true }
        }
        findViewById<View>(R.id.dialCall).setOnClickListener { call() }
        findViewById<View>(R.id.dialRedial).setOnClickListener {
            val last = store.getString(KEY_LAST, null)
            if (last.isNullOrEmpty()) status(getString(R.string.dial_no_last)) else setNumber(last)
        }
        findViewById<View>(R.id.dialPhoneApp).setOnClickListener { openCarPhoneApp() }
        render()

        lifecycleScope.launch {
            carPhone = withContext(Dispatchers.IO) { findCarPhoneApp() }
            findViewById<View>(R.id.dialPhoneApp).visibility = if (carPhone != null) View.VISIBLE else View.INVISIBLE
            AppLog.i("Telefone: app do carro = ${carPhone?.packageName ?: "nenhum"}")
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_NUMBER, number)
    }

    // ── Teclado ─────────────────────────────────────────────────────────────

    private fun buildKeys() {
        val box = findViewById<LinearLayout>(R.id.dialKeys)
        val m = (6 * density).toInt()
        KEYS.forEach { row ->
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            box.addView(line, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            row.forEach { (key, letters) ->
                val tv = TextView(this).apply {
                    tag = "chip"
                    gravity = Gravity.CENTER
                    textSize = 30f
                    includeFontPadding = false
                    setTextColor(palette.text1)
                    text = keyLabel(key, letters)
                    contentDescription = if (key == '0') "0, toque longo para +" else key.toString()
                    setOnClickListener { setNumber(DialNumber.append(number, key)) }
                    if (key == '0') setOnLongClickListener { setNumber(DialNumber.append(number, '+')); true }
                }
                line.addView(tv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply { setMargins(m, m, m, m) })
            }
        }
    }

    private fun keyLabel(key: Char, letters: String): CharSequence {
        if (letters.isEmpty()) return key.toString()
        val s = SpannableString("$key\n$letters")
        s.setSpan(RelativeSizeSpan(0.38f), 2, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        s.setSpan(ForegroundColorSpan(palette.text2), 2, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return s
    }

    private fun styleCallButton() {
        findViewById<ImageView>(R.id.dialCall).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(palette.ok) }
            imageTintList = ColorStateList.valueOf(0xFFFFFFFF.toInt())
        }
    }

    private fun setNumber(n: String) {
        number = n
        render()
    }

    private fun render() {
        findViewById<TextView>(R.id.dialNumber).text = DialNumber.format(number)
        findViewById<View>(R.id.dialCall).alpha = if (DialNumber.isDialable(number)) 1f else 0.45f
        findViewById<View>(R.id.dialBackspace).visibility = if (number.isEmpty()) View.INVISIBLE else View.VISIBLE
        findViewById<View>(R.id.dialStatus).visibility = View.GONE
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> setNumber(DialNumber.append(number, '0' + (keyCode - KeyEvent.KEYCODE_0)))
            KeyEvent.KEYCODE_STAR -> setNumber(DialNumber.append(number, '*'))
            KeyEvent.KEYCODE_POUND -> setNumber(DialNumber.append(number, '#'))
            KeyEvent.KEYCODE_PLUS -> setNumber(DialNumber.append(number, '+'))
            KeyEvent.KEYCODE_DEL -> setNumber(DialNumber.backspace(number))
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_CALL -> call()
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    // ── Ligar ───────────────────────────────────────────────────────────────

    private fun env(uri: Uri) = CallPlan.Env(
        hasCallPermission = checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED,
        callIntentResolves = Intent(Intent.ACTION_CALL, uri).resolveActivity(packageManager) != null,
        dialIntentResolves = Intent(Intent.ACTION_DIAL, uri).resolveActivity(packageManager) != null,
        telecomAvailable = packageManager.hasSystemFeature(PackageManager.FEATURE_CONNECTION_SERVICE) &&
            getSystemService(TelecomManager::class.java) != null,
        carPhoneApp = carPhone != null,
    )

    private fun call() {
        if (!DialNumber.isDialable(number)) { status(getString(R.string.dial_type_number)); return }
        val uri = Uri.parse(DialNumber.telUri(number))
        val e = env(uri)
        if (CallPlan.shouldAskPermission(e, store.getBoolean(KEY_ASKED, false))) {
            store.edit().putBoolean(KEY_ASKED, true).apply()
            requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), REQ_CALL)
            return
        }
        val routes = CallPlan.routes(e)
        for (r in routes) {
            if (tryRoute(r, uri)) {
                AppLog.i("Telefone: chamada por $r (opções: $routes)")
                store.edit().putString(KEY_LAST, number).apply()
                return
            }
        }
        AppLog.w("Telefone: nenhum caminho para ligar (opções: $routes)")
        status(getString(R.string.dial_no_way))
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_CALL) call() // com ou sem a permissão: segue pelo melhor caminho que sobrar
    }

    private fun tryRoute(r: CallPlan.Route, uri: Uri): Boolean = try {
        when (r) {
            CallPlan.Route.CALL_INTENT -> startActivity(Intent(Intent.ACTION_CALL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            CallPlan.Route.DIAL_INTENT -> startActivity(Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            CallPlan.Route.PLACE_CALL -> {
                @Suppress("MissingPermission")
                getSystemService(TelecomManager::class.java)!!.placeCall(uri, Bundle())
                status(getString(R.string.dial_requested))
            }
            CallPlan.Route.CAR_PHONE_APP -> {
                if (!openCarPhoneApp()) throw ActivityNotFoundException()
            }
        }
        true
    } catch (t: Throwable) {
        // ActivityNotFound, SecurityException (a BYD pode bloquear) ou falha do Telecom: tenta o próximo.
        AppLog.w("Telefone: $r falhou (${t.javaClass.simpleName})")
        false
    }

    /** Abre o app de telefone do carro e deixa o número copiado para colar lá. */
    private fun openCarPhoneApp(): Boolean {
        val app = carPhone ?: return false
        val i = packageManager.getLaunchIntentForPackage(app.packageName) ?: return false
        return try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            if (DialNumber.isDialable(number)) {
                getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("número", number))
                status(getString(R.string.dial_in_car_app, app.label))
            }
            true
        } catch (t: Throwable) {
            false
        }
    }

    private fun findCarPhoneApp(): PhoneApps.App? {
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(main, 0).map {
            PhoneApps.App(it.activityInfo.packageName, it.loadLabel(packageManager).toString())
        }
        return PhoneApps.pick(apps, packageName)
    }

    private fun status(msg: String) {
        findViewById<TextView>(R.id.dialStatus).apply { text = msg; visibility = View.VISIBLE }
    }

    companion object {
        private const val REQ_CALL = 31
        private const val STATE_NUMBER = "number"
        private const val KEY_LAST = "last"
        private const val KEY_ASKED = "asked_call_permission"

        private val KEYS = listOf(
            listOf('1' to "", '2' to "ABC", '3' to "DEF"),
            listOf('4' to "GHI", '5' to "JKL", '6' to "MNO"),
            listOf('7' to "PQRS", '8' to "TUV", '9' to "WXYZ"),
            listOf('*' to "", '0' to "+", '#' to ""),
        )
    }
}
