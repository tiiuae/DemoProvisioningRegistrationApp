package com.tii.provisioningapp

class InvalidAddressError : Error()
class AlreadyConnectedError : Error()

sealed interface FRAResult<out T, out E> {
    data class Success<out T> (val data: T) : FRAResult<T, Nothing>
    data class Failure<out E> (val error: E) : FRAResult<Nothing, E>
}
