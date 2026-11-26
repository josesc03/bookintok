package com.bookintok.api.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class AppConfig {

    /** Injected instead of calling Instant.now() directly, so time is controllable in tests. */
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun openApi(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("BookinTok API")
                .version("v1")
                .description("Users, books, exchanges and ratings for the BookinTok book-swapping app."),
        )
        .components(
            Components().addSecuritySchemes(
                "firebase",
                SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"),
            ),
        )
        .addSecurityItem(SecurityRequirement().addList("firebase"))
}
