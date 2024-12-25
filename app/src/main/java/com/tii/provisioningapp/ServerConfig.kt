package com.tii.provisioningapp


import javax.net.ssl.SSLContext

const val NATS_IP_DEFAULT = "127.0.0.1"
const val NATS_PORT_DEFAULT = "4222"
data class ServerConfig(
    val ipAddress: String = NATS_IP_DEFAULT,
    val port: String = NATS_PORT_DEFAULT,
    val sslContext: SSLContext? = null,
) {

    override fun toString(): String {
        return if (port != NATS_PORT_DEFAULT) {
            "$ipAddress:$port"
        } else {
            ipAddress
        }
    }
}