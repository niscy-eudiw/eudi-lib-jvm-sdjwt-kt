package eu.europa.ec.eudi.sdjwt.vc

import io.ktor.client.*
import io.ktor.http.*
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject

internal interface MetadataOps : GetSdJwtVcIssuerMetadataOps, GetJwkSetKtorOps {

    suspend fun HttpClient.getJWKSetFromSdJwtVcIssuerMetadata(issuer: Url): JsonObject = coroutineScope {
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

    companion object : MetadataOps
}