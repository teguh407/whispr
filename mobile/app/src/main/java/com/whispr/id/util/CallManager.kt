package com.whispr.id.util

import android.content.Context
import com.whispr.id.network.ApiClient
import com.whispr.id.network.TokenStore
import kotlinx.coroutines.*
import okhttp3.*
import org.webrtc.*
import org.json.JSONObject

/**
 * WebRTC voice call manager (webrtc-sdk 104 API).
 * PeerConnection + local audio + STUN + signaling over /ws/call/{token}.
 */
object CallManager {

    interface Listener {
        fun onConnecting()
        fun onRinging()
        fun onConnected()
        fun onRemoteIce()
        fun onCallEnded()
        fun onError(msg: String)
    }

    var listener: Listener? = null

    private var pc: PeerConnection? = null
    private var localAudio: AudioTrack? = null
    private var audioSource: AudioSource? = null
    private var ws: WebSocket? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var activeCallId: String? = null
    private var peerUserId: String? = null
    private var isInitiator = false
    private var contextRef: android.content.Context? = null
    private var audioManager: android.media.AudioManager? = null
    private var prevAudioMode: Int = android.media.AudioManager.MODE_NORMAL

    fun isInCall(): Boolean = activeCallId != null

    /** Route audio to the call: set communication mode + earpiece by default. */
    private fun startAudioSession(context: Context) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            audioManager = am
            prevAudioMode = am.mode
            am.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
            am.isSpeakerphoneOn = false // earpiece by default
            @Suppress("DEPRECATION")
            am.requestAudioFocus(null, android.media.AudioManager.STREAM_VOICE_CALL,
                android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        } catch (_: Exception) {}
    }

    private fun stopAudioSession() {
        try {
            audioManager?.let { am ->
                am.mode = prevAudioMode
                am.isSpeakerphoneOn = false
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
            }
        } catch (_: Exception) {}
        audioManager = null
    }

    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        // Whispr self-hosted TURN (Oracle) — relay for calls across different NATs
        PeerConnection.IceServer.builder("turn:129.225.1.184:3478?transport=udp")
            .setUsername("whispr")
            .setPassword("wh1spr_turn_9847cb06")
            .createIceServer(),
        PeerConnection.IceServer.builder("turn:129.225.1.184:3478?transport=tcp")
            .setUsername("whispr")
            .setPassword("wh1spr_turn_9847cb06")
            .createIceServer()
    )

    private var factoryInitialized = false

    private fun initFactory(context: Context) {
        if (factoryInitialized) return
        try {
            System.loadLibrary("jingle_peerconnection_so")
        } catch (_: Throwable) {
            // Library is usually loaded automatically by PeerConnectionFactory;
            // do not crash here — real init below handles it.
        }
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )
        factoryInitialized = true
    }

    private fun buildObserver(): PeerConnection.Observer = object : PeerConnection.Observer {
        override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
            when (state) {
                // Only mark "active" when ICE actually connects (real media path)
                PeerConnection.IceConnectionState.CONNECTED,
                PeerConnection.IceConnectionState.COMPLETED -> listener?.onConnected()
                PeerConnection.IceConnectionState.DISCONNECTED,
                PeerConnection.IceConnectionState.FAILED,
                PeerConnection.IceConnectionState.CLOSED -> {
                    contextRef?.let { endCall(it, notifyPeer = true) }
                }
                else -> {}
            }
        }
        override fun onIceConnectionReceivingChange(receiving: Boolean) {}
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
        override fun onIceCandidate(candidate: IceCandidate?) {
            candidate ?: return
            sendSignal(JSONObject().apply {
                put("type", "ice")
                put("target_user_id", peerUserId)
                put("candidate", candidate.sdp)
                put("sdp_mid", candidate.sdpMid)
                put("sdp_mline_index", candidate.sdpMLineIndex)
            })
        }
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
        // NOTE: do NOT fire onConnected() here — a remote track can be added
        // during negotiation before ICE connectivity is established, which made
        // the call show "connected" with a running timer prematurely.
        override fun onAddStream(stream: MediaStream?) {}
        override fun onRemoveStream(stream: MediaStream?) {}
        override fun onDataChannel(channel: DataChannel?) {}
        override fun onRenegotiationNeeded() {}
        override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
    }

    private fun createPcWithAudio(context: Context): PeerConnection? {
        initFactory(context)
        val factory = PeerConnectionFactory.builder()
            .setOptions(PeerConnectionFactory.Options())
            .createPeerConnectionFactory()
        audioSource = factory.createAudioSource(MediaConstraints())
        localAudio = factory.createAudioTrack("whispr_audio", audioSource)
        localAudio?.setEnabled(true)
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        // Anonymity: force ALL media through the TURN relay so the peer never
        // sees the caller's real IP address (no direct P2P host/srflx candidates).
        rtcConfig.iceTransportsType = PeerConnection.IceTransportsType.RELAY
        val conn = factory.createPeerConnection(rtcConfig, buildObserver())
        conn?.addTrack(localAudio, listOf("stream1"))
        conn?.setAudioPlayout(true)
        return conn
    }

    private fun makeAudioConstraints(): MediaConstraints = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
    }

    private fun emptyObserver() = object : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onSetFailure(e: String?) {}
        override fun onCreateFailure(e: String?) {}
    }

    /** Outgoing call. */
    fun startCall(context: Context, callId: String, peerId: String) {
        if (activeCallId != null) { listener?.onError("Already in a call"); return }
        contextRef = context.applicationContext
        activeCallId = callId
        peerUserId = peerId
        isInitiator = true
        listener?.onConnecting()

        pc = createPcWithAudio(context)
        startAudioSession(context)

        connectSignaling(context) {
            // Offer created when callee sends "ready" handshake
        }
    }

    /** Answer incoming call. */
    fun answerCall(context: Context, callId: String, peerId: String, remoteOfferSdp: String?) {
        contextRef = context.applicationContext
        activeCallId = callId
        peerUserId = peerId
        isInitiator = false
        listener?.onConnecting()

        pc = createPcWithAudio(context)
        startAudioSession(context)

        if (remoteOfferSdp != null) {
            pc?.setRemoteDescription(object : SdpObserver {
                override fun onCreateSuccess(desc: SessionDescription?) {}
                override fun onSetSuccess() {
                    pc?.createAnswer(object : SdpObserver {
                        override fun onCreateSuccess(desc: SessionDescription?) {
                            desc ?: return
                            pc?.setLocalDescription(emptyObserver(), desc)
                            sendSignal(JSONObject().apply {
                                put("type", "answer")
                                put("target_user_id", peerUserId)
                                put("sdp", desc.description)
                            })
                        }
                        override fun onSetSuccess() {}
                        override fun onSetFailure(e: String?) { listener?.onError("Answer failed: $e") }
                        override fun onCreateFailure(e: String?) { listener?.onError("Answer failed: $e") }
                    }, makeAudioConstraints())
                }
                override fun onSetFailure(e: String?) { listener?.onError("Set remote failed: $e") }
                override fun onCreateFailure(e: String?) { listener?.onError("Create remote failed: $e") }
            }, SessionDescription(SessionDescription.Type.OFFER, remoteOfferSdp))
        } else {
            listener?.onRinging()
        }

        connectSignaling(context) {
            sendSignal(JSONObject().apply {
                put("type", "ready")
                put("target_user_id", peerUserId)
            })
        }
    }

    /** Incoming signaling. */
    fun handleSignal(message: String) {
        try {
            val json = JSONObject(message)
            when (json.optString("type")) {
                "ready" -> {
                    if (isInitiator) {
                        pc?.createOffer(object : SdpObserver {
                            override fun onCreateSuccess(desc: SessionDescription?) {
                                desc ?: return
                                pc?.setLocalDescription(emptyObserver(), desc)
                                sendSignal(JSONObject().apply {
                                    put("type", "offer")
                                    put("target_user_id", peerUserId)
                                    put("sdp", desc.description)
                                })
                                listener?.onRinging()
                            }
                            override fun onSetSuccess() {}
                            override fun onSetFailure(e: String?) { listener?.onError("Offer failed: $e") }
                            override fun onCreateFailure(e: String?) { listener?.onError("Offer failed: $e") }
                        }, makeAudioConstraints())
                    }
                }
                "offer" -> {
                    val sdp = json.optString("sdp")
                    pc ?: return
                    pc?.setRemoteDescription(object : SdpObserver {
                        override fun onCreateSuccess(desc: SessionDescription?) {}
                        override fun onSetSuccess() {
                            pc?.createAnswer(object : SdpObserver {
                                override fun onCreateSuccess(desc: SessionDescription?) {
                                    desc ?: return
                                    pc?.setLocalDescription(emptyObserver(), desc)
                                    sendSignal(JSONObject().apply {
                                        put("type", "answer")
                                        put("target_user_id", peerUserId)
                                        put("sdp", desc.description)
                                    })
                                }
                                override fun onSetSuccess() {}
                                override fun onSetFailure(e: String?) {}
                                override fun onCreateFailure(e: String?) {}
                            }, makeAudioConstraints())
                        }
                        override fun onSetFailure(e: String?) { listener?.onError("Remote offer failed: $e") }
                        override fun onCreateFailure(e: String?) {}
                    }, SessionDescription(SessionDescription.Type.OFFER, sdp))
                }
                "answer" -> {
                    val sdp = json.optString("sdp")
                    pc?.setRemoteDescription(object : SdpObserver {
                        override fun onCreateSuccess(desc: SessionDescription?) {}
                        // Setting the remote answer does NOT mean media is flowing —
                        // wait for onIceConnectionChange(CONNECTED) to mark active.
                        override fun onSetSuccess() {}
                        override fun onSetFailure(e: String?) { listener?.onError("Set answer failed: $e") }
                        override fun onCreateFailure(e: String?) {}
                    }, SessionDescription(SessionDescription.Type.ANSWER, sdp))
                }
                "ice" -> {
                    val candidate = json.optString("candidate")
                    val mid = json.optString("sdp_mid")
                    val mline = json.optInt("sdp_mline_index")
                    if (candidate.isNotBlank()) {
                        pc?.addIceCandidate(IceCandidate(mid, mline, candidate))
                        listener?.onRemoteIce()
                    }
                }
                "call_ended" -> {
                    scope.launch { listener?.onCallEnded() }
                }
            }
        } catch (_: Exception) {}
    }

    fun setMuted(muted: Boolean) { localAudio?.setEnabled(!muted) }

    fun setSpeaker(enabled: Boolean) {
        try {
            val am = contextRef?.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager ?: return
            am.mode = if (enabled) android.media.AudioManager.MODE_NORMAL else android.media.AudioManager.MODE_IN_COMMUNICATION
            am.isSpeakerphoneOn = enabled
        } catch (_: Exception) {}
    }

    fun endCall(context: Context, notifyPeer: Boolean = false) {
        try {
            if (notifyPeer && activeCallId != null) {
                scope.launch {
                    try { ApiClient.api.endCall(activeCallId!!) } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
        try { pc?.close() } catch (_: Exception) {}
        try { localAudio?.setEnabled(false) } catch (_: Exception) {}
        try { audioSource?.dispose() } catch (_: Exception) {}
        try { ws?.close(1000, "call_end") } catch (_: Exception) {}
        stopAudioSession()
        pc = null; localAudio = null; audioSource = null; ws = null
        activeCallId = null; peerUserId = null; isInitiator = false
    }

    private fun connectSignaling(context: Context, onReady: () -> Unit) {
        val token = TokenStore.getTokenBlocking(context)
        val url = ApiClient.getWsUrl("/ws/call/$token")
        val client = ApiClient.okHttpClient
        val request = Request.Builder().url(url).build()
        ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                onReady()
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                handleSignal(text)
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                listener?.onError("Signaling failed: ${t.message}")
            }
        })
    }

    private fun sendSignal(json: JSONObject) {
        ws?.send(json.toString())
    }
}
