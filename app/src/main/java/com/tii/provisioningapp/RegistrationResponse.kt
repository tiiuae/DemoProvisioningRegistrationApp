package com.tii.provisioningapp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RegistrationResponse(
    @SerialName("status") var status: String? = null,
    @SerialName("alias") var alias: String? = null,
)
