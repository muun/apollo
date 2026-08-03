package io.muun.apollo.presentation.ui.debug.securekv

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import io.muun.apollo.data.os.secure_storage.SecureStorageProvider
import io.muun.apollo.data.secure_key_value_storage.SecureKeyValueStorageRepository
import io.muun.apollo.databinding.ActivityDebugSecureKeyValueStorageBinding
import io.muun.apollo.presentation.app.ApolloApplication
import io.muun.apollo.presentation.app.Navigator
import io.muun.apollo.presentation.ui.base.di.ActivityComponent
import io.muun.apollo.presentation.ui.utils.setWindowInsetsCompat
import io.muun.apollo.presentation.ui.view.MuunHeader
import javax.inject.Inject

class DebugSecureKeyValueStorageActivity : AppCompatActivity() {

    companion object {
        private const val SMOKE_KEY = "libwallet-bridge-smoketest"

        fun getStartActivityIntent(context: Context): Intent {
            return Intent(context, DebugSecureKeyValueStorageActivity::class.java)
        }
    }

    private lateinit var binding: ActivityDebugSecureKeyValueStorageBinding

    private val component: ActivityComponent
        get() {
            return (application as ApolloApplication).getApplicationComponent().activityComponent()
        }

    @Inject
    lateinit var secureKeyValueStorageRepository: SecureKeyValueStorageRepository

    @Inject
    lateinit var secureStorageProvider: SecureStorageProvider

    @Inject
    lateinit var navigator: Navigator

    override fun onCreate(savedInstanceState: Bundle?) {
        setWindowInsetsCompat()
        super.onCreate(savedInstanceState)

        component.inject(this)

        binding = ActivityDebugSecureKeyValueStorageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setUpHeader()
        initializeUi()
    }

    private fun setUpHeader() {
        binding.header.attachToActivity(this)
        binding.header.setNavigation(MuunHeader.Navigation.BACK)
        binding.header.showTitle("SecureKeyValueStorage debug")
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun initializeUi() {
        binding.debugButtonSecureKvPut.setOnClickListener {
            try {
                val payload = "hello-from-libwallet-${System.currentTimeMillis()}".toByteArray()
                secureKeyValueStorageRepository.put(SMOKE_KEY, payload)
                toast("PUT OK: key=$SMOKE_KEY")
            } catch (e: Throwable) {
                toast("PUT failed: ${e.message}")
            }
        }

        binding.debugButtonSecureKvGet.setOnClickListener {
            try {
                val secret = secureKeyValueStorageRepository.get(SMOKE_KEY)
                secret.withSecret { bytes ->
                    toast("GET OK: ${bytes.size} bytes")
                }
            } catch (e: Throwable) {
                toast("GET failed: ${e.message}")
            }
        }

        binding.debugButtonSecureKvDelete.setOnClickListener {
            try {
                secureKeyValueStorageRepository.delete(SMOKE_KEY)
                toast("DELETE OK: key=$SMOKE_KEY")
            } catch (e: Throwable) {
                toast("DELETE failed: ${e.message}")
            }
        }

        binding.debugButtonSecureKvWipe.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Wipe secure storage?")
                .setMessage("Deletes ALL keys in the native secure storage. Cannot be undone.")
                .setPositiveButton("Wipe") { _, _ ->
                    try {
                        secureKeyValueStorageRepository.wipe()
                        toast("WIPE OK")
                    } catch (e: Throwable) {
                        toast("WIPE failed: ${e.message}")
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.debugButtonExportAuditTrail.setOnClickListener {
            val trail = secureStorageProvider.debugSnapshot().auditTrail
            val text = trail.joinToString("\n").ifBlank { "(empty audit trail)" }
            navigator.shareText(this, text, "Audit trail")
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
