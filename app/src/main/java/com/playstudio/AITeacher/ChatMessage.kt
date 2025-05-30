package com.playstudio.aiteacher

import android.os.Parcel
import android.os.Parcelable
import android.text.SpannableString

// Make sure ChatFragment.Citation is accessible.
// It's better to define Citation as a top-level class or inside this file/package.
// For this example, I'll assume it's defined elsewhere and accessible.
// If not, move its definition here or to a common models file.
/*
data class Citation(
    val url: String,
    val title: String,
    val startIndex: Int,
    val endIndex: Int
) : Parcelable { // Make Citation Parcelable if ChatMessage is
    constructor(parcel: Parcel) : this(
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readInt(),
        parcel.readInt()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(url)
        parcel.writeString(title)
        parcel.writeInt(startIndex)
        parcel.writeInt(endIndex)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Citation> {
        override fun createFromParcel(parcel: Parcel): Citation {
            return Citation(parcel)
        }

        override fun newArray(size: Int): Array<Citation?> {
            return arrayOfNulls(size)
        }
    }
}
*/


data class ChatMessage(
    val id: String,
    val content: String, // Make content immutable (val) if possible. If it needs to change, DiffUtil will handle it.
    val isUser: Boolean,
    val isTyping: Boolean = false,
    val followUpQuestions: List<String> = emptyList(),
    val citations: List<com.playstudio.aiteacher.ChatFragment.Citation> = emptyList(), // Assuming ChatFragment.Citation is the correct type
    val timestamp: Long = System.currentTimeMillis(),
    val containsRichContent: Boolean = false,
    val isWebSearchResult: Boolean = false,// This determines if WebView or TextView is used
    // spannableContent: SpannableString? = null // Usually, Spannables are generated on-the-fly in onBindViewHolder or pre-processed.
    // Storing them directly in the data model can be complex for DiffUtil and Parcelable.
    // If you must store it, ensure it's handled in equals/hashCode and Parcelable.
    // For simplicity, I'll assume Spannables are generated when needed.
) : Parcelable {

    // Init block is fine
    init {
        require(id.isNotEmpty()) { "ID must not be empty" }
        // Content can be empty for typing indicator
        // require(content.isNotEmpty()) { "Content must not be empty" }
    }

    constructor(parcel: Parcel) : this(
        parcel.readString()!!, // id
        parcel.readString()!!, // content
        parcel.readByte() != 0.toByte(), // isUser
        parcel.readByte() != 0.toByte(), // isTyping
        parcel.createStringArrayList() ?: emptyList(), // followUpQuestions
        mutableListOf<com.playstudio.aiteacher.ChatFragment.Citation>().apply { // citations
            parcel.readList(this as List<*>, com.playstudio.aiteacher.ChatFragment.Citation::class.java.classLoader)
        },
        parcel.readLong(), // timestamp
        parcel.readByte() != 0.toByte() // containsRichContent
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(content)
        parcel.writeByte(if (isUser) 1 else 0)
        parcel.writeByte(if (isTyping) 1 else 0)
        parcel.writeStringList(followUpQuestions)
        parcel.writeList(citations as List<*>)
        parcel.writeLong(timestamp)
        parcel.writeByte(if (containsRichContent) 1 else 0)
    }

    override fun describeContents(): Int {
        return 0
    }

    // The default equals/hashCode from data class should be sufficient
    // if all relevant fields are in the primary constructor.

    // The copy() method is automatically generated by data classes.
    // You don't need to define it explicitly unless you want custom copy behavior.

    companion object CREATOR : Parcelable.Creator<ChatMessage> {
        override fun createFromParcel(parcel: Parcel): ChatMessage {
            return ChatMessage(parcel)
        }

        override fun newArray(size: Int): Array<ChatMessage?> {
            return arrayOfNulls(size)
        }
    }
}