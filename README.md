Device Provisioning and Registration has two basic steps to complete the device onboarding in Fleet.

1 Device Provisioning :-  

We need the provisioning server code from below Github repo 

Github Repo link for running Provisioning server :- https://github.com/tiiuae/provisioning-serverConnect your Github account 

First step is to run the provisioning server in an isolated environment 

Now connect the mobile to the same network and get the IP  of the host machine running this provisioning server

Need to generate Id using UUID id = UUID.randomUUID().toString().replace("-", "")

then generate CSR - certificate Signing Request to locally running Provisioning server



private fun generateCSR(keyPair: KeyPair, id: String): FraCsr {
    val csr = createCSR(keyPair, id)
    val csrPem = encodeCSRIntoPemBlock(csr)
    return FraCsr(csrPem)
}
for CreateCSR CN must be like = /Finland~Solita/fleet/$id/registration_agent  

id is the deviceId 

After hitting the IP with post request we will receive below values in response



 caCertificate: String?,
 certificate: String?
 fleetManagementNatsUrl: String,
Now we need to store these values for registration process.

fleetManagementNatsUrl is used to hit the NATs server to register device 

caCertificate and certificate are used for hitting APIs for registration in Fleet Management server .

 

2.Device Registration 

 

OLD API - Discontinued

We need to integrate NATs library to get the NATs connection client for connecting 

Then we have fleetManagementNatsUrl from above to hit the API

For that we need to look into FleetManagement server code for setting up the Request format. :- https://github.com/tiiuae/fleet-manager/blob/main/docs/registration.md 



val requestBody = RegistrationRequest(
    deviceType = "mobile",
    architecture = "arm64",
    BuildVersion = "1.0.0",
    currentOwner = "solita",
    tailscaleID = "ts-1234567890",
    certificate = registrationData?.deviceRegistrationCertificate,
    caCertificate = registrationData?.deviceRegistrationCaCertificate,
)
for End point url we need to send in the format of  "device_actions.$deviceUUID.register"and post the above value in byte array.

On Success Response will be



{
  "alias": "laptop-1",
  "token": "aGVsbG8K",
  "configuration": "...",
  "utmClientSecret": "aGVsbG8K",
  "rabbitMqSecret": "aGVsbG8K"
}
On Error Response will be 



{
  "status": "failure",
  "error": "device already registered"
}
 

New API Endpoint for latest code implementation : -


SUBJECT = "device_actions.<id>.register"
val requestBody = RegistrationRequest(
    id = "$deviceUUID", //optional
    deviceType = "mobile", // mandatory
    architecture = "arm64", //mandatory
    buildVersion = "1.0.0",
    currentOwner = "solita",
    tailscaleID = "ts-1234567890",
    certificate = registrationData?.deviceRegistrationCertificate,
    caCertificate = registrationData?.deviceRegistrationCaCertificate,
    alias = "Aplha$deviceUUID" //optional
)
 val headerVal = Headers()
 headerVal.add("ID", deviceUUID)
 headerVal.add("timestamp", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX")
                    .format(Date()) ) //Date Format in RFC3339 or ISO8601
 var  msg = NatsMessage.builder()
            .subject(SUBJECT)
            .headers(headerVal)
            .data(requestString)
            .build()
natsConnectionImpl.publish(msg)
 

Once we hit the publish API then we need to subscribe to the subject mention above in the code and using jetStreamDispatcher we can get the response from backend API as below

[#1] Received on "device_results.9000f4db6a8645c7a5445cb56ac3ce22.register"
ID: 9000f4db6a8645c7a5445cb56ac3ce22
timestamp: 2024-09-02T10:26:32Z



{"status":"success","payload":
{"alias":"Aplha9000f4db6a8645c7a5445cb56ac3ce22"},
"alias":"Aplha9000f4db6a8645c7a5445cb56ac3ce22"}
 

Important Link :- 
Link can be used for Request Response values  and other details of the APIs of NATS 

https://studious-happiness-1w3zmmr.pages.github.io/ 

 
