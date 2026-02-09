package io.muun.apollo.presentation.ui.diagnostic

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import io.muun.apollo.databinding.ActivityDiagnosticBinding
import io.muun.apollo.domain.libwallet.LibwalletClient
import io.muun.apollo.presentation.app.ApolloApplication
import io.muun.apollo.presentation.ui.base.di.ActivityComponent
import rx.android.schedulers.AndroidSchedulers
import javax.inject.Inject


class DiagnosticActivity : AppCompatActivity() {
    companion object {
        fun getStartActivityIntent(context: Context) =
            Intent(context, DiagnosticActivity::class.java)
    }

    private lateinit var binding: ActivityDiagnosticBinding
    private val component: ActivityComponent
        get() {
            return (application as ApolloApplication).getApplicationComponent().activityComponent()
        }

    @Inject
    lateinit var libwalletClient: LibwalletClient

    private var savedSessionId: String? = null
    private var currentValue: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        component.inject(this)

        binding = ActivityDiagnosticBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeUi()
    }

    @SuppressLint("SetTextI18n")
    private fun initializeUi() {
        binding.scanButton.isEnabled = false
        binding.sendButton.isEnabled = false

        val looper = Looper.myLooper()
        binding.beginButton.setOnClickListener {
            libwalletClient.startDiagnosticSession()
                .observeOn(AndroidSchedulers.from(looper))
                .map { sessionId ->
                    savedSessionId = sessionId
                    binding.statusText.text = "Session id: $sessionId."
                    binding.scanButton.isEnabled = true
                }
                .subscribe()
        }

        binding.scanButton.setOnClickListener {
            savedSessionId?.let { sessionId ->
                libwalletClient.scanForUtxos(sessionId)
                    .observeOn(AndroidSchedulers.from(looper))
                    .doOnError { throwable ->
                        Toast.makeText(
                            baseContext,
                            "ERROR: ${throwable.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .doOnNext { update ->
                        if (update.scanComplete) {
                            binding.statusText.text = "Completed Scan."
                            binding.logTextBox.text =
                                "${binding.logTextBox.text}\nPROCESS COMPLETE\n"
                            binding.sendButton.isEnabled = true
                        }
                        update.amount?.let { amount ->
                            currentValue += amount
                            binding.logTextBox.text =
                                "${binding.logTextBox.text}\nFOUND $amount SATS @ ${update?.address}"
                            binding.logScrollview.fullScroll(View.FOCUS_DOWN)
                        }
                    }
                    .subscribe()
            }
        }

        binding.sendButton.setOnClickListener {
            savedSessionId?.let { sessionId ->
                libwalletClient.submitDiagnosticLog(sessionId)
                    .observeOn(AndroidSchedulers.from(looper))
                    .doOnError { throwable ->
                        Toast.makeText(
                            baseContext,
                            "ERROR: ${throwable.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .doOnNext {
                        binding.statusText.text = "Diagnostic sent. Thank you!"
                        binding.scanButton.isEnabled = false
                        binding.sendButton.isEnabled = false
                    }
                    .subscribe()
            }
        }
    }
}
