package com.uvindu.linuxsyncandroid.domain.model

data class PhoneNotification(
    val id: String,
    val appName: String,
    val title: String,
    val body: String,
    val isReplyable: Boolean = false,
    val replyKey: String? = null
)
