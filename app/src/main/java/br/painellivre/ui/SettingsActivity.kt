package br.painellivre.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import br.painellivre.R
import br.painellivre.adb.HomeChoice
import br.painellivre.adb.SystemSetup
import br.painellivre.cluster.ClusterService
import br.painellivre.debug.DebugProbe
import br.painellivre.util.Prefs
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().replace(android.R.id.content, Fragment()).commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    class Fragment : PreferenceFragmentCompat() {

        private val pickPhoto = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@registerForActivityResult
            try {
                requireContext().contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) { /* alguns seletores não dão permissão persistente */ }
            Prefs(requireContext()).carPhoto = uri.toString()
            Toast.makeText(requireContext(), "Foto do carro salva", Toast.LENGTH_SHORT).show()
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            Prefs(requireContext()) // Migra antes de o XML gravar os padrões.
            setPreferencesFromResource(R.xml.preferences, rootKey)
            listOf(Prefs.KEY_RESTORE_DELAY, Prefs.KEY_MAP_TILT).forEach { key ->
                findPreference<EditTextPreference>(key)?.setOnBindEditTextListener {
                    it.inputType = InputType.TYPE_CLASS_NUMBER
                }
            }
            // Ajuste fino da área segura: aceita negativo (aproxima da borda).
            listOf(Prefs.KEY_INSET_LEFT, Prefs.KEY_INSET_TOP, Prefs.KEY_INSET_RIGHT, Prefs.KEY_INSET_BOTTOM).forEach { key ->
                findPreference<EditTextPreference>(key)?.setOnBindEditTextListener {
                    it.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
                }
            }
            onClick("car_photo_pick") { pickPhoto.launch(arrayOf("image/*")) }
            onClick("car_photo_clear") {
                Prefs(requireContext()).carPhoto = null
                Toast.makeText(requireContext(), "Ilustração do carro de volta", Toast.LENGTH_SHORT).show()
            }
            onClick("simulate") {
                ClusterService.start(requireContext(), ClusterService.ACTION_SIMULATE)
                Toast.makeText(requireContext(), "Simulação ligada/desligada", Toast.LENGTH_SHORT).show()
            }
            onClick("grant_all") { run { SystemSetup.grantAll(requireContext()) } }
            findPreference<Preference>(Prefs.KEY_DEBUG_MODE)?.setOnPreferenceChangeListener { _, v ->
                DebugProbe.sync(requireContext(), v as Boolean)
                true
            }
            onClick("debug_open") { startActivity(Intent(requireContext(), DebugActivity::class.java)) }
            onClick("diagnostics") { startActivity(Intent(requireContext(), DiagnosticsActivity::class.java)) }
            onClick("become_home") { becomeHome() }
            onClick("restore_home") { run { SystemSetup.restoreHome(requireContext()) } }
        }

        /** Sem ADB, cai na tela "Tela inicial" do Android ou no seletor com "Sempre" (issue #7). */
        private fun becomeHome() {
            viewLifecycleOwner.lifecycleScope.launch {
                val ctx = requireContext()
                val r = SystemSetup.becomeHome(ctx)
                val dialog = AlertDialog.Builder(ctx).setMessage(r.message)
                when (r.fallback) {
                    HomeChoice.Step.HOME_SETTINGS -> dialog.setPositiveButton(android.R.string.ok) { _, _ ->
                        openOr(Intent(Settings.ACTION_HOME_SETTINGS), homeIntent())
                    }
                    HomeChoice.Step.CHOOSER -> dialog.setPositiveButton(android.R.string.ok) { _, _ -> openOr(homeIntent(), null) }
                    else -> dialog.setPositiveButton(android.R.string.ok, null)
                }
                dialog.show()
            }
        }

        private fun homeIntent() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

        private fun openOr(first: Intent, second: Intent?) {
            try {
                startActivity(first.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (t: Throwable) {
                if (second != null) runCatching { startActivity(second.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }

        private fun onClick(key: String, action: () -> Unit) {
            findPreference<Preference>(key)?.setOnPreferenceClickListener { action(); true }
        }

        private fun run(block: suspend () -> String) {
            viewLifecycleOwner.lifecycleScope.launch {
                val out = block()
                AlertDialog.Builder(requireContext()).setMessage(out).setPositiveButton(android.R.string.ok, null).show()
            }
        }
    }
}
