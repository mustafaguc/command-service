package com.powersoft.commandservice.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.servers.Server
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Configuration for OpenAPI documentation
 */
@Configuration
class OpenApiConfig {

    @Value("\${spring.application.name}")
    private lateinit var applicationName: String

    @Bean
    fun openAPI(): OpenAPI {
        return OpenAPI()
            .info(
                Info()
                    .title("$applicationName API")
                    .description("API for executing commands and managing command jobs")
                    .version("v1.0.0")
                    .contact(
                        Contact()
                            .name("PowerSoft")
                            .email("support@powersoft.com")
                    )
                    .license(
                        License()
                            .name("Apache 2.0")
                            .url("https://www.apache.org/licenses/LICENSE-2.0")
                    )
            )
            .addServersItem(
                Server()
                    .url("/")
                    .description("Default Server URL")
            )
    }
}