package com.playstudio.aiteacher

import android.os.Parcel
import android.os.Parcelable

data class ChatMessage(
    var content: String,
    val isUser: Boolean,
    var isTyping: Boolean = false,
    var followUpQuestions: List<String> = emptyList()
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readByte() != 0.toByte(),
        parcel.readByte() != 0.toByte(),
        parcel.createStringArrayList() ?: emptyList()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(content)
        parcel.writeByte(if (isUser) 1 else 0)
        parcel.writeByte(if (isTyping) 1 else 0)
        parcel.writeStringList(followUpQuestions)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<ChatMessage> {
        override fun createFromParcel(parcel: Parcel): ChatMessage {
            return ChatMessage(parcel)
        }

        override fun newArray(size: Int): Array<ChatMessage?> {
            return arrayOfNulls(size)
        }
    }
}