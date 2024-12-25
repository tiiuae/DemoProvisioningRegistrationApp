package com.tii.provisioningapp

object Utils {
    fun isWildcardMatch(subject: String, messageSubject: String): Boolean {
        val pattern = subject.replace(".", "\\.").replace("*", "([^.]+)")
        val regex = Regex(pattern)
        return regex.matches(messageSubject)
    }
}