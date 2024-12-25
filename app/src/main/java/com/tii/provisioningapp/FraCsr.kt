package com.tii.provisioningapp

import kotlinx.serialization.SerialName

/**
 * Data class for CSR request.
 *
 * In case of Swarm Provisioning, includes also swarm_id, otherwise it is null.
 * */
@kotlinx.serialization.Serializable
data class FraCsr(
    val csr: String,
    @SerialName("swarm_id") val swarmId: String? = null,
)
