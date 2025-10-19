package stirling.software.proprietary.security.service;

import java.util.Properties;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import stirling.software.common.model.ApplicationProperties;
import stirling.software.proprietary.security.configuration.MailConfig;

class MailConfigTest {

    private ApplicationProperties.Mail mailProps;

    @BeforeEach
    void initMailProperties() {
        mailProps = Mockito.mock(ApplicationProperties.Mail.class);
        Mockito.when(mailProps.getHost()).thenReturn("smtp.example.com");
        Mockito.when(mailProps.getPort()).thenReturn(587);
        Mockito.when(mailProps.getUsername()).thenReturn("user@example.com");
        Mockito.when(mailProps.getPassword()).thenReturn("password");
    }

    @Test
    void shouldConfigureJavaMailSenderWithCorrectProperties() {
        ApplicationProperties appProps = Mockito.mock(ApplicationProperties.class);
        Mockito.when(appProps.getMail()).thenReturn(mailProps);

        MailConfig config = new MailConfig(appProps);
        JavaMailSender sender = config.javaMailSender();

        Assertions.assertInstanceOf(JavaMailSenderImpl.class, sender);
        JavaMailSenderImpl impl = (JavaMailSenderImpl) sender;

        Properties props = impl.getJavaMailProperties();

        Assertions.assertAll(
                "SMTP configuration",
                () -> Assertions.assertEquals("smtp.example.com", impl.getHost()),
                () -> Assertions.assertEquals(587, impl.getPort()),
                () -> Assertions.assertEquals("user@example.com", impl.getUsername()),
                () -> Assertions.assertEquals("password", impl.getPassword()),
                () -> Assertions.assertEquals("UTF-8", impl.getDefaultEncoding()),
                () -> Assertions.assertEquals("true", props.getProperty("mail.smtp.auth")),
                () ->
                        Assertions.assertEquals(
                                "true", props.getProperty("mail.smtp.starttls.enable")));
    }
}
