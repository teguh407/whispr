package com.whispr.id.util

import android.content.Context
import com.whispr.id.network.ApiClient
import com.whispr.id.network.TokenStore
import kotlinx.coroutines.*
import okhttp3.*
import org.webrtc.*
import org.json.JSONObject

/**
 * WebRTC voice call manager.
 * Handles: PeerConnection lifecycle, local audio capture, ICE/STUN,
 * and signaling over the existing /ws/call/{token} WebSocket.
 */
object CallManager {

    // ── Callback surface for UI ──
    interface Listener {
        fun onConnecting()
        fun onRinging()                 // offer sent, waiting for answer
        fun onConnected()               // media flowing (call active)
        fun onRemoteIce()               // ICE received (cosmetic)
        fun onCallEnded()
        fun onError(msg: String)
    }

    var listener: Listener? = null

    // ── Peer state ──
    private var pc: PeerConnection? = null
    private var localAudio: AudioTrack? = null
    private var audioSource: AudioSource? = null
    private var ws: WebSocket? = null
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var activeCallId: String? = null
    private var peerUserId: String? = null
    private var isInitiator = false
    private var signalingConnected = false
    private var pendingIce: MutableList<JSONObject> = mutableListOf()
    private var contextRef: android.content.Context? = null

    fun isInCall(): Boolean = activeCallId != null

    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
    )

    private fun initFactory(context: Context) {
        if (!PeerConnectionFactory.initializationComplete) {
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context)
                    .setEnableInternalTracer(false)
                    .createInitializationOptions()
            )
        }
    }

    /**
     * Start an outgoing call. Connects signaling, creates peer connection with local audio,
     * sends an SDP offer to the peer.
     */
    fun startCall(context: Context, callId: String, peerId: String) {
        if (activeCallId != null) { listener?.onError("Already in a call"); return }
        contextRef = context.applicationContext
        activeCallId = callId
        peerUserId = peerId
        isInitiator = true
        pendingIce.clear()
        listener?.onConnecting()

        initFactory(context)
        // Build factory with audio only
        val factory = PeerConnectionFactory.builder()
            .setOptions(PeerConnectionFactory.Options())
            .createPeerConnectionFactory()
        val mediaConstraints = MediaConstraints()
        // Create local audio track
        audioSource = factory.createAudioSource(MediaConstraints())
        localAudio = factory.createAudioTrack("whispr_audio", audioSource)
        localAudio?.setEnabled(true)

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY

        pc = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
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
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> listener?.onConnected()
                    PeerConnection.IceConnectionState.DISCONNECTED,
                    PeerConnection.IceConnectionState.FAILED -> endCall(contextRef!!, notifyPeer = true)
                    else -> {}
                }
            }
            override fun onAddStream(stream: MediaStream?) { listener?.onConnected() }
            override fun onTrack(track: RtpTransceiver?) { listener?.onConnected() }
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(channel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) { listener?.onConnected() }
        })

        pc?.addTrack(localAudio, listOf("stream1"))
        pc?.setAudioPlayout(true)

        // Connect signaling WS
        connectSignaling(context) {
            // Wait for callee "ready" handshake before creating offer
            // (offer is created in handleSignal("ready"))
        }
    }

    /**
     * Answer an incoming call. Sets remote offer (if delivered via signaling),
     * creates SDP answer, sends it back to caller.
     */
    fun answerCall(context: Context, callId: String, peerId: String, remoteOfferSdp: String?) {
        contextRef = context.applicationContext
        activeCallId = callId
        peerUserId = peerId
        isInitiator = false
        pendingIce.clear()
        listener?.onConnecting()

        initFactory(context)
        val factory = PeerConnectionFactory.builder()
            .setOptions(PeerConnectionFactory.Options())
            .createPeerConnectionFactory()
        audioSource = factory.createAudioSource(MediaConstraints())
        localAudio = factory.createAudioTrack("whispr_audio", audioSource)
        localAudio?.setEnabled(true)

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        pc = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
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
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> listener?.onConnected()
                    PeerConnection.IceConnectionState.DISCONNECTED,
                    PeerConnection.IceConnectionState.FAILED -> endCall(contextRef!!, notifyPeer = true)
                    else -> {}
                }
            }
            override fun onAddStream(stream: MediaStream?) { listener?.onConnected() }
            override fun onTrack(track: RtpTransceiver?) { listener?.onConnected() }
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(channel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) { listener?.onConnected() }
        })

        pc?.addTrack(localAudio, listOf("stream1"))
        pc?.setAudioPlayout(true)

        if (remoteOfferSdp != null) {
            pc?.setRemoteDescription(object : SdpObserver {
                override fun onCreateSuccess(desc: SessionDescription?) {}
                override fun onSetSuccess() {
                    val constraints = MediaConstraints().apply {
                        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
                    }
                    pc?.createAnswer(object : SdpObserver {
                        override fun onCreateSuccess(desc: SessionDescription) {
                            pc?.setLocalDescription(object : SdpObserver {
                                override fun onCreateSuccess(desc: SessionDescription?) {}
                                override fun onSetFailure(e: String?) {}
                                override fun onCreateFailure(e: String?) {}
                                override fun onSetSuccess() {}
                            }, desc)
                            sendSignal(JSONObject().apply {
                                put("type", "answer")
                                put("target_user_id", peerUserId)
                                put("sdp", desc.description)
                            })
                        }
                        override fun onSetSuccess() {}
                        override fun onSetFailure(e: String?) { listener?.onError("Answer failed: $e") }
                        override fun onCreateFailure(e: String?) { listener?.onError("Answer failed: $e") }
                    }, constraints)
                }
                override fun onSetFailure(e: String?) { listener?.onError("Set remote failed: $e") }
                override fun onCreateFailure(e: String?) { listener?.onError("Create remote failed: $e") }
                override fun onCreateSuccess(desc: SessionDescription?) {}
            }, SessionDescription(SessionDescription.Type.OFFER, remoteOfferSdp))
        } else {
            listener?.onRinging()
        }

        connectSignaling(context) {
            // Handshake: tell caller we're here, so they can create the offer
            sendSignal(JSONObject().apply {
                put("type", "ready")
                put("target_user_id", peerUserId)
            })
        }
    }

    /** Handle an incoming signaling message from the WS. */
    fun handleSignal(message: String) {
        try {
            val json = JSONObject(message)
            when (json.optString("type")) {
                "ready" -> {
                    // Callee is connected — create and send the offer (initiator only)
                    if (isInitiator) {
                        val constraints = MediaConstraints().apply {
                            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
                        }
                        pc?.createOffer(object : SdpObserver {
                            override fun onCreateSuccess(desc: SessionDescription) {
                                pc?.setLocalDescription(object : SdpObserver {
                                    override fun onCreateSuccess(desc: SessionDescription?) {}
                                    override fun onSetFailure(e: String?) {}
                                    override fun onCreateFailure(e: String?) {}
                                    override fun onSetSuccess() {}
                                }, desc)
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
                        }, constraints)
                    }
                }
                "offer" -> {
                    val sdp = json.optString("sdp")
                    pc ?: return
                    pc?.setRemoteDescription(object : SdpObserver {
                        override fun onCreateSuccess(desc: SessionDescription?) {}
                        override fun onSetSuccess() {
                            val constraints = MediaConstraints().apply {
                                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
                            }
                            pc?.createAnswer(object : SdpObserver {
                                override fun onCreateSuccess(desc: SessionDescription) {
                                    pc?.setLocalDescription(object : SdpObserver {
                                        override fun onCreateSuccess(desc: SessionDescription?) {}
                                        override fun onSetFailure(e: String?) {}
                                        override fun onCreateFailure(e: String?) {}
                                        override fun onSetSuccess() {}
                                    }, desc)
                                    sendSignal(JSONObject().apply {
                                        put("type", "answer")
                                        put("target_user_id", peerUserId)
                                        put("sdp", desc.description)
                                    })
                                }
                                override fun onSetSuccess() {}
                                override fun onSetFailure(e: String?) {}
                                override fun onCreateFailure(e: String?) {}
                            }, constraints)
                        }
                        override fun onSetFailure(e: String?) { listener?.onError("Remote offer failed: $e") }
                        override fun onCreateFailure(e: String?) {}
                        override fun onCreateSuccess(desc: SessionDescription?) {}
                    }, SessionDescription(SessionDescription.Type.OFFER, sdp))
                }
                "answer" -> {
                    val sdp = json.optString("sdp")
                    pc?.setRemoteDescription(object : SdpObserver {
                        override fun onCreateSuccess(desc: SessionDescription?) {}
                        override fun onSetSuccess() { listener?.onConnected() }
                        override fun onSetFailure(e: String?) { listener?.onError("Set answer failed: $e") }
                        override fun onCreateFailure(e: String?) {}
                        override fun onCreateSuccess(desc: SessionDescription?) {}
                    }, SessionDescription(SessionDescription.Type.ANSWER, sdp))
                }
                "ice" -> {
                    val candidate = json.optString("candidate")
                    val mid = json.optString("sdp_mid")
                    val mline = json.optInt("sdp_mline_index")
                    if (candidate.isNotBlank()) {
                        val ice = IceCandidate(mid, mline, candidate)
                        pc?.addIceCandidate(ice)
                        listener?.onRemoteIce()
                    }
                }
                "call_ended" -> listener?.onCallEnded()
            }
        } catch (_: Exception) {}
    }

    /** Toggle mic mute. */
    fun setMuted(muted: Boolean) {
        localAudio?.setEnabled(!muted)
    }

    /** Toggle loudspeaker (audio routing). */
    fun setSpeaker(enabled: Boolean) {
        try {
            val am = contextRef?.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager ?: return
            am.mode = if (enabled) android.media.AudioManager.MODE_NORMAL else android.media.AudioManager.MODE_IN_COMMUNICATION
            am.isSpeakerphoneOn = enabled
        } catch (_: Exception) {}
    }

    /** End the call: close peer connection + WS, reset state. */
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
        pc = null; localAudio = null; audioSource = null; ws = null
        activeCallId = null; peerUserId = null; isInitiator = false
        pendingIce.clear()
    }

    // ── Signaling WS ──
    private fun connectSignaling(context: Context, onReady: () -> Unit) {
        val token = TokenStore.getToken(context)
        val url = ApiClient.getWsUrl("/ws/call/$token")
        val client = ApiClient.okHttpClient
        val request = Request.Builder().url(url).build()
        ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                signalingConnected = true
                onReady()
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                handleSignal(text)
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                listener?.onError("Signaling failed: ${t.message}")
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                signalingConnected = false
            }
        })
    }

    private fun sendSignal(json: JSONObject) {
        ws?.send(json.toString())
    }
}
