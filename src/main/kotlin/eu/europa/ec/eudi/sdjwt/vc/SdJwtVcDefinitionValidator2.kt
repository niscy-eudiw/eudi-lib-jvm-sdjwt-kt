/*
 * Copyright (c) 2023 European Commission
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package eu.europa.ec.eudi.sdjwt.vc

import eu.europa.ec.eudi.sdjwt.*
import eu.europa.ec.eudi.sdjwt.SdJwtPresentationOps.Companion.disclosuresPerClaimVisitor
import eu.europa.ec.eudi.sdjwt.dsl.Disclosable
import eu.europa.ec.eudi.sdjwt.dsl.DisclosableArray
import eu.europa.ec.eudi.sdjwt.dsl.DisclosableObject
import eu.europa.ec.eudi.sdjwt.dsl.DisclosableValue
import eu.europa.ec.eudi.sdjwt.dsl.sdjwt.def.AttributeMetadata
import eu.europa.ec.eudi.sdjwt.dsl.sdjwt.def.SdJwtDefinition
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/**
 * Validates a SD-JWT-VC credential against the [SdJwtDefinition] of this credential.
 *
 * The validation can be performed by a wallet, right after issued the credential. In this case,
 * the full list of [Disclosure] it is assumed, as provided by the SD-JWT-VC issuer.
 *
 * In addition, the validation can be performed by a verifier, right after receiving a presentation
 * of the SD-JWT-VC from the wallet. In this case, the list of [Disclosure] can be even empty
 */
class SdJwtVcDefinitionValidator2 private constructor(
    private val disclosuresPerClaimPath: DisclosuresPerClaimPath,
    private val definition: SdJwtDefinition,
) {
    private val allErrors = mutableListOf<SdJwtDefinitionCredentialValidationError>()

    private val validateObject: DeepRecursiveFunction<Triple<ClaimPath?, JsonObject, DisclosableObject<String, AttributeMetadata>>, Unit> =
        DeepRecursiveFunction { (parent, current, definition) ->
            val unknownAttributes = current.keys - definition.content.keys
            allErrors.addAll(
                unknownAttributes.map {
                    val unknownAttributeClaimPath = parent?.claim(it) ?: ClaimPath.claim(it)
                    SdJwtDefinitionCredentialValidationError.UnknownObjectAttribute(unknownAttributeClaimPath)
                },
            )

            // iterate through the known attributes and validate them
            current.filterKeys { it !in unknownAttributes }.forEach { (attributeName, attributeValue) ->
                val attributeClaimPath = parent?.claim(attributeName) ?: ClaimPath.claim(attributeName)
                val attributeDefinition = checkNotNull(definition.content[attributeName]) { "can find definition for $attributeClaimPath" }

                // check disclosability
                val requiresDisclosures = run {
                    val parentDisclosures = parent?.let {
                        checkNotNull(disclosuresPerClaimPath[it]) { "can find disclosures for $it" }
                    }.orEmpty()
                    val propertyDisclosures = checkNotNull(disclosuresPerClaimPath[attributeClaimPath]) {
                        "can find disclosures for $attributeClaimPath"
                    }

                    // if property requires more disclosures than its parent, it is selectively disclosed
                    propertyDisclosures.size > parentDisclosures.size
                }
                if (
                    (attributeDefinition is Disclosable.AlwaysSelectively<*> && !requiresDisclosures) ||
                    (attributeDefinition is Disclosable.NeverSelectively<*> && requiresDisclosures)
                ) {
                    allErrors.add(SdJwtDefinitionCredentialValidationError.IncorrectlyDisclosedAttribute(attributeClaimPath))
                }

                // check type and recurse as needed
                // proceed only when attributeValue is not JsonNull
                if (attributeValue !is JsonNull) {
                    when (val disclosableValue = attributeDefinition.value) {
                        is DisclosableValue.Obj -> {
                            if (attributeValue is JsonObject) {
                                callRecursive(Triple(attributeClaimPath, attributeValue, disclosableValue.value))
                            } else {
                                allErrors.add(SdJwtDefinitionCredentialValidationError.WrongAttributeType(attributeClaimPath))
                            }
                        }

                        is DisclosableValue.Arr -> {
                            if (attributeValue is JsonArray) {
                                validateArray.callRecursive(Triple(attributeClaimPath, attributeValue, disclosableValue.value))
                            } else {
                                allErrors.add(SdJwtDefinitionCredentialValidationError.WrongAttributeType(attributeClaimPath))
                            }
                        }

                        is DisclosableValue.Id -> {
                            // nothing more we can check
                        }
                    }
                }
            }
        }

    private val validateArray: DeepRecursiveFunction<Triple<ClaimPath, JsonArray, DisclosableArray<String, AttributeMetadata>>, Unit> =
        DeepRecursiveFunction { (parent, current, definition) ->
            // proceed only when array is uniform
            if (definition.content.size == 1) {
                val elementDefinition = definition.content.first()

                current.withIndex().forEach { (elementIndex, elementValue) ->
                    val elementClaimPath = parent.arrayElement(elementIndex)

                    // check disclosability
                    val requiresDisclosures = run {
                        val parentDisclosures = checkNotNull(disclosuresPerClaimPath[parent]) {
                            "can find disclosures for $parent"
                        }
                        val elementDisclosures = checkNotNull(disclosuresPerClaimPath[elementClaimPath]) {
                            "can find disclosures for $elementClaimPath"
                        }

                        // if element requires more disclosures than its parent, it is selectively disclosed
                        elementDisclosures.size > parentDisclosures.size
                    }
                    if (
                        (elementDefinition is Disclosable.AlwaysSelectively<*> && !requiresDisclosures) ||
                        (elementDefinition is Disclosable.NeverSelectively<*> && requiresDisclosures)
                    ) {
                        allErrors.add(SdJwtDefinitionCredentialValidationError.IncorrectlyDisclosedAttribute(elementClaimPath))
                    }

                    // check type and recurse as needed
                    // proceed only when elementValue is not JsonNull
                    if (elementValue !is JsonNull) {
                        when (val disclosableValue = elementDefinition.value) {
                            is DisclosableValue.Obj -> {
                                if (elementValue is JsonObject) {
                                    validateObject.callRecursive(Triple(elementClaimPath, elementValue, disclosableValue.value))
                                } else {
                                    allErrors.add(SdJwtDefinitionCredentialValidationError.WrongAttributeType(elementClaimPath))
                                }
                            }

                            is DisclosableValue.Arr -> {
                                if (elementValue is JsonArray) {
                                    callRecursive(Triple(elementClaimPath, elementValue, disclosableValue.value))
                                } else {
                                    allErrors.add(SdJwtDefinitionCredentialValidationError.WrongAttributeType(elementClaimPath))
                                }
                            }

                            is DisclosableValue.Id -> {
                                // nothing more we can check
                            }
                        }
                    }
                }
            }
        }

    private fun validate(processedPayload: JsonObject): List<SdJwtDefinitionCredentialValidationError> {
        val processedPayloadWithoutWellKnown = JsonObject(processedPayload - wellKnownClaims)
        validateObject(Triple(null, processedPayloadWithoutWellKnown, definition))
        return allErrors
    }

    companion object {

        // SdJwtSpec.CLAIM_SD, SdJwtSpec.CLAIM_SD_ALG, are not included because we work with processed payloads
        // TODO: check whether other well known claims must be added
        // TODO: verify that when present, the well known claims are never selectively disclosed
        private val wellKnownClaims: Set<String> = setOf(
            RFC7519.ISSUER,
            RFC7519.SUBJECT,
            RFC7519.AUDIENCE,
            RFC7519.EXPIRATION_TIME,
            RFC7519.NOT_BEFORE,
            RFC7519.ISSUED_AT,
            RFC7519.JWT_ID,
            SdJwtVcSpec.VCT,
            SdJwtVcSpec.VCT_INTEGRITY,
        )

        /**
         * Validates a SD-JWT-VC credential against the [SdJwtDefinition] of this credential.
         *
         * The validation can be performed by a wallet, right after issued the credential. In this case,
         * the full list of [disclosures] it is assumed, as provided by the SD-JWT-VC issuer.
         *
         * In addition, the validation can be performed by a verifier, right after receiving a presentation
         * of the SD-JWT-VC from the wallet. In this case, the list of [disclosures] can be even empty
         *
         * @param jwtPayload The JWT payload of a presented SD-JWT-VC
         * @param disclosures The list of disclosures related to the SD-JWT-VC.
         * @param definition the definition of the SD-JWT-VC credential against which the given [jwtPayload] and [disclosures]
         * will be validated
         */
        fun validate(jwtPayload: JsonObject, disclosures: List<Disclosure>, definition: SdJwtDefinition): SdJwtDefinitionValidationResult =
            validate(UnsignedSdJwt(jwtPayload, disclosures), definition)

        /**
         * Validates a SD-JWT-VC credential against the [SdJwtDefinition] of this credential.
         *
         * The validation can be performed by a wallet, right after issued the credential. In this case,
         * the full list of [UnsignedSdJwt.disclosures] it is assumed, as provided by the SD-JWT-VC issuer.
         *
         * In addition, the validation can be performed by a verifier, right after receiving a presentation
         * of the SD-JWT-VC from the wallet. In this case, the list of [UnsignedSdJwt.disclosures] can be even empty
         *
         * @param sdJwt The JWT payload and the disclosures of a presented SD-JWT-VC
         * @param definition the definition of the SD-JWT-VC credential against which the given [sdJwt] will be validated
         */
        fun validate(sdJwt: UnsignedSdJwt, definition: SdJwtDefinition): SdJwtDefinitionValidationResult {
            val (processedPayload, disclosuresPerClaimPath) = runCatching {
                val disclosuresPerClaimPath = mutableMapOf<ClaimPath, List<Disclosure>>()
                val visitor = disclosuresPerClaimVisitor(disclosuresPerClaimPath)
                sdJwt.recreateClaims(visitor) to disclosuresPerClaimPath.toMap()
            }.getOrElse {
                return SdJwtDefinitionValidationResult.Invalid(SdJwtDefinitionCredentialValidationError.DisclosureInconsistencies(it))
            }

            val errors = SdJwtVcDefinitionValidator2(disclosuresPerClaimPath, definition).validate(processedPayload)
            return if (errors.isEmpty()) {
                SdJwtDefinitionValidationResult.Valid
            } else {
                SdJwtDefinitionValidationResult.Invalid(errors)
            }
        }
    }
}
