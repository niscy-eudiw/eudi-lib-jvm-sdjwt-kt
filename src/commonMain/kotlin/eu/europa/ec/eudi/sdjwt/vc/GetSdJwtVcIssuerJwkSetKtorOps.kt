package eu.europa.ec.eudi.sdjwt.vc

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject

interface GetSdJwtVcIssuerJwkSetKtorOps : GetSdJwtVcIssuerMetadataKtorOps {

    suspend fun HttpClient.getSdJwtIssuerKeySet(issuer: Url): JsonObject = coroutineScope {
        val metadata = getSdJwtVcIssuerMetadata(issuer)
        checkNotNull(metadata) { "Failed to obtain issuer metadata for $issuer" }
        val jwkSet = jwkSetOf(metadata)
        checkNotNull(jwkSet) { "Failed to obtain JWKSet from metadata" }

    }

    private suspend fun HttpClient.jwkSetOf(metadata: SdJwtVcIssuerMetadata): JsonObject? = coroutineScope {
        when {
            metadata.jwksUri != null -> getJWKSet(Url(metadata.jwksUri))
            else -> metadata.jwks
        }
    }

    private suspend fun HttpClient.getJWKSet(jwksUri: Url): JsonObject? {
        val httpResponse = get(jwksUri)
        return if (httpResponse.status.isSuccess()) httpResponse.body()
        else null
    }

    companion object : GetSdJwtVcIssuerJwkSetKtorOps
}

