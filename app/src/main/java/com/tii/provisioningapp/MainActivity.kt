package com.tii.provisioningapp

//import io.nats.client.Nats
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.tii.provisioningapp.PKCS12Util.convertCertificateToPEM
import io.nats.client.ConnectionListener
import io.nats.client.Message
import io.nats.client.Nats
import io.nats.client.Options
import io.nats.client.impl.Headers
import io.nats.client.impl.NatsMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.StringWriter
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import java.util.UUID


class MainActivity : ComponentActivity() {
    private var editor: SharedPreferences.Editor? = null
    private var sharedPreference: SharedPreferences? = null
    private lateinit var registrationData: RegistrationData
    private lateinit var keyPair: KeyPair
    private lateinit var id: String
    private lateinit var responseProvisioning: Response<ProvisioningResponse>
    private lateinit var path: FleetMgmtKeystoreFilePath
    private lateinit var btn_register: Button
    private var natsURL: String? = null
    private lateinit var button: Button
    private lateinit var editTextIp: EditText
    private val NATS_SERVER_ADDRESS_MATCHER = """^((nats)?:\/\/)?([^:/?#]+)(?::(\d+))?"""

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.main_activity)
        editTextIp = findViewById<EditText>(R.id.editTextIp)
        button = findViewById<Button>(R.id.button)
        btn_register =findViewById<Button>(R.id.btn_register)
        path = FleetMgmtKeystoreFilePath(applicationContext.filesDir.absolutePath)
         sharedPreference =  getSharedPreferences("PREFERENCE_NAME", Context.MODE_PRIVATE)
         editor = sharedPreference?.edit()

        button.setOnClickListener {

            lifecycleScope.launch {
                try {
                    // Switch to IO thread for network operation
                    val responseData = withContext(Dispatchers.IO) {
                        if(!editTextIp.text.isNullOrBlank())
                            apiCall()
                    }
                    // Update UI with the result (on Main thread)
                } catch (e: Exception) {
                    Toast.makeText(applicationContext, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        btn_register.setOnClickListener {
            callRegister()
        }
    }

    fun callRegister() {

        lifecycleScope.launch {
            val sslContext = SSLUtils.createSSLContext(
                "1234".toCharArray(), path,
            )
            if(natsURL ==  null ){
                natsURL = "nats.staging.airoplatform.com:4222"
            }
            natsURL?.let { serverUrl ->
                val ipAndPort = splitIpAndPort(serverUrl)
                val config = ServerConfig(ipAndPort.first, ipAndPort.second, sslContext)
                //connect(config)
                registerDevice(config)
            }
        }
    }
    private fun apiCall(){
        keyPair = generateKeyPair()
        var baseUrl = editTextIp.text.toString().trim()
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl) // Replace with your API's base URL
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        id = UUID.randomUUID().toString().replace("-", "")
        val fraCsr = generateCSR(keyPair, id)
        val apiService = retrofit.create(ApiService::class.java)

        // Make the API call
        apiService.postData(fraCsr).enqueue(object : Callback <ProvisioningResponse> {


            override fun onFailure(
                call: Call<ProvisioningResponse>,
                t: Throwable
            ) {
                Toast.makeText(applicationContext, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }

            override fun onResponse(
                call: Call<ProvisioningResponse>,
                response: Response<ProvisioningResponse>
            ) {
                responseProvisioning = response
               natsURL=  response.body()?.fleetManagementNatsUrl
               editor?.putString("natsUrl",natsURL)
                createKeys(
                    responseProvisioning.body()!!.certificate!!,
                    responseProvisioning.body()!!.caCertificate!! ,
                    FRAKeyStoreConfig(id, "1234".toCharArray(), keyPair.private, path)
                )
            }
        })
    }

    fun splitIpAndPort(serverAddress: String): Pair<String, String> {
        val regex = Regex(NATS_SERVER_ADDRESS_MATCHER)
        regex.find(serverAddress)?.let { result ->
            val hostAddress = result.groupValues[3]
            val port = result.groupValues[4]
            if (port.isBlank()) return hostAddress to NATS_PORT_DEFAULT
            return hostAddress to port
        }

        return NATS_IP_DEFAULT to NATS_PORT_DEFAULT
    }
    val KEY_PAIR_ALGORITHM = "RSA"
    val CSR_SIGNATURE_ALGORITHM = "SHA512WITHRSA"
    val CERTIFICATE_FACTORY_ALGORITHM = "X509"
    val PKCS12_KEY_STORE_TYPE = "PKCS12"
    val BOUNCY_CASTLE_PROVIDER = "BC"
    private fun saveRegistrationCache(chain: Array<X509Certificate>) {
         registrationData =
            createRegistrationData(chain = chain, "1234".toCharArray())
        editor?.clear()
        editor?.putString("registrationData",gson.toJson(registrationData))
        editor?.putString("id",id)
        editor?.commit()
        //fraStore.registrationCache = registrationData
    }
    private fun generateKeyPair(): KeyPair {
        val keygen = KeyPairGenerator.getInstance(KEY_PAIR_ALGORITHM)
        keygen.initialize(2048)
        return keygen.genKeyPair()
    }

    private fun encodeCSRIntoPemBlock(csr: PKCS10CertificationRequest): String {
        return encodeCertificateIntoPEMBlock(csr)
    }

    private fun generateCSR(keyPair: KeyPair, id: String): FraCsr {
        val csr = createCSR(keyPair, id)
        val csrPem = encodeCSRIntoPemBlock(csr)
        return FraCsr(csrPem)
    }

    private fun encodeCertificateIntoPEMBlock(any: Any): String {
        val sw = StringWriter()
        val pemWriter = JcaPEMWriter(sw)
        pemWriter.writeObject(any)
        pemWriter.close()
        return sw.toString()
    }

    private fun createCSR(keyPair: KeyPair, id: String): PKCS10CertificationRequest {
        Log.i("CSR", "CREATING CSR")

        // Build common name for the device following the identity format specified at
        // https://github.com/tiiuae/registration-agent#device-identity-format
        val pkcs10Builder: PKCS10CertificationRequestBuilder =
            JcaPKCS10CertificationRequestBuilder(
                //X500Name("CN=/Finland~Solita/fleet/$id/registration_agent"),
                X500Name("CN=$id/registration_agent"),
                keyPair.public,
            )
        return pkcs10Builder.build(
            JcaContentSignerBuilder(CSR_SIGNATURE_ALGORITHM)
                .build(keyPair.private),
        )
    }

    fun createKeys(
        provisioningResponseCertificate: String,
        provisioningResponseCACertificate: String,
        fraKeyStoreConfig: FRAKeyStoreConfig
    ) {
        Log.i("TAG","createKeys")
        val certificates = generateCertificates(provisioningResponseCertificate)
        val caCertificate = generateCaCertificate(provisioningResponseCACertificate)
        val chain = createChain(certificates)
        if (fraKeyStoreConfig.password == null) {
            Log.i("TAG","Failed to create keys, onFailure()")
        }
        // Launch both operations concurrently
        listOf(
            PKCS12Util.generatePKCS12(
                chain,
                fraKeyStoreConfig.paths,
                fraKeyStoreConfig.privateKey,
                fraKeyStoreConfig.password,
                fraKeyStoreConfig.alias,
                KeyStore.getInstance(PKCS12_KEY_STORE_TYPE),
            ),
            createTrustKeyInstance(caCertificate, fraKeyStoreConfig.paths, fraKeyStoreConfig.password),
        )
        // Load is done, now we have both KeyStore.jks and truststore.jks in our files
        Log.i("TAG","Created Keys, onSuccess()")
        //onSuccess(chain)
        saveRegistrationCache(chain)

    }


    /**
     * Generates CA Certificate from provided pem block
     *
     * @param caCertificate String? - CA certificate pem block
     * @return Certificate
     */
    private fun generateCaCertificate(caCertificate: String?): Certificate  {

        val caCertBytes = caCertificate?.toByteArray(Charsets.UTF_8)
            val caCertStream = ByteArrayInputStream(caCertBytes)
            val caCert =
                CertificateFactory.getInstance(CERTIFICATE_FACTORY_ALGORITHM)
                    .generateCertificate(caCertStream)
            caCertStream.close()
        return caCert
//            return@withContext caCert
        }

    /**
     * Generates collection of certificates from provided certificates pem block.
     *
     * @param certificatePem String?  Certificates PEM block
     * @return Collection<Certificate>
     */
    private fun generateCertificates(certificatePem: String?): Collection<Certificate>
         {
            val certBytes = certificatePem?.toByteArray(Charsets.UTF_8)
            val certStream = ByteArrayInputStream(certBytes)
            val cert =
                CertificateFactory.getInstance(CERTIFICATE_FACTORY_ALGORITHM)
                    .generateCertificates(certStream)
            certStream.close()
            return cert
        }

    private fun createChain(certificates: Collection<Certificate>): Array<X509Certificate> {
        return certificates.map {
            Log.v("TAG","Keystore certificate :$it")
            return@map it as X509Certificate
        }.toTypedArray()
    }

    private fun createRegistrationData(
        chain: Array<X509Certificate>,
        keystorePassword: CharArray?,
    ): RegistrationData {
        val deviceRegistrationCertificate = convertCertificateToPEM(chain.first())
        val deviceRegistrationCaCertificate = convertCertificateToPEM(chain.last())
        return RegistrationData(
            keystorePassword,
            deviceRegistrationCertificate,
            deviceRegistrationCaCertificate,
        )
    }

    /**
     * Creates truststore.jks keystore file
     *
     * @param caCertificate Certificate
     * @param path FraFilePath - Path where truststore should be loaded to
     * @param keystorePassword CharArray? - key used to encrypt the .jks file
     * @return Job
     * @throws java.security.KeyStoreException
     * @throws java.security.cert.CertificateException
     * @throws java.io.IOException
     */
    private fun createTrustKeyInstance(
        caCertificate: Certificate,
        path: FRAKeystoreFilePath,
        keystorePassword: CharArray,
    ): Job {
        return CoroutineScope(Dispatchers.IO).launch(context = Dispatchers.IO) {
            val trustStore = KeyStore.getInstance(PKCS12_KEY_STORE_TYPE)

            trustStore.load(null, null)
            trustStore.setCertificateEntry("ca", caCertificate as X509Certificate)

            //   Save JKS file
            val file = File(path.truststoreJKS)
            val keyStoreStream = FileOutputStream(file)
            trustStore.store(keyStoreStream, keystorePassword)
            keyStoreStream.close()
            Log.i("TAG","CREATED TRUSTSTORE INSTANCE")
        }
    }

    private val subjectObservers: MutableList<SubjectObserver> = mutableListOf()
    private val natsConnectionListener = ConnectionListener(function = { _, type ->
        when (type) {
            ConnectionListener.Events.CONNECTED -> {
            }

            ConnectionListener.Events.DISCONNECTED -> {
            }

            ConnectionListener.Events.DISCOVERED_SERVERS -> {}
            ConnectionListener.Events.RECONNECTED -> {
            }

            ConnectionListener.Events.RESUBSCRIBED -> {}
            ConnectionListener.Events.LAME_DUCK -> {} // preparing to shut down
            ConnectionListener.Events.CLOSED -> {
            }

            null -> {}
        }
    })
    fun connect(config: ServerConfig) {

            try {
                // Switch to IO thread for network operation
                val builder: Options.Builder = Options.Builder()
                    //.connectionListener(natsConnectionListener)
                    .server("${config.ipAddress}:${config.port}")
                config.sslContext?.let {
                    builder.sslContext(it)
                }

                try {


                    var nc = Nats.connect(builder.build())
                    var dispatcher = nc?.createDispatcher { messageHandler(it) }
                    var jetStreamDispatcher = nc?.createDispatcher()
                    subjectObservers.map {
                        it.subjects.map { subject ->
                            dispatcher?.subscribe(subject)
                        }
                    }
                } catch (exp: Exception) {
                    exp.message?.let { Log.e("Error",it) }
                    val error = Error(exp.message)
                }
                // Update UI with the result (on Main thread)
            } catch (e: Exception) {
                Toast.makeText(applicationContext, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }

    }


    private val wildCardSortingPredicate: (SubjectObserver) -> Boolean = { subjectObserver ->
        subjectObserver.subjects.any { !it.contains('*') }
    }

    // delegate received messages to corresponding observers
    private fun messageHandler(msg: Message?) {
        if (msg == null) return
        val (satisfying, remaining) = subjectObservers.partition(
            predicate = wildCardSortingPredicate,
        )
        val sortedList = satisfying + remaining
        sortedList.map { subjectObserver ->
            if (subjectObserver.muted) return@map
            if (subjectObserver.subjects.contains(msg.subject)) {
                subjectObserver.onMessageReceived(msg.data, msg.subject)
                return
            } else if (subjectObserver.derivedSubjects.contains(msg.subject)) {
                subjectObserver.onMessageReceived(msg.data, msg.subject)
                return
            } else if (subjectObserver.subjects.any { subject -> subject.contains("*") }) {
                if (matchSubject(subjectObserver, msg)) {
                    subjectObserver.onMessageReceived(msg.data, msg.subject)
                    subjectObserver.derivedSubjects.add(msg.subject)
                    return
                }
            }
        }
    }
    private fun matchSubject(subjectObserver: SubjectObserver, msg: Message): Boolean {
        subjectObserver.subjects.forEach { subject ->
            if (!subject.contains("*")) return@forEach
            if (Utils.isWildcardMatch(subject, msg.subject)) {
                Log.v(
                    "Wildcard match, onMessageReceived and add", "${msg.subject} subject to " +
                            "${subjectObserver.javaClass.simpleName} derivedSubjects"
                )
                return true
            }
        }
        Log.v("No wildcard match in ","$subjectObserver for ${msg.subject}")
        return false
    }

    val gson = Gson()
    private var FLEET_MGMT_DEVICE_REGISTER = "device_actions.<id>.register"
    private fun registerDevice(config: ServerConfig) {

      //  GlobalScope.launch {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                registrationData = gson.fromJson<RegistrationData>(sharedPreference?.getString("registrationData","null"),RegistrationData::class.java )
                val key = registrationData?.key
                val deviceUUID = sharedPreference?.getString("id","null")

                if (key == null || deviceUUID == null) {
                    //stateMachine.transition(Event.RegistrationFailed)
                    return@launch
                }
                FLEET_MGMT_DEVICE_REGISTER = "device_actions.$deviceUUID.register"
                val requestBody = RegistrationRequest(
                    id = "$deviceUUID",
                    deviceType = "mobile",
                    architecture = "arm64",
                    buildVersion = "1.0.0",
                    currentOwner = "solita",
                    tailscaleID = "ts-1234567890",
                    certificate = registrationData?.deviceRegistrationCertificate,
                    caCertificate = registrationData?.deviceRegistrationCaCertificate,
                    alias = "Satyam$deviceUUID"
                )
                val builder: Options.Builder = Options.Builder()
                    //.connectionListener(natsConnectionListener)
                    .server("${config.ipAddress}:${config.port}")
                config.sslContext?.let {
                    builder.sslContext(it)
                }


                val requestString = gson.toJson(requestBody).toByteArray()

                val natsConnectionImpl = NatsConnectionImpl()
                val headerVal = Headers()
                headerVal.add("ID", deviceUUID)

                headerVal.add("timestamp", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX")
                    .format(Date()) )


                var  msg = NatsMessage.builder()
                    .subject(FLEET_MGMT_DEVICE_REGISTER)
                    .headers(headerVal)
                    .data(requestString)
                    .build()


                var nc = Nats.connect(builder.build())

                natsConnectionImpl.initNats(nc)
               var results=  natsConnectionImpl.publish(msg)

              /*  val result =
                    natsConnectionImpl.sendRequest(FLEET_MGMT_DEVICE_REGISTER, requestString)
                        ?: return@launch*/
                val message = results?.toString()
                Log.i("Response" ,message.toString())


                val response =
                    gson.fromJson<RegistrationResponse>(message, RegistrationResponse::class.java)//
                // result?.let { parseMessage(it, RegistrationResponse) }
                Log.i("Registration $id", response!!.toString() + "  --"+response?.status + "   " + response?.alias)
                when (response?.status) {

                }
            }
            catch (e :Exception) {
                Log.e("Error" , e.message.toString())
            }
        }
    }

}