package com.tii.provisioningapp

import java.security.PrivateKey

/**
 *
 * @property alias String - identifies the keystore entry
 * @property password CharArray - 256-byte password hash derived from user-selected PIN
 * @property privateKey PrivateKey -The private key used to store chain in keystore.
 * @property paths FRAKeystoreFilePath
 * @constructor
 */
data class FRAKeyStoreConfig(
    val alias: String,
    val password: CharArray,
    val privateKey: PrivateKey,
    val paths: FRAKeystoreFilePath,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FRAKeyStoreConfig

        if (alias != other.alias) return false
        if (!password.contentEquals(other.password)) return false
        if (paths != other.paths) return false

        return true
    }

    override fun hashCode(): Int {
        var result = alias.hashCode()
        result = 31 * result + password.contentHashCode()
        result = 31 * result + paths.hashCode()
        return result
    }
}
