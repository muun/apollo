package io.muun.apollo.data.preferences

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
 * of having a static list of all the repositories clases, and having BaseRepository (the class
 * which every repository should extend from), load the repository here upon its creation.
 * That way if we forget to add a new repository to this list, build will quickly fail upon running.
 * This is the simplest and "closest to a linter/static check" solution we could came up with.
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
    )

    // Notable exceptions:
    // - FirebaseInstallationIdRepository
    // - NightModeRepository
    // - SchemaVersionRepository
    // They get special treatment and are not wiped on logout to avoid problems.
    // Update: technically SchemaVersionRepository could be wiped with no issues, but we feel
    // its more clear and clean to keep it and avoid wiping it (there's no privacy or security
    // issues).
    private val logoutSurvivorRepositories: Set<Class<out BaseRepository>> = setOf(
        FirebaseInstallationIdRepository::class.java,
        NightModeRepository::class.java,
        SchemaVersionRepository::class.java
    )

    private val loadedRepositories: MutableSet<BaseRepository> = mutableSetOf()

    fun load(repo: BaseRepository) {
        synchronized(lock) {
            if (!isRegistered(repo)) {
                throw IllegalStateException("${repo.javaClass} is not registered in ${this.javaClass}!")
            }

            loadedRepositories.add(repo)
        }
    }

    /**
     * The list of repositories to clear include all loaded repositories except for
     * logoutSurvivorRepositories.
     */
    fun repositoriesToClear(): List<BaseRepository> =
        synchronized(lock) {
            loadedRepositories.filter {
                !logoutSurvivorRepositories.contains(it.javaClass)
            }
        }

    private fun isRegistered(repository: BaseRepository) =
        registry.contains(repository.javaClass)
}