package no.nav.btnchat.common.infrastructure

import io.ktor.auth.Authentication
import io.ktor.auth.AuthenticationPipeline
import io.ktor.auth.AuthenticationProvider
import io.ktor.auth.Principal

class MockAuthenticationProvider internal constructor(config: Configuration) : AuthenticationProvider(config) {
    internal val principal: Principal? = config.principal

    class Configuration internal constructor(name: String?) : AuthenticationProvider.Configuration(name) {
        var principal: Principal? = null

        fun build(): MockAuthenticationProvider = MockAuthenticationProvider(this)
    }
}

fun Authentication.Configuration.mock(
        name: String? = null,
        configure: MockAuthenticationProvider.Configuration.() -> Unit
) {
    val provider = MockAuthenticationProvider.Configuration(name).apply(configure).build()
    val principal = provider.principal

    provider.pipeline.intercept(AuthenticationPipeline.RequestAuthentication) { context ->
        if (principal != null) {
            context.principal(principal)
        }
    }

    register(provider)
}

