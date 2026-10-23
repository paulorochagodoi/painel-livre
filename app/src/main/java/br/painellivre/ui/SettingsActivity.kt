package br.painellivre.ui

import android.content.Intent
import android.os.Bundle
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
import br.painellivre.adb.SystemSetup
import br.painellivre.cluster.ClusterService
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
            setPreferencesFromResource(R.xml.preferences, rootKey)
            listOf(
                Prefs.KEY_RESTORE_DELAY, Prefs.KEY_MAP_TILT, Prefs.KEY_INSET_LEFT, Prefs.KEY_INSET_TOP,
                Prefs.KEY_INSET_RIGHT, Prefs.KEY_INSET_BOTTOM,
            ).forEach { key ->
                findPreference<EditTextPreference>(key)?.setOnBindEditTextListener {
                    it.inputType = InputType.TYPE_CLASS_NUMBER
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
            onClick("diagnostics") { startActivity(Intent(requireContext(), DiagnosticsActivity::class.java)) }
            onClick("become_home") { run { SystemSetup.becomeHome(requireContext()) } }
            onClick("restore_home") { run { SystemSetup.restoreHome(requireContext()) } }
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
