package com.cbruegg.socialmediaserver.retrieval.security

import io.ktor.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.SecureRandom

class Auth(dataDir: File) {
    private val tokenFile = File(dataDir, "token.txt")
    private lateinit var tokenStr: String

    suspend fun initialize() {
        withContext(Dispatchers.IO) {
            if (tokenFile.exists()) {
                println("tokenFile $tokenFile exists")
                tokenStr = tokenFile.readText()
                println("tokenFile contains tokenStr=$tokenStr")
            } else {
                val tokenBytes = ByteArray(64).also { SecureRandom.getInstanceStrong().nextBytes(it) }
                tokenStr = tokenBytes.encodeBase64()
                println("Generated tokenStr=$tokenStr")
                tokenFile.writeText(tokenStr)
                println("Wrote token to $tokenFile")
            }
        }
    }

    fun isValidToken(token: String): Boolean {
        return token == tokenStr
    }
}