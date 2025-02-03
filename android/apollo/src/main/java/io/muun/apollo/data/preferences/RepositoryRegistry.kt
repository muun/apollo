package io.muun.apollo.data.preferences

import io.muun.apollo.data.preferences.permission.NotificationPermissionStateRepository
import timber.log.Timber

/**
 * Welcome to RepositoryRegistry! This is class is meant to be a centralized, singleton registry
 * for all preferences repositories. Why do we need it? The idea is to invert the way of doing this
 * and to only have to manually or carefully update the list of repositories NOT to clear and that
 * way every repository (except for the exceptions hehe) will be cleared by default.
 *
 * As to why one strategy is better than the other: delete by default makes for a better security
 * default. Most data we store is tied to a session or user. If another user logins, they should not
 * have access to another users data. This way, we are forced to do sth only for the unusual case
 * (ie we want to keep that data) instead of the usual (rm -rf).
 *
 * Since we are still forced to manually keep a list of ALL repositories, we came up with the idea
 * of having a static list of all the repositories classes, and having BaseRepository (the class
 * which every repository should extend from), load the repository here upon its creation.
 * That way if we forget to add a new repository to this list, build will quickly fail upon running.
 * This is the simplest and "closest to a linter/static check" solution we could came up with.
 *
 * Note: this class is used a @Singleton. Check out its provider method in { @link DataModule }.
 */
class RepositoryRegistry {

    private val lock = this

    private val registry: Set<Class<out BaseRepository>> = setOf(
        ApiMigrationsVersionRepository::class.java,
        AuthRepository::class.java,
        BlockchainHeightRepository::class.java,
        ClientVersionRepository::class.java,
        ExchangeRateWindowRepository::class.java,
        FeeWindowRepository::class.java,
        FirebaseInstallationIdRepository::class.java,
        ForwardingPoliciesRepository::class.java,
        MinFeeRateRepository::class.java,
        KeysRepository::class.java,
        NightModeRepository::class.java,
        NotificationRepository::class.java,
        SchemaVersionRepository::class.java,
        TransactionSizeRepository::class.java,
        UserPreferencesRepository::class.java,
        UserRepository::class.java,
        FeaturesRepository::class.java,
        AppVersionRepository::class.java,
        PlayIntegrityNonceRepository::class.java,
        NotificationPermissionStateRepository::class.java,
        NotificationPermissionDeniedRepository::class.java,
        NotificationPermissionSkippedRepository::class.java,
        BackgroundTimesRepository::class.java
    )

    // Notable exceptions:
    // - FirebaseInstallationIdRepository
    // - NightModeRepository
    // - SchemaVersionRepository
    // - NotificationPermissionDeniedRepository
    // - NotificationPermissionSkippedRepository
    // They get special treatment and are not wiped on logout to avoid problems.
    // Update: technically SchemaVersionRepository could be wiped with no issues, but we feel
    // its more clear and clean to keep it and avoid wiping it (there's no privacy or security
    // issues).
    private val logoutSurvivorRepositories: Set<Class<out BaseRepository>> = setOf(
        FirebaseInstallationIdRepository::class.java,
        NightModeRepository::class.java,
        SchemaVersionRepository::class.java,
        NotificationPermissionDeniedRepository::class.java,
        NotificationPermissionSkippedRepository::class.java,
        BackgroundTimesRepository::class.java
    )

    // Note: the use of a map is critical here for 2 reasons, both of them related to memory
    // footprint. First, we want to keep exactly ONE reference to each repository, since that's all
    // we need to perform the clearing of the repository at logout (dependency injection framework
    // may instantiate more than one instance of the same repository if its not annotated with
    // @Singleton). Secondly, we take advantage of the behavior of the put() method, to "renew" our
    // reference of the repository and allow "older" references/instances to be properly garbage
    // collected. This help prevents leaks of other objects which can't be GCed because they held
    // a reference to that "old" repository instance (e.g objects where that repository was injected
    // as a dependency).
    private val loadedRepos: MutableMap<Class<out BaseRepository>, BaseRepository> =
        mutableMapOf()

    fun load(repo: BaseRepository) {
        synchronized(lock) {
            if (!isRegistered(repo)) {
                throw IllegalStateException("${repo.javaClass} is not registered in ${this.javaClass}!")
            }

            loadedRepos[repo.javaClass] = repo
            Timber.d(
                "RepositoryRegistry#load(${repo.javaClass.simpleName}). Size: ${loadedRepos.size}"
            )
        }
    }

    /**
     * The list of repositories to clear include all loaded repositories except for
     * logoutSurvivorRepositories.
     */
    fun repositoriesToClearOnLogout(): Collection<BaseRepository> =
        synchronized(lock) {
            loadedRepos.filterKeys {
                !logoutSurvivorRepositories.contains(it)
            }.values
        }

    private fun isRegistered(repository: BaseRepository) =
        registry.contains(repository.javaClass)
}