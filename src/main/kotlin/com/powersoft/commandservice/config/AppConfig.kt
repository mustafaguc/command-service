package com.powersoft.commandservice.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

/**
 * Application configuration
 */
@Configuration
class AppConfig {
    
    /**
     * Creates a RestClient bean for making HTTP requests
     * @return A RestClient instance
     */
    @Bean
    fun restClient(): RestClient {
        return RestClient.builder().build()
    }
}