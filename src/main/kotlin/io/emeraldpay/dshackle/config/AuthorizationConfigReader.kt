package io.emeraldpay.dshackle.config

import org.slf4j.LoggerFactory
import org.springframework.util.ResourceUtils
import org.yaml.snakeyaml.nodes.MappingNode
import java.io.FileNotFoundException

class AuthorizationConfigReader : YamlConfigReader<AuthorizationConfig>() {

    companion object {
        private val log = LoggerFactory.getLogger(AuthorizationConfigReader::class.java)
    }

    override fun read(input: MappingNode?): AuthorizationConfig {
        val auth = getMapping(input, "auth")
        if (auth == null) {
            log.warn("Authorization is not using")
            return AuthorizationConfig.default()
        }

        val enabled = getValueAsBool(auth, "enabled")
        if (enabled == null || !enabled) {
            log.warn("Authorization is not enabled")
            return AuthorizationConfig.default()
        }

        val keyPair = getMapping(auth, "key-pair") ?: throw IllegalStateException("Auth key-pair is not specified")
        val privateKey = getValueAsString(keyPair, "provider-private-key")
            ?: throw IllegalStateException("Private key in not specified")
        val publicKey = getValueAsString(keyPair, "drpc-public-key")
            ?: throw IllegalStateException("Public key in not specified")

        if (fileNotExists(privateKey)) {
            throw IllegalStateException("There is no such file: $privateKey")
        }
        if (fileNotExists(publicKey)) {
            throw IllegalStateException("There is no such file: $publicKey")
        }

        return AuthorizationConfig(enabled, privateKey, publicKey)
    }

    private fun fileNotExists(path: String): Boolean {
        return try {
            !ResourceUtils.getFile(path).exists()
        } catch (e: FileNotFoundException) {
            true
        }
    }
}
