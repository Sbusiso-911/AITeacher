package com.playstudio.aiteacher // Ensure this matches your project's main package

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
// No need to import EmailMessage if it's in the same package.
// If EmailMessage is in a different sub-package, you'd import it here.
// import com.playstudio.aiteacher.EmailMessage // This line is correct if EmailMessage.kt is in this package

class EmailAdapter(
    private var emails: List<EmailMessage>,
    private val listener: OnEmailClickListener
) : RecyclerView.Adapter<EmailAdapter.EmailViewHolder>() {

    interface OnEmailClickListener {
        // Changed from uri: String to email: EmailMessage, as 'uri' is not in our EmailMessage
        fun onEmailClicked(email: EmailMessage)
    }

    inner class EmailViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val subjectView: TextView = itemView.findViewById(R.id.emailSubject)
        private val senderView: TextView = itemView.findViewById(R.id.emailSender)
        private val previewView: TextView = itemView.findViewById(R.id.emailPreview)
        private val dateView: TextView = itemView.findViewById(R.id.emailDate) // Assuming R.id.emailDate exists in item_email.xml

        fun bind(email: EmailMessage) {
            subjectView.text = email.subject
            senderView.text = email.from ?: "Unknown Sender" // Use 'from' field

            // Create a preview from the body
            previewView.text = if (email.body.length > 100) {
                email.body.substring(0, 100) + "..."
            } else {
                email.body
            }

            dateView.text = "N/A" // Date is not in our EmailMessage, set placeholder

            itemView.setOnClickListener { listener.onEmailClicked(email) }
        }
    }

    fun updateData(newEmails: List<EmailMessage>) {
        emails = newEmails
        notifyDataSetChanged() // Consider using DiffUtil for better performance
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmailViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_email, parent, false) // Ensure R.layout.item_email exists
        return EmailViewHolder(view)
    }

    override fun onBindViewHolder(holder: EmailViewHolder, position: Int) {
        holder.bind(emails[position])
    }

    override fun getItemCount() = emails.size
}