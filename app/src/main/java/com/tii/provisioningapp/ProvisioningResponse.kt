package com.tii.provisioningapp


@kotlinx.serialization.Serializable
data class ProvisioningResponse(
    var caCertificate: String? = null,
    var certificate: String? = null,
    var fleetManagementNatsUrl: String? = null,
) {
    fun toProvisioningResponseCerts(): ProvisioningResponseCerts {
        return ProvisioningResponseCerts(certificate, caCertificate)
    }
}