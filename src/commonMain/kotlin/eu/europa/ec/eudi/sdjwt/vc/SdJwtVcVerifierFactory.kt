package eu.europa.ec.eudi.sdjwt.vc

/**
 * A function to look up public keys from DIDs/DID URLs.
 */
fun interface LookupPublicKeysFromDIDDocument<out JWK> {

    /**
     * Lookup the public keys from a DID document.
     *
     * @param did the identifier of the DID document
     * @param didUrl optional DID URL, that is either absolute or relative to [did], indicating the exact public key
     * to lookup from the DID document
     *
     * @return the matching public keys or null in case lookup fails for any reason
     */
    suspend fun lookup(did: String, didUrl: String?): List<JWK>?
}


fun interface X509CertificateTrust<in X509Chain> {
    suspend fun isTrusted(chain: X509Chain): Boolean

    companion object {
        val None: X509CertificateTrust<*> = X509CertificateTrust<Any> { false }
    }
}

interface SdJwtVcVerifierFactory<out JWT, in JWK, out X509Chain> {

    /**
     * Creates a new [SdJwtVcVerifier] with SD-JWT-VC Issuer Metadata resolution enabled.
     */
    fun usingIssuerMetadata(httpClientFactory: KtorHttpClientFactory): SdJwtVcVerifier<JWT>

    /**
     * Creates a new [SdJwtVcVerifier] with X509 Certificate trust enabled.
     */
    fun usingX5c(x509CertificateTrust: X509CertificateTrust<X509Chain>): SdJwtVcVerifier<JWT>

    /**
     * Creates a new [SdJwtVcVerifier] with DID resolution enabled.
     */
    fun usingDID(didLookup: LookupPublicKeysFromDIDDocument<JWK>): SdJwtVcVerifier<JWT>

    /**
     * Creates a new [SdJwtVcVerifier] with X509 Certificate trust, and SD-JWT-VC Issuer Metadata resolution enabled.
     */
    fun usingX5cOrIssuerMetadata(
        x509CertificateTrust: X509CertificateTrust<X509Chain>,
        httpClientFactory: KtorHttpClientFactory,
    ): SdJwtVcVerifier<JWT>
}
