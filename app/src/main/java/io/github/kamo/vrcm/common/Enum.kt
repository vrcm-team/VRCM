package io.github.kamo.vrcm.common

import com.google.gson.JsonDeserializer
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializer

enum class  UserStatus(val type: String){
    Online("active"),
    JoinMe("join me"),
    AskMe("ask me"),
    Busy("busy"),
    Offline("offline");

    companion object {
        val Deserializer = JsonDeserializer { json, _, _ ->
            entries.first { it.type == json.asString }
        }
        val Serializer = JsonSerializer<UserStatus> { src, _, _ ->
            JsonPrimitive(src.type)
        }
    }
}