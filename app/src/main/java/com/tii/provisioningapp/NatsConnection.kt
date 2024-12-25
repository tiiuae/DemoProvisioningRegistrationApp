package com.tii.provisioningapp

import android.util.Log
import io.nats.client.Connection
import io.nats.client.ConnectionListener
import io.nats.client.Dispatcher
import io.nats.client.Message
import io.nats.client.Nats
import io.nats.client.Options
import io.nats.client.PushSubscribeOptions
import io.nats.client.Subscription
import io.nats.client.api.AckPolicy
import io.nats.client.api.ConsumerConfiguration
import io.nats.client.api.KeyValueEntry
import io.nats.client.api.KeyValueWatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration


interface NatsConnection {


    val natsConnectionState: StateFlow<NATSConnectionState>
    fun connect(config: ServerConfig)
   // suspend fun sendRequest(subject: String, data: ByteArray): ByteArray?
    fun addSubjectObserver(observer: SubjectObserver)
    fun removeSubjectObserver(observer: SubjectObserver)
    fun disconnect()
    fun publish(message: Message)


    fun publish(subject: String, data: ByteArray)

    //fun addDisconnectNotifier(disconnectNotifier: DisconnectNotifier)
    val server: String?

    fun setKeyValuePair(bucket: String, key: String, value: String)

    fun removeJetStreamSubscriber(observer: SubjectObserver)

    fun addJetStreamSubscriber(observer: SubjectObserver)

    //fun addKeyValueBucketWatcher(keyValueBucketWatcher: KeyValueBucketWatcher)

    //fun <T, S> addRequestReplyObserver(observer: RequestReplyObserver<T, S>)
    //fun <T, S> removeRequestReplyObserver(observer: RequestReplyObserver<T, S>)
}

class NatsConnectionImpl : NatsConnection {
    private var nc: Connection? = null
    private var dispatcher: Dispatcher? = null
    private var jetStreamDispatcher: Dispatcher? = null
    private val subjectObservers: MutableList<SubjectObserver> = mutableListOf()

    fun initNats(nc : Connection) {
     this.nc = nc
    }
    private val wildCardSortingPredicate: (SubjectObserver) -> Boolean = { subjectObserver ->
        subjectObserver.subjects.any { !it.contains('*') }
    }

    private val _natsConnectionState: MutableStateFlow<NATSConnectionState> =
        MutableStateFlow(NATSConnectionState.Disconnected)
    override val natsConnectionState: StateFlow<NATSConnectionState>
        get() = _natsConnectionState



    private val natsConnectionListener = ConnectionListener(function = { _, type ->
        when (type) {
            ConnectionListener.Events.CONNECTED -> {
                _natsConnectionState.value = NATSConnectionState.Connected
            }

            ConnectionListener.Events.DISCONNECTED -> {
                _natsConnectionState.value = NATSConnectionState.Reconnecting
            }

            ConnectionListener.Events.DISCOVERED_SERVERS -> {}
            ConnectionListener.Events.RECONNECTED -> {
                _natsConnectionState.value = NATSConnectionState.Connected
            }

            ConnectionListener.Events.RESUBSCRIBED -> {}
            ConnectionListener.Events.LAME_DUCK -> {} // preparing to shut down
            ConnectionListener.Events.CLOSED -> {
                _natsConnectionState.value = NATSConnectionState.Disconnected
            }

            null -> {}
        }
    })

    // delegate received messages to corresponding observers
    private fun messageHandler(msg: Message?) {
        if (msg == null) return
        val (satisfying, remaining) = subjectObservers.partition(
            predicate = wildCardSortingPredicate,
        )
        val sortedList = satisfying + remaining
        sortedList.map { subjectObserver ->
            if (subjectObserver.muted) return@map
            if (subjectObserver.subjects.contains(msg.subject)) {
                subjectObserver.onMessageReceived(msg.data, msg.subject)
                return
            } else if (subjectObserver.derivedSubjects.contains(msg.subject)) {
                subjectObserver.onMessageReceived(msg.data, msg.subject)
                return
            } else if (subjectObserver.subjects.any { subject -> subject.contains("*") }) {
                if (matchSubject(subjectObserver, msg)) {
                    subjectObserver.onMessageReceived(msg.data, msg.subject)
                    subjectObserver.derivedSubjects.add(msg.subject)
                    return
                }
            }
        }
    }

    private fun matchSubject(subjectObserver: SubjectObserver, msg: Message): Boolean {
        subjectObserver.subjects.forEach { subject ->
            if (!subject.contains("*")) return@forEach
            if (Utils.isWildcardMatch(subject, msg.subject)) {

                return true
            }
        }
        return false
    }

    override fun connect(config: ServerConfig) {
        _natsConnectionState.value = NATSConnectionState.Connecting
        Thread {
            val builder: Options.Builder = Options.Builder()
                .connectionListener(natsConnectionListener)
                .server("${config.ipAddress}:${config.port}")
            config.sslContext?.let {
                builder.sslContext(it)
            }
            try {
                nc = Nats.connect(builder.build())
                dispatcher = nc?.createDispatcher { messageHandler(it) }
                jetStreamDispatcher = nc?.createDispatcher()
                subjectObservers.map {
                    it.subjects.map { subject ->
                        dispatcher?.subscribe(subject)
                    }
                }
            } catch (exp: Exception) {
                exp.message?.let { Log.e("Error",it) }
                val error = Error(exp.message)
                _natsConnectionState.value = NATSConnectionState.Error(error)
            }
        }.start()
    }

  /*  override suspend fun sendRequest(subject: String, data: ByteArray): ByteArray? {
        val result = withContext(Dispatchers.IO) {
            var duration = 2.
            //..nc?.request(subject, data,duration)

        }
        return result?.data
    }*/


    override fun addSubjectObserver(observer: SubjectObserver) {
        try {
            Log.i("addSubjectObserver", "${observer.javaClass.simpleName}")
            subjectObservers.add(observer)
            Log.i("Current SubjectObservers : ","${subjectObservers.map { it.javaClass.simpleName }} ")
            if (nc?.connectedUrl == null) {
                Log.e("Not connected"," to server")
                return
            }
            observer.subjects.map {
                dispatcher?.subscribe(it)
            }
        } catch (e: Exception) {
            Log.e("Error adding subject observer:"," ${e.message}")
        }
    }





    override fun removeSubjectObserver(observer: SubjectObserver) {
        if (dispatcher?.isActive == true) {
            observer.subjects.map {
                dispatcher?.unsubscribe(it)
                Log.i("Unsubscribed"," $it")
            }
        }
        subjectObservers.remove(observer)
        Log.i("Removed ","${observer.javaClass.simpleName}")
        Log.i("Current SubjectObservers :"," ${subjectObservers.map { it.javaClass.simpleName }} ")
    }

    override fun disconnect() {
        TODO("Not yet implemented")
    }

    override fun publish(message: Message) {

        try {
            var result = nc?.publish(message)
            nc?.subscribe(message.subject)
            dispatcher = nc?.createDispatcher { messageHandler(it) }
            jetStreamDispatcher = nc?.createDispatcher()
            subjectObservers.map {
                it.subjects.map { subject ->
                    dispatcher?.subscribe(subject)
                }
            }
            return
        } catch (exp: Exception) {
            exp.message?.let { Log.e("Error",it) }
            val error = Error(exp.message)
            _natsConnectionState.value = NATSConnectionState.Error(error)
        }
        return
    }


    override fun publish(subject: String, data: ByteArray) {
        if (server != null)
            nc?.publish(subject, data)

    }

    override val server get() = nc?.connectedUrl

    override fun setKeyValuePair(bucket: String, key: String, value: String) {
        try {
            val keyValue = nc?.keyValue(bucket)
            keyValue?.let {
                it.put(key, value)
            } ?: Log.e("No bucket"," $bucket found")
        } catch (e: Exception) {
            Log.e("Error setting"," $key as $value in $bucket: ${e.message}")
        }
    }

    override fun addJetStreamSubscriber(observer: SubjectObserver) {
        if (nc == null) {
            Log.e("Cannot add jetstream"," when not connected")
            return
        }
        try {
            subjectObservers.add(observer)
            Log.i("addJetStreamSubscriber"," ${observer.javaClass.simpleName}")
            val config = ConsumerConfiguration
                .builder()
                .ackPolicy(AckPolicy.None)
                .build()
            val pushSubscribeOptions = PushSubscribeOptions
                .builder()
                .configuration(config)
                .stream(observer.stream)
                .build()
            observer.subjects.map { subject ->
                nc?.jetStream()?.subscribe(
                    subject,
                    jetStreamDispatcher,
                    { messageHandler(it) },
                    true,
                    pushSubscribeOptions,
                )
            }
        } catch (e: Exception) {
            Log.e("Error adding JetStream subscriber"," ${e.message}")
        }
    }

    override fun removeJetStreamSubscriber(observer: SubjectObserver) {
        if (jetStreamDispatcher?.isActive == true) {
            observer.subjects.map {
                jetStreamDispatcher?.unsubscribe(it)
                Log.i("Unsubscribed ","$it")
            }
            subjectObservers.remove(observer)
            Log.i("Removed ","${observer.javaClass.simpleName}")
        }
    }
}
