package com.lytovka.jwt.service

import com.lytovka.jwt.configuration.RequestContext
import com.lytovka.jwt.dto.JwtTokenResponse
import com.lytovka.jwt.dto.JwtValidationResponse
import com.lytovka.jwt.model.Header
import com.lytovka.jwt.model.JwtTokenBuilder
import com.lytovka.jwt.model.Payload
import com.lytovka.jwt.utils.Base64
import com.lytovka.jwt.utils.Signature
import com.nimbusds.jose.JWSAlgorithm
import org.springframework.stereotype.Service
import java.security.interfaces.RSAPublicKey
import java.util.Date
import java.util.UUID

@Service
class JwtService(private val requestContext: RequestContext, private val keyService: KeyService) {
    private val kid = "my-key-id"

    fun createJwt(): JwtTokenResponse {
        val keyPair = keyService.loadKeyPair()

        val header = buildHeader()
        val payload = buildPayload()

        val jwtToken = JwtTokenBuilder()
            .setHeader(header)
            .setPayload(payload)
            .signWith(keyPair.private)
            .build()

        return JwtTokenResponse(accessToken = jwtToken.toString(), expiresIn = payload.getExpiresIn())
    }

    fun validateJwt(): JwtValidationResponse {
        val keyPair = keyService.loadKeyPair()
        val jwtToken = requestContext.httpHeaders?.getFirst("Authorization") ?: throw IllegalStateException("Authorization header is not set")
        val jwt = JwtTokenBuilder().parse(jwtToken).build()
        val isValid = Signature.Verifier.verifyRSA(jwt.getUnprotectedToken(), Base64.urlDecode(jwt.signature!!), keyPair.public as RSAPublicKey)
        return JwtValidationResponse(isValid = isValid)
    }

    private fun buildHeader(): Header {
        return Header(
            alg = JWSAlgorithm.RS256.name,
            kid = kid,
            typ = "JWT",
        )
    }

    private fun buildPayload(): Payload {
        val issuer = getIssuerUrl()
        val date = Date().time
        return Payload(
            sub = "1234567890",
            role = "user",
            iss = issuer,
            aud = "https://example.com",
            exp = (date + 60 * 60 * 1000) / 1000,
            iat = date / 1000,
            jti = UUID.randomUUID().toString(),
        )
    }

    /**
     * This method is tied to using `ngrok` locally. The issuer URL should be statically defined if the application is deployed.
     */
    private fun getIssuerUrl(): String {
        val headers = requestContext.httpHeaders ?: throw IllegalStateException("Http headers are not set")
        val scheme = headers.getFirst("X-Forwarded-Proto") ?: "http"
        val host = headers.getFirst("Host") ?: "localhost"
        return "$scheme://$host/"
    }
}
