package com.bulbainvest.domain.external

import com.bulbainvest.domain.config.MailConfig
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import org.slf4j.LoggerFactory
import java.util.Properties

class MailService(private val cfg: MailConfig) {
    private val log = LoggerFactory.getLogger(MailService::class.java)

    fun sendAuthCode(to: String, code: String) {
        val props = Properties().apply {
            put("mail.smtp.host", cfg.host)
            put("mail.smtp.port", cfg.port.toString())
            put("mail.smtp.auth", (cfg.user != null).toString())
            put("mail.smtp.starttls.enable", "false")
        }
        val session = if (cfg.user != null && cfg.password != null) {
            Session.getInstance(props, object : jakarta.mail.Authenticator() {
                override fun getPasswordAuthentication() =
                    PasswordAuthentication(cfg.user, cfg.password)
            })
        } else {
            Session.getInstance(props)
        }

        val msg = MimeMessage(session).apply {
            setFrom(InternetAddress(cfg.from))
            setRecipient(Message.RecipientType.TO, InternetAddress(to))
            subject = "BulbaInvest auth code"
            setText("Your login code: $code\nValid for 5 minutes.")
        }
        try {
            Transport.send(msg)
            log.info("Auth code sent to {}", to)
        } catch (e: Exception) {
            log.error("Failed to send mail to {}: {}", to, e.message)
            throw e
        }
    }
}
