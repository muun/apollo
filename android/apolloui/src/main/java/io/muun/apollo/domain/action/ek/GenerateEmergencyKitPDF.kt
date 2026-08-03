package io.muun.apollo.domain.action.ek

import io.muun.apollo.data.fs.FileCache
import io.muun.apollo.data.os.execution.ExecutionTransformerFactory
import io.muun.apollo.data.preferences.KeysRepository
import io.muun.apollo.data.preferences.UserRepository
import io.muun.apollo.domain.action.base.BaseAsyncAction0
import io.muun.apollo.domain.libwallet.LibwalletClient
import io.muun.apollo.domain.model.EmergencyKitExport
import io.muun.apollo.domain.model.GeneratedEmergencyKitInfo
import io.muun.apollo.domain.utils.EK_CHILD_GO_COMPONENTS_RENDERING
import io.muun.apollo.domain.utils.EK_CHILD_GO_CREATE_AND_SAVE_ON_DISK
import io.muun.apollo.domain.utils.EK_CHILD_GO_EMBED_METADATA
import io.muun.apollo.domain.utils.EK_CHILD_GO_LOAD_TRANSLATIONS
import io.muun.apollo.domain.utils.EK_CHILD_GO_REGISTER_FONTS
import io.muun.apollo.domain.utils.EK_CHILD_GO_REGISTER_IMAGES
import io.muun.apollo.domain.utils.EK_CHILD_GO_TOTAL_HEAP_ALLOCATED
import io.muun.apollo.domain.utils.EK_CHILD_GO_TOTAL_INSIDE_GO
import io.muun.apollo.domain.utils.EK_CHILD_GO_TOTAL_OBJECTS_ALLOCATED
import io.muun.apollo.domain.utils.EK_CHILD_MUUN_FINGERPRINT
import io.muun.apollo.domain.utils.EK_CHILD_MUUN_KEY
import io.muun.apollo.domain.utils.EK_CHILD_RC_CHECKSUM
import io.muun.apollo.domain.utils.EK_CHILD_USER_FINGERPRINT
import io.muun.apollo.domain.utils.EK_CHILD_USER_KEY
import io.muun.apollo.domain.utils.TimeTracker
import io.muun.apollo.domain.utils.Trace
import io.muun.apollo.domain.utils.TraceLabel
import io.muun.common.crypto.ChallengeType
import rpc.WalletServiceOuterClass.GenerateEmergencyKitPDFResponse
import rx.Observable
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GenerateEmergencyKitPDF @Inject constructor(
    private val userRepository: UserRepository,
    private val keysRepository: KeysRepository,
    private val reportEmergencyKitExported: ReportEmergencyKitExportedAction,
    private val transformerFactory: ExecutionTransformerFactory,
    private val fileCache: FileCache,
    private val getOrCreateEncryptedBasePrivateKeyAction: GetOrCreateEncryptedBasePrivateKeyAction,
    private val libwalletClient: LibwalletClient,
    private val timeTracker: TimeTracker,
) : BaseAsyncAction0<GeneratedEmergencyKitInfo>() {

    inner class RequiredData(
        val userKey: String,
        val userFingerprint: String,
        val muunKey: String,
        val muunFingerprint: String,
        val rcChecksum: String,
    )

    override fun action(): Observable<GeneratedEmergencyKitInfo> = Observable.defer {
        val e2eTrace = timeTracker.start(TraceLabel.EK_E2E_NEW_KIT_GENERATION)

        watchData().first()
            .map { data ->
                val trace = timeTracker.start(TraceLabel.EK_NEW_PDF_GENERATION)
                try {
                    generatePDF(data, trace)
                } finally {
                    trace.finish()
                }
            }
            .doOnNext { ek ->
                e2eTrace.finish()
                val export = EmergencyKitExport(
                    ek,
                    false,
                    EmergencyKitExport.Method.UNKNOWN
                )

                // NOTE:
                // Rather than use `run()`, we subscribe to this action() in background to avoid
                // competing with other callers for the Action concurrency check.
                // Remember: this is a fire-and-forget call
                reportEmergencyKitExported.action(export)
                    .subscribeOn(transformerFactory.backgroundScheduler)
                    .subscribe({}, { error ->
                        Timber.i("Error while reportEmergencyKitExported")
                        Timber.e(error)
                    })
            }
    }

    private fun generatePDF(data: RequiredData, trace: Trace): GeneratedEmergencyKitInfo {
        // Clear previously saved files:
        fileCache.delete(FileCache.Entry.EMERGENCY_KIT_NO_META)
        fileCache.delete(FileCache.Entry.EMERGENCY_KIT)

        val outputPath = fileCache.getFile(FileCache.Entry.EMERGENCY_KIT).absolutePath

        val result = libwalletClient.generateEmergencyKitPDF(
            data = data,
            outputPath = outputPath,
            language = Locale.getDefault().language
        )

        addRenderProfiling(trace, result)

        userRepository.storeEmergencyKitVerificationCode(result.verificationCode)

        return GeneratedEmergencyKitInfo(
            result.verificationCode,
            result.version
        )
    }

    /**
     * Forward libwallet's per-stage render profiling as children of the PDF-generation trace, so
     * prod telemetry can validate the offline profiling against real devices.
     */
    private fun addRenderProfiling(trace: Trace, result: GenerateEmergencyKitPDFResponse) {
        val profiling = result.profiling
        trace.addChild(EK_CHILD_GO_LOAD_TRANSLATIONS, profiling.loadTranslationsMs)
        trace.addChild(EK_CHILD_GO_REGISTER_FONTS, profiling.registerFontsMs)
        trace.addChild(EK_CHILD_GO_REGISTER_IMAGES, profiling.registerImagesMs)
        trace.addChild(EK_CHILD_GO_COMPONENTS_RENDERING, profiling.componentsRenderingMs)
        trace.addChild(EK_CHILD_GO_CREATE_AND_SAVE_ON_DISK, profiling.createAndSaveOnDiskMs)
        trace.addChild(EK_CHILD_GO_TOTAL_HEAP_ALLOCATED, profiling.totalHeapAllocatedBytes)
        trace.addChild(EK_CHILD_GO_TOTAL_OBJECTS_ALLOCATED, profiling.totalObjectsAllocated)
        trace.addChild(EK_CHILD_GO_EMBED_METADATA, profiling.embedMetadataMs)
        trace.addChild(EK_CHILD_GO_TOTAL_INSIDE_GO, profiling.totalInsideGoMs)
    }

    private fun watchData(): Observable<RequiredData> {
        val trace = timeTracker.start(TraceLabel.EK_NEW_DATA_FETCHING)
        val tUserKey = trace.child(EK_CHILD_USER_KEY)
        val tUserFp = trace.child(EK_CHILD_USER_FINGERPRINT)
        val tMuunKey = trace.child(EK_CHILD_MUUN_KEY)
        val tMuunFp = trace.child(EK_CHILD_MUUN_FINGERPRINT)
        val tRcChecksum = trace.child(EK_CHILD_RC_CHECKSUM)

        val challengePublicKey =
            keysRepository.getChallengePublicKey(ChallengeType.RECOVERY_CODE)

        return Observable.zip(
            getEncryptedBasePrivateKey().doOnNext { tUserKey.finish() },
            keysRepository.userKeyFingerprint.doOnNext { tUserFp.finish() },
            keysRepository.encryptedMuunPrivateKey.doOnNext { tMuunKey.finish() },
            keysRepository.muunKeyFingerprint.doOnNext { tMuunFp.finish() },
            challengePublicKey.map { it.checksum }.doOnNext { tRcChecksum.finish() },
            ::RequiredData
        ).doOnNext {
            trace.finish()
        }
    }

    private fun getEncryptedBasePrivateKey(): Observable<String> {
        return getOrCreateEncryptedBasePrivateKeyAction.action()
    }
}