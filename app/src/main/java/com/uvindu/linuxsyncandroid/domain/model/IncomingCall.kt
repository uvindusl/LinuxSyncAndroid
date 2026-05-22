package com.uvindu.linuxsyncandroid.domain.model

data class IncomingCall(
    val callId: String,
    val callerName: String,
    val callerNumber: String,
    val state: CallState = CallState.RINGING
)

enum class CallState { RINGING, ACCEPTED, DECLINED, ENDED }