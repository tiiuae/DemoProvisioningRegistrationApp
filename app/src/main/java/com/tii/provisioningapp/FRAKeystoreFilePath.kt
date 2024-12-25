package com.tii.provisioningapp

sealed interface FRAKeystoreFilePath {
    val keyStoreJKS: String
    val truststoreJKS: String
}
class FleetMgmtKeystoreFilePath(uri: String) : FRAKeystoreFilePath {
    override val keyStoreJKS = "$uri/fleet_keystore.jks"
    override val truststoreJKS = "$uri/fleet_truststore.jks"
}

class SwarmKeystoreFilePath(uri: String) : FRAKeystoreFilePath {
    override val keyStoreJKS = "$uri/swarm_keystore.jks"
    override val truststoreJKS = "$uri/swarm_truststore.jks"
}