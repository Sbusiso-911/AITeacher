package com.playstudio.aiteacher

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.accounts.Account
import android.accounts.AccountManager
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.lang.Exception
import java.nio.charset.Charset
import java.util.Properties
import javax.mail.Session
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import javax.mail.Part // Ensure this import is correct

class EmailProviderHelper(private val context: Context) {

    companion object {
        const val EMAIL_PERMISSION_REQUEST_CODE = 1005
        const val EMAIL_PICK_REQUEST = 1006
    }

    private val accountManager: AccountManager by lazy { AccountManager.get(context) }

    fun hasEmailPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.GET_ACCOUNTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun getAvailableEmailAccounts(): List<Account> {
        if (!hasEmailPermissions()) {
            Log.w("EmailProviderHelper", "GET_ACCOUNTS permission not granted.")
            return emptyList()
        }
        try {
            return accountManager.accounts.filter {
                it.type.equals("com.google", ignoreCase = true) ||
                        it.type.contains("mail", ignoreCase = true) ||
                        it.type.contains("email", ignoreCase = true) ||
                        it.type.contains("exchange", ignoreCase = true) ||
                        it.type.equals("com.android.email", ignoreCase = true)
            }
        } catch (e: SecurityException) {
            Log.e("EmailProviderHelper", "SecurityException while getting accounts", e)
            return emptyList()
        }
    }

    fun extractEmailContent(intentData: Intent?, callback: (emailMessage: EmailMessage?) -> Unit) {
        val uri = intentData?.data
        if (uri == null) {
            Log.e("EmailProviderHelper", "URI is null, cannot extract content.")
            callback(null)
            return
        }

        Log.d("EmailProviderHelper", "Attempting to extract email from URI: $uri")

        CoroutineScope(Dispatchers.IO).launch {
            var extractedSubject: String? = null
            var extractedBody = "Could not extract email body."
            var extractedFrom: String? = null

            try {
                // Try to get a display name from the ContentResolver (often the subject)
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (displayNameIndex != -1) {
                            cursor.getString(displayNameIndex)?.let { dn ->
                                Log.d("EmailProviderHelper", "Display Name from ContentResolver: $dn")
                                // Heuristic: if it doesn't look like a common email filename, it might be the subject
                                if (!dn.endsWith(".eml", ignoreCase = true) &&
                                    !dn.endsWith(".msg", ignoreCase = true) &&
                                    dn.length > 5 && // Very short names are unlikely subjects
                                    !dn.contains("/") && !dn.contains("\\") // Path characters
                                ) {
                                    extractedSubject = dn
                                    Log.i("EmailProviderHelper", "Using display name as subject: $extractedSubject")
                                }
                            }
                        }
                    }
                }

                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    // Try parsing with JavaMail API (add dependency if not present)
                    // For a more robust solution, consider adding 'com.sun.mail:android-mail' and 'com.sun.mail:android-activation'
                    // This is a simplified in-place attempt which might not always work without the full library.
                    try {
                        val props = Properties()
                        val session = Session.getDefaultInstance(props, null)
                        val mimeMessage = MimeMessage(session, inputStream)

                        extractedSubject = mimeMessage.subject ?: extractedSubject // Prefer JavaMail subject
                        extractedFrom = mimeMessage.from?.joinToString { it.toString() } ?: "Unknown Sender"

                        val content = mimeMessage.content
                        if (content is String) {
                            extractedBody = content
                        } else if (content is MimeMultipart) {
                            extractedBody = getTextFromMimeMultipart(content)
                        } else {
                            // Fallback: try to read the stream as plain text if MimeMessage parsing fails or content is unusual
                            // This part is kept as a fallback from previous simple parsing
                            inputStream.reset() // Important: Reset stream if MimeMessage consumed it
                            extractedBody = readStreamAsText(inputStream)
                        }
                        Log.i("EmailProviderHelper", "JavaMail - Subject: $extractedSubject, From: $extractedFrom")

                    } catch (e: Exception) {
                        Log.w("EmailProviderHelper", "JavaMail parsing failed: ${e.message}. Falling back to simple parsing.")
                        // IMPORTANT: If JavaMail consumed the stream and an error occurred,
                        // we need to reopen it or ensure the stream was reset if possible.
                        // The `openInputStream` gives a new stream each time if `inputStream.reset()` isn't supported
                        // or if the first attempt fully consumed it.
                        // So, we re-open the stream for the fallback.
                        context.contentResolver.openInputStream(uri)?.use { fallbackStream ->
                            val (s, f, b) = parseEmailStreamSimple(fallbackStream)
                            if (extractedSubject == null) extractedSubject = s
                            if (extractedFrom == null) extractedFrom = f
                            extractedBody = b // Overwrite with simple parsing result if JavaMail failed badly
                        }
                    }
                } ?: run {
                    Log.e("EmailProviderHelper", "Could not open InputStream for URI: $uri")
                }

                val finalSubject = extractedSubject ?: "Subject Not Found"
                val finalFrom = extractedFrom ?: "Sender Not Found"

                val emailMessage = EmailMessage(finalSubject, extractedBody, finalFrom, null)
                Log.d("EmailProviderHelper", "Successfully extracted: Subject='${emailMessage.subject}', From='${emailMessage.from}', Body snippet='${emailMessage.body.take(100)}...'")
                withContext(Dispatchers.Main) {
                    callback(emailMessage)
                }

            } catch (e: Exception) {
                Log.e("EmailProviderHelper", "General error extracting email content: ${e.message}", e)
                val errorSubject = extractedSubject ?: "Error Parsing Email"
                val errorEmail = EmailMessage(
                    errorSubject,
                    "Could not parse email content. Details: ${e.message}\nBody may have been partially extracted or unavailable.",
                    extractedFrom ?: "Unknown",
                    null
                )
                withContext(Dispatchers.Main) {
                    callback(errorEmail)
                }
            }
        }
    }

    // Helper function for simple stream parsing (fallback)
    private fun parseEmailStreamSimple(inputStream: InputStream): Triple<String?, String?, String> {
        var subject: String? = null
        var from: String? = null
        val bodyLines = mutableListOf<String>()
        var headersParsed = false
        var inBody = false

        // Try to detect charset, default to UTF-8
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)) // Consider adding more robust charset detection

        try {
            reader.forEachLine { line ->
                if (!headersParsed) {
                    if (line.startsWith("Subject:", ignoreCase = true)) {
                        subject = line.substringAfter("Subject:").trim()
                    } else if (line.startsWith("From:", ignoreCase = true)) {
                        from = line.substringAfter("From:").trim()
                    } else if (line.isBlank()) {
                        headersParsed = true
                        inBody = true
                        return@forEachLine // continue to next line
                    }
                }
                if (inBody) {
                    bodyLines.add(line)
                }
            }
        } catch (e: Exception) {
            Log.e("EmailProviderHelper", "Error during simple stream parsing: ${e.message}")
            // return Triple(subject, from, "Error reading email body during simple parse.") // Optionally return error here
        }

        val body = bodyLines.joinToString("\n").trim()
        Log.d("EmailProviderHelper", "Simple Parse - Subject: $subject, From: $from, Body (first 50): ${body.take(50)}")
        return Triple(subject, from, if (body.isEmpty() && bodyLines.isNotEmpty()) bodyLines.joinToString("\n") else body.ifEmpty { "Body not found in simple parse." })
    }


    // Helper to attempt reading the stream as plain text
    private fun readStreamAsText(inputStream: InputStream): String {
        return try {
            // It's crucial the stream is at its beginning if MimeMessage didn't consume it or if it was reset.
            val baos = ByteArrayOutputStream()
            inputStream.copyTo(baos)
            baos.toString(Charsets.UTF_8.name()) // Or try to detect encoding
        } catch (e: Exception) {
            Log.e("EmailProviderHelper", "Failed to read stream as plain text: ${e.message}")
            "Could not read email stream as plain text."
        }
    }


    // Helper to extract text from MimeMultipart (used by JavaMail)
    @Throws(Exception::class)
    private fun getTextFromMimeMultipart(mimeMultipart: MimeMultipart): String {
        val result = StringBuilder()
        val count = mimeMultipart.count
        for (i in 0 until count) {
            val bodyPart = mimeMultipart.getBodyPart(i)
            Log.d("EmailProviderHelper", "Multipart part $i: Type: ${bodyPart.contentType}")
            if (bodyPart.isMimeType("text/plain")) {
                result.append(bodyPart.content.toString())
                // If plain text found, often good enough, but could concatenate HTML as text too
            } else if (bodyPart.isMimeType("text/html")) {
                // Simple extraction, for robust HTML to text, use a library like Jsoup
                val htmlContent = bodyPart.content.toString()
                // Basic HTML tag stripping (very naive)
                result.append("\n--- HTML Content (simplified) ---\n")
                result.append(htmlContent.replace(Regex("<.*?>"), " ").replace(Regex("\\s+"), " ").trim())
                result.append("\n--- End HTML Content ---")
            } else if (bodyPart.content is MimeMultipart) {
                result.append(getTextFromMimeMultipart(bodyPart.content as MimeMultipart))
            }
        }
        return result.toString().ifEmpty { "No suitable text part found in multipart email." }
    }
}