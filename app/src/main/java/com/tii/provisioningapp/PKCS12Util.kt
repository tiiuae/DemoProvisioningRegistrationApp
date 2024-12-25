package com.tii.provisioningapp

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.StringWriter
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate


object PKCS12Util {
    private val TAG ="PKCS12Util"
    /**
     * Creates PKCS12 keystore that contains private keys and certificates which can be used in SSL communications across the web.
     *
     * Before storing an entry into a PKCS12 keystore, the keystore has to be loaded first. This means we have to create a keystore first.
     * Then the key entry is stored in the keyStore by calling keyStore.setEntry().
     * Then keyStore is saved by calling keyStore.store(), otherwise the entry will be lost when the program exits.
     *
     * @param chain Array<X509Certificate> - Certificate chain can stored in PKCS12 keystore.
     * @param path FraFilePath - Path to the keystore.
     * @param key PrivateKey? - The private key used to store chain in PKCS12 keystore.
     * @param keystorePassword CharArray? - Used to encrypt keystore.
     * @param alias String - the alias for the KeyEntry
     * @return Job - job that generates the PCKS12
     * @throws java.security.KeyStoreException
     * @throws java.security.cert.CertificateException
     * @throws java.io.IOException
     */
    fun generatePKCS12(
        chain: Array<X509Certificate>,
        path: FRAKeystoreFilePath,
        key: PrivateKey,
        keystorePassword: CharArray,
        alias: String,
        keyStore: KeyStore,
    ): Job {
        return CoroutineScope(Dispatchers.IO).launch(Dispatchers.IO) {
            try {
                keyStore.load(null, null)
                keyStore.store(FileOutputStream(path.keyStoreJKS), keystorePassword)
                keyStore.load(FileInputStream(path.keyStoreJKS), keystorePassword)
                keyStore.setKeyEntry(alias, key, keystorePassword, chain)
                keyStore.store(FileOutputStream(path.keyStoreJKS), keystorePassword)
            } catch (ex: Exception) {
                Log.e(TAG,"Error : ${ex.message}")
            }
            Log.i(TAG,"GENERATED KEYSTORE.JKS")
        }
    }

    fun loadRegistrationDataFromKeyStore(
        keystorePassword: CharArray,
        path: FRAKeystoreFilePath,
        deviceUUID: String,
        keyStore: KeyStore,
    ): RegistrationData? {
        return try {
            keyStore.load(getFileInputStream(path.keyStoreJKS), keystorePassword)
            val chain = keyStore.getCertificateChain(deviceUUID)

            val deviceRegistrationCertificate =
                chain.first()?.let { convertCertificateToPEM(it as X509Certificate) }
            val deviceRegistrationCaCertificate =
                chain.last()?.let { convertCertificateToPEM(it as X509Certificate) }

            val registrationData = RegistrationData(
                keystorePassword,
                deviceRegistrationCertificate,
                deviceRegistrationCaCertificate,
            )
            registrationData
        } catch (ex: Exception) {
            Log.e(TAG,"Error : ${ex.message}")
            null
        }
    }


    fun getFileInputStream(path: String): FileInputStream {
        return FileInputStream(path)
    }
    fun convertCertificateToPEM(certificate: X509Certificate): String {
        return encodeCertificateIntoPEMBlock(certificate)
    }

    /**
     *
     * @param any Any
     * @return String
     * @throws java.io.IOException
     */
    private fun encodeCertificateIntoPEMBlock(any: Any): String {
        val sw = StringWriter()
        val pemWriter = JcaPEMWriter(sw)
        pemWriter.writeObject(any)
        pemWriter.close()
        return sw.toString()
    }
}
