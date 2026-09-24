package com.example.divide.sync.protocol

import com.google.gson.Gson

/**
 * Converts Kotlin objects into bytes that Nearby Connections can send,
 * and converts received bytes back into Kotlin objects.
 */
class MessageCodec {

    private val gson = Gson()

    fun encode(message: NetworkMessage): ByteArray {
        val json = gson.toJson(message)
        return json.toByteArray(Charsets.UTF_8)
    }

    fun decode(bytes: ByteArray): NetworkMessage {
        val json = bytes.toString(Charsets.UTF_8)
        return gson.fromJson(json, NetworkMessage::class.java)
    }

    fun <T> toPayload(value: T): String {
        return gson.toJson(value)
    }

    fun <T> fromPayload(
        payload: String,
        clazz: Class<T>
    ): T {
        return gson.fromJson(payload, clazz)
    }
}
