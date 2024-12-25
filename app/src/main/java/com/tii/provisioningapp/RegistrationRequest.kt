package com.tii.provisioningapp

import kotlinx.serialization.Serializable

@Serializable
data class RegistrationRequest(
    val id: String? = null,
    val alias: String? = null,
    val deviceType: String? = null,
    val architecture: String? = null,
    val tailscaleID: String? = null,
    val buildVersion: String? = null,
    val currentOwner: String? = null,
    var certificate: String? = null,
    var caCertificate: String? = null,
    )