package io.muun.apollo.presentation.ui.nfc

import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewbinding.ViewBinding
import io.muun.apollo.R
import io.muun.apollo.data.nfc.api.NfcSession
import io.muun.apollo.databinding.NfcReaderActivityBinding
import io.muun.apollo.domain.libwallet.errors.LibwalletGrpcError
import io.muun.apollo.presentation.ui.activity.extension.MuunDialog
import io.muun.apollo.presentation.ui.activity.extension.NfcReaderModeExtension
import io.muun.apollo.presentation.ui.base.BaseActivity
import io.muun.apollo.presentation.ui.base.BasePresenter
import io.muun.apollo.presentation.ui.base.BaseView
import io.muun.apollo.presentation.ui.utils.attachChildAtMm
import io.muun.apollo.presentation.ui.utils.vibrateShort
import io.muun.apollo.presentation.ui.view.MuunHeader
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

class NfcReaderActivity : BaseActivity<BasePresenter<BaseView>>() {

    companion object {
        fun getStartActivityIntent(context: Context) =
            Intent(context, NfcReaderActivity::class.java)
    }

    @Inject
    lateinit var viewModel: NfcReaderViewModel

    @Inject
    lateinit var nfcReaderModeExtension: NfcReaderModeExtension

    override fun inject() {
        component.inject(this)
    }

    override fun setUpExtensions() {
        super.setUpExtensions()
        addExtension(nfcReaderModeExtension)
    }

    private val binding: NfcReaderActivityBinding
        get() = getBinding() as NfcReaderActivityBinding

    override fun bindingInflater(): (LayoutInflater) -> ViewBinding {
        return NfcReaderActivityBinding::inflate
    }

    override fun getLayoutResource() = R.layout.nfc_reader_activity

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initializeUi()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.nfcReadState.collectLatest { state ->
                    when (state) {
                        is NfcReaderViewModel.NfcReadState.Success -> {
                            vibrateShort()
                            disableReaderMode()
                            viewModel.securityCard2faSuccess(this@NfcReaderActivity)
                        }

                        is NfcReaderViewModel.NfcReadState.Error -> {
                            handleNfcError(state)
                        }
                    }
                }
            }
        }
    }

    private fun handleNfcError(state: NfcReaderViewModel.NfcReadState.Error) {
        var message = "Error! See Debug logs or dismiss and try again\n\n${state.cause.message}"

        if (state.cause is LibwalletGrpcError) {
            val errorDetail = state.cause.errorDetail

            if (errorDetail?.developerMessage?.contains("invalid signature:") == true) {
                message = "Invalid Signature Verification! You're probably tapping with another " +
                    "(incorrect) security card"

            } else if (errorDetail?.developerMessage?.isNotEmpty() == true) {
                message = errorDetail.developerMessage
            }
        }

        Timber.e("NFC Error: ${state.cause.message}")
        viewModel.reportNfcError(state)
        MuunDialog.Builder()
            .title("Security Card Auth Required")
            .message(message)
            .build()
            .let(::showDialog)
    }

    override fun onResume() {
        super.onResume()

        viewModel.subscribeToAllSensors(this, this)

        if (!isNfcEnabled()) {
            MuunDialog.Builder()
                .title(R.string.nfc_reader_screen_title)
                .message(R.string.nfc_reader_screen_enable_nfc)
                .positiveButton(R.string.nfc_reader_screen_go_to_configs) {
                    startActivity(Intent(android.provider.Settings.ACTION_NFC_SETTINGS))
                }
                .negativeButton(R.string.cancel) {
                    finishActivity()
                }
                .build()
                .let(::showDialog)
        }

        enableReaderMode()
    }

    override fun onPause() {
        super.onPause()

        viewModel.unsubscribeFromAllSensors()

        disableReaderMode()
    }

    override fun initializeUi() {
        with(binding.header) {
            attachToActivity(this@NfcReaderActivity)
            setNavigation(MuunHeader.Navigation.BACK)
            // TODO: define this title or if we're going to need a screen title here
            showTitle(R.string.nfc_reader_screen_title)
            setElevated(false)
        }
        val container = binding.container
        val nfcSensorView = NfcSensorView(container.context)

        viewModel.getAntennaPosition()?.let {
            container.attachChildAtMm(
                childView = nfcSensorView,
                originXmm = it.first,
                originYmm = it.second,
                modifications = { pxPos ->
                    pxPos * 0.9f // -10% shift for positioning
                },
            )
        } ?: run {
            // TODO: get this information from table if not API > 34
            val textView = TextView(this).apply {
                setText(R.string.nfc_reader_screen_unknown_coordinates)
            }
            container.attachChildAtMm(
                childView = textView,
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        viewModel.onGestureDetected(event)
        return super.onTouchEvent(event)
    }

    private fun isNfcEnabled(): Boolean {
        val nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        return nfcAdapter?.isEnabled == true
    }

    override fun onNewNfcSession(nfcSession: NfcSession) {
        viewModel.onNewNfcSession(nfcSession)
    }

    private fun enableReaderMode() {
        nfcReaderModeExtension.enableReaderMode()
    }

    private fun disableReaderMode() {
        nfcReaderModeExtension.disableReaderMode()
    }
}