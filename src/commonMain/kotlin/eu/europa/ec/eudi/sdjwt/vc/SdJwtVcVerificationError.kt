package eu.europa.ec.eudi.sdjwt.vc

/**
 * SD-JWT VC verification errors.
 */
sealed interface SdJwtVcVerificationError {

    /**
     * Verification errors regarding the resolution of the Issuer's key or the verification of the Issuer's signature.
     */
    sealed interface IssuerKeyVerificationError : SdJwtVcVerificationError {

        /**
         * Indicates the key verification methods is not supported.
         *
         * @property method one of 'issuer-metadata', 'x5c', or 'did'
         */
        data class UnsupportedVerificationMethod(val method: String) : IssuerKeyVerificationError

        /**
         * Indicates an error while trying to resolve the Issuer's metadata.
         */
        data class IssuerMetadataResolutionFailure(val cause: Throwable? = null) : IssuerKeyVerificationError

        /**
         * Indicates the leaf certificate of the 'x5c' certificate chain is not trusted.
         */
        data class UntrustedIssuerCertificate(val reason: String? = null) : IssuerKeyVerificationError

        /**
         * DID resolution fail.
         */
        data class DIDLookupFailure(val message: String, val cause: Throwable? = null) : IssuerKeyVerificationError

        /**
         * Indicates a key source for the Issuer could not be determined.
         */
        data object CannotDetermineIssuerVerificationMethod : IssuerKeyVerificationError
    }
}