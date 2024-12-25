package com.tii.provisioningapp

sealed class NATSConnectionState {
    object Disconnected : NATSConnectionState()
    object Connecting : NATSConnectionState()
    object Connected : NATSConnectionState()
    object Reconnecting : NATSConnectionState()
    class Error(val error: kotlin.Error) : NATSConnectionState()
}