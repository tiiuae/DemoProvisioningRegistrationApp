package com.tii.provisioningapp

sealed interface CommonFilePath {
    val keyStoreJKS: String
    val truststoreJKS: String
}
