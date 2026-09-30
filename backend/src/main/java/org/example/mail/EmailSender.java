package org.example.mail;

import org.example.exception.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * 메일 발송. SMTP(spring.mail.host 등)가 설정돼 있으면 실제로 보내고,
 * 설정이 없고 개발 모드(app.mail.dev-mode=true)면 보내는 대신 서버 로그에 남긴다.
 * 운영(prod 프로필)에서는 개발 모드가 꺼져 있어서, SMTP 없이 발송을 시도하면 오류가 난다.
 */
@Component
public class EmailSender {

    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final boolean devMode;
    private final String from;

    public EmailSender(ObjectProvider<JavaMailSender> mailSenderProvider,
                       @Value("${app.mail.dev-mode:false}") boolean devMode,
                       @Value("${app.mail.from:no-reply@cleaneat.local}") String from) {
        this.mailSenderProvider = mailSenderProvider;
        this.devMode = devMode;
        this.from = from;
    }

    /** 실제 메일 서버 없이 동작 중인지 (이때만 화면에 인증번호를 보여준다) */
    public boolean isDevMode() {
        return mailSenderProvider.getIfAvailable() == null && devMode;
    }

    public void send(String to, String subject, String text) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            if (devMode) {
                log.warn("[개발 모드 - 메일 미발송] to={} subject={}\n{}", to, subject, text);
                return;
            }
            throw new ServiceUnavailableException("메일 발송 설정이 되어 있지 않습니다. 관리자에게 문의해주세요");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        try {
            sender.send(message);
        } catch (MailException e) {
            log.error("메일 발송 실패: to={}", to, e);
            throw new ServiceUnavailableException("인증 메일을 보내지 못했습니다. 잠시 후 다시 시도해주세요", e);
        }
    }
}
