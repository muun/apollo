package io.muun.apollo.presentation.app.di.modules

import dagger.Binds
import dagger.MapKey
import dagger.Module
import dagger.multibindings.IntoMap
import dagger.multibindings.Multibinds
import io.muun.apollo.presentation.app.startup.EmojiCompatInitializer
import io.muun.apollo.presentation.app.startup.Initializer
import kotlin.reflect.KClass

@MustBeDocumented
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MapKey
private annotation class AppStartupInitializerKey(
    val value: KClass<out Initializer>
)

@Module
interface StartupModule {

    @Multibinds
    fun multibindAppStartupInitializer(): @JvmSuppressWildcards Map<Class<out Initializer>, Initializer>

    @Binds
    @IntoMap
    @AppStartupInitializerKey(EmojiCompatInitializer::class)
    fun bindEmojiCompatAppStartupRunner(impl: EmojiCompatInitializer): Initializer
}
