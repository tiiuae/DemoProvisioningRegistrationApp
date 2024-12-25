package com.tii.provisioningapp

import java.io.BufferedInputStream
import java.io.FileInputStream
import java.security.KeyStore
import java.security.SecureRandom
import javax.net.ssl.*

object SSLUtils {

    const val PKCS12_KEY_STORE_TYPE = "PKCS12"
    const val BOUNCY_CASTLE_PROVIDER = "BC"
    const val CERTIFICATE_FACTORY_ALGORITHM = "X509"
    /**
     *
     * @param path String?
     * @param keystorePassword CharArray
     * @return [KeyStore]
     *
     * @throws java.io.IOException if there is an I/O or format problem with the keystore data,
     * if a password is required but not given, or if the given password was incorrect.
     * If the error is due to a wrong password, the cause of the IOException should be an UnrecoverableKeyException
     * @throws java.security.cert.CertificateException if any of the certificates in the keystore could not be loaded
     * @throws java.security.NoSuchAlgorithmException if the algorithm used to check the integrity of the keystore cannot be found
     * @throws java.security.NoSuchProviderException if the specified provider is not registered in the security provider list.
     */
    private fun loadKeystore(path: String?, keystorePassword: CharArray): KeyStore {
        val store = KeyStore.getInstance(PKCS12_KEY_STORE_TYPE, BOUNCY_CASTLE_PROVIDER)
        val input = BufferedInputStream(FileInputStream(path))
        input.use {
            store.load(it, keystorePassword)
        }
        return store
    }

    private fun createKeyManagers(
        keystorePassword: CharArray,
        path: FRAKeystoreFilePath,
    ): Array<KeyManager> {
        val store = loadKeystore(path.keyStoreJKS, keystorePassword)
        val factory =
            KeyManagerFactory.getInstance(CERTIFICATE_FACTORY_ALGORITHM)
        factory.init(store, keystorePassword)
        return factory.keyManagers
    }

    private fun createTrustManagers(
        keystorePassword: CharArray,
        path: FRAKeystoreFilePath,
    ): Array<TrustManager> {
        val store = loadKeystore(path.truststoreJKS, keystorePassword)
        val factory =
            TrustManagerFactory.getInstance(CERTIFICATE_FACTORY_ALGORITHM)
        factory.init(store)
        return factory.trustManagers
    }

    /**
     * Creates SSL context from keystore and truststore specified by [FRAKeystoreFilePath].
     * Must be supplied with correct encryption password in order to succeed.
     *
     * @param keystorePassword CharArray
     * @param path FraFilePath
     * @return SSLContext
     * @throws java.io.IOException if there is an I/O or format problem with the keystore data,
     * if a password is required but not given, or if the given password was incorrect.
     * If the error is due to a wrong password, the cause of the IOException should be an UnrecoverableKeyException
     * @throws java.security.cert.CertificateException if any of the certificates in the keystore could not be loaded
     * @throws java.security.NoSuchAlgorithmException if the algorithm used to check the integrity of the keystore cannot be found
     * @throws java.security.NoSuchProviderException if the specified provider is not registered in the security provider list.
     */
    fun createSSLContext(keystorePassword: CharArray, path: FRAKeystoreFilePath): SSLContext {
        val ctx = SSLContext.getInstance("TLSv1.2")
        ctx.init(
            createKeyManagers(keystorePassword, path),
            createTrustManagers(keystorePassword, path),
            SecureRandom(),
        )
        return ctx
    }
}