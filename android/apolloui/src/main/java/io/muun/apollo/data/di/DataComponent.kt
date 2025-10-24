package io.muun.apollo.data.di

import android.content.Context
import app_provided_data.Config
import dagger.Component
import io.muun.apollo.data.afs.MetricsProvider
import io.muun.apollo.data.apis.DriveAuthenticator
import io.muun.apollo.data.apis.DriveUploader
import io.muun.apollo.data.async.gcm.GcmMessageListenerService
import io.muun.apollo.data.async.tasks.MuunWorkerFactory
import io.muun.apollo.data.async.tasks.TaskScheduler
import io.muun.apollo.data.db.DaoManager
import io.muun.apollo.data.db.contact.ContactDao
import io.muun.apollo.data.db.operation.OperationDao
import io.muun.apollo.data.db.public_profile.PublicProfileDao
import io.muun.apollo.data.external.HoustonConfig
import io.muun.apollo.data.external.NotificationService
import io.muun.apollo.data.net.HoustonClient
import io.muun.apollo.data.net.NetworkInfoProvider
import io.muun.apollo.data.nfc.NfcBridgerFactory
import io.muun.apollo.data.os.ClipboardProvider
import io.muun.apollo.data.os.Configuration
import io.muun.apollo.data.os.execution.ExecutionTransformerFactory
import io.muun.apollo.data.os.secure_storage.SecureStorageProvider
import io.muun.apollo.data.preferences.AuthRepository
import io.muun.apollo.data.preferences.ExchangeRateWindowRepository
import io.muun.apollo.data.preferences.FeeWindowRepository
import io.muun.apollo.data.preferences.FirebaseInstallationIdRepository
import io.muun.apollo.data.preferences.KeysRepository
import io.muun.apollo.data.preferences.RepositoryRegistry
import io.muun.apollo.data.preferences.UserRepository
import io.muun.apollo.domain.ApplicationLockManager
import io.muun.apollo.domain.SignupDraftManager
import io.muun.apollo.domain.action.LogoutActions
import io.muun.apollo.domain.action.di.ActionComponent
import io.muun.apollo.domain.analytics.Analytics
import io.muun.apollo.domain.libwallet.LibwalletService
import io.muun.apollo.domain.libwallet.WalletClient
import org.bitcoinj.core.NetworkParameters
import java.util.concurrent.Executor
import javax.inject.Singleton

/**
 * Dagger Component. {@link Component}.
 * Add here:
 * - members-injection methods (e.g for classes which lifecycles are 3rd-party controlled, like
 * Android's). Example: void inject(GcmMessageListenerService service).
 * - provision methods, to expose injected or provided dependencies to other (dependent) components.
 * Example: ClipboardProvider clipboardProvider();
 */
@Singleton
@Component(modules = [DataModule::class])
interface DataComponent : ActionComponent {

    fun inject(service: GcmMessageListenerService)

    fun inject(workerFactory: MuunWorkerFactory)

    // Exposed to dependent components

    fun backgroundExecutor(): Executor

    fun transformers(): ExecutionTransformerFactory

    fun houstonClient(): HoustonClient

    fun authRepository(): AuthRepository

    fun keysRepository(): KeysRepository

    fun userRepository(): UserRepository

    fun fcmTokenRepository(): FirebaseInstallationIdRepository

    fun contactDao(): ContactDao

    fun operationDao(): OperationDao

    fun publicProfileDao(): PublicProfileDao

    fun networkParameters(): NetworkParameters

    fun taskScheduler(): TaskScheduler

    fun exchangeRateWindowRepository(): ExchangeRateWindowRepository

    fun clipboardProvider(): ClipboardProvider

    fun networkInfoProvider(): NetworkInfoProvider

    fun context(): Context

    fun expectedFeeRepository(): FeeWindowRepository

    fun configuration(): Configuration

    fun secureStorageProvider(): SecureStorageProvider

    fun logoutActions(): LogoutActions

    fun applicationLockManager(): ApplicationLockManager

    fun signupDraftManager(): SignupDraftManager

    fun houstonConfig(): HoustonConfig

    fun driveAuthenticator(): DriveAuthenticator

    fun driveUploader(): DriveUploader

    fun repositoryRegistry(): RepositoryRegistry

    fun notificationService(): NotificationService

    fun analytics(): Analytics

    fun daoManager(): DaoManager

    fun libwalletConfig(): Config

    fun walletClient(): WalletClient

    fun nfcBridgerFactory(): NfcBridgerFactory

    fun goLibwalletService(): LibwalletService

    fun metricsProvider(): MetricsProvider
}
