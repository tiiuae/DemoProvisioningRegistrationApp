package com.tii.provisioningapp

interface SubjectObserver {
    /**
     * @return list of topics that this observer wants to receive
     */
    val subjects: Array<String>

    /**
     * @return The name of a stream to which consumer will be attached to,
     * this value will be only present for SubjectObserver that relies on JetStream
     * */
    val stream: String?

    /**
     * A built list of derived subjects that were discovered at runtime using wildcards.
     * */
    val derivedSubjects: MutableList<String>

    /**
     * When true, NatsConnection will not send the messages to this observer
     */
    val muted: Boolean

    /**
     * Called when NatsConnection receives a message that matches the subjects array.
     * @param data Message as [ByteArray]
     * @param subject the topic of the message
     */
    fun onMessageReceived(data: ByteArray, subject: String)
}