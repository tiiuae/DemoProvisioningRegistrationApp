package com.tii.provisioningapp


data class RegistrationData(
    val key: CharArray?,
    val deviceRegistrationCertificate: String?,
    val deviceRegistrationCaCertificate: String?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RegistrationData

        if (key != null) {
            if (other.key == null) return false
            if (!key.contentEquals(other.key)) return false
        } else if (other.key != null) return false
        if (deviceRegistrationCertificate != other.deviceRegistrationCertificate) return false
        if (deviceRegistrationCaCertificate != other.deviceRegistrationCaCertificate) return false

        return true
    }

    override fun hashCode(): Int {
        var result = key?.contentHashCode() ?: 0
        result = 31 * result + (deviceRegistrationCertificate?.hashCode() ?: 0)
        result = 31 * result + (deviceRegistrationCaCertificate?.hashCode() ?: 0)
        return result
    }

    fun certificatesMissing(): Boolean {
        return (deviceRegistrationCertificate == null || deviceRegistrationCaCertificate == null)
    }

    fun missingPinAndCertificates(): Boolean {
        return (
                key == null &&
                        (
                                deviceRegistrationCertificate == null ||
                                        deviceRegistrationCaCertificate == null
                                )
                )
    }
}
