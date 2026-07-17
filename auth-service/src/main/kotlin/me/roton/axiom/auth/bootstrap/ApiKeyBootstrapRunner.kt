package me.roton.axiom.auth.bootstrap

import me.roton.axiom.auth.repository.ApiKeyRepository
import me.roton.axiom.auth.service.ApiKeyService
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component

// On the first run of service we dont have any keys, so this generates one default key and logs it ONCE
@Component
class ApiKeyBootstrapRunner(
    private val apiKeyRepository: ApiKeyRepository,
    private val apiKeyService: ApiKeyService
) : CommandLineRunner {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun run(args: Array<String>) {
        if (apiKeyRepository.existsByActiveTrue()) {
            return // a key already exists somewhere — nothing to bootstrap
        }

        val generated = apiKeyService.createKey(companyName = "default")

        logger.warn(
            """
            |
            |=================================================================
            | No API keys existed — generated a bootstrap key.
            | THIS WILL NEVER BE SHOWN AGAIN. Store it now.
            |
            | company: ${generated.companyName}
            | key id:  ${generated.apiKeyId}
            | key:     ${generated.rawKey}
            |=================================================================
            |
            """.trimMargin()
        )
    }
}