package com.tkrz.qrtix.data.cloud

import android.util.Base64
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.model.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Properties
import javax.inject.Inject
import javax.inject.Singleton
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

@Singleton
class GmailService @Inject constructor(
    private val credentialManager: GoogleCredentialManager
) {
    private val jsonFactory = GsonFactory.getDefaultInstance()
    private val httpTransport = GoogleNetHttpTransport.newTrustedTransport()

    private val gmailService: Gmail by lazy {
        Gmail.Builder(httpTransport, jsonFactory, credentialManager.getCredential())
            .setApplicationName("QRTix")
            .build()
    }

    suspend fun sendEmailWithAttachment(
        to: String,
        subject: String,
        htmlBody: String,
        attachments: List<Pair<ByteArray, String>>
    ): Boolean = withContext(Dispatchers.IO) {
        withRetry {
            val props = Properties()
            val session = Session.getDefaultInstance(props, null)
            val email = MimeMessage(session)

            email.setFrom(InternetAddress("me"))
            email.addRecipient(javax.mail.Message.RecipientType.TO, InternetAddress(to))
            email.subject = subject

            val multipart = MimeMultipart()

            // Body part
            val bodyPart = MimeBodyPart()
            bodyPart.setContent(htmlBody, "text/html; charset=utf-8")
            multipart.addBodyPart(bodyPart)

            // Attachment parts
            attachments.forEach { (bytes, fileName) ->
                val attachmentPart = MimeBodyPart()
                val dataSource = javax.mail.util.ByteArrayDataSource(bytes, "image/png")
                attachmentPart.dataHandler = javax.activation.DataHandler(dataSource)
                attachmentPart.fileName = fileName
                multipart.addBodyPart(attachmentPart)
            }

            email.setContent(multipart)

            val buffer = ByteArrayOutputStream()
            email.writeTo(buffer)
            val rawMessageBytes = buffer.toByteArray()
            val encodedEmail = Base64.encodeToString(rawMessageBytes, Base64.URL_SAFE or Base64.NO_WRAP)

            val message = Message()
            message.raw = encodedEmail

            gmailService.users().messages().send("me", message).execute()
            true
        }
    }

    private suspend fun <T> withRetry(times: Int = 3, initialDelay: Long = 1000, block: suspend () -> T): T {
        var currentDelay = initialDelay
        repeat(times - 1) {
            try {
                return block()
            } catch (e: Exception) {
                // Ignore and retry
                delay(currentDelay)
                currentDelay *= 2
            }
        }
        return block()
    }
}
