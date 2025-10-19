package stirling.software.proprietary.security.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.multipart.MultipartFile;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import stirling.software.common.model.ApplicationProperties;
import stirling.software.proprietary.security.model.api.Email;

@ExtendWith(MockitoExtension.class)
public class EmailServiceTest {

    @Mock private JavaMailSender mailSender;

    @Mock private ApplicationProperties applicationProperties;

    @Mock private ApplicationProperties.Mail mailProperties;

    @Mock private MultipartFile fileInput;

    @InjectMocks private EmailService emailService;

    @Test
    void testSendEmailWithAttachment() throws MessagingException {
        // Mock the values returned by ApplicationProperties
        Mockito.when(applicationProperties.getMail()).thenReturn(mailProperties);
        Mockito.when(mailProperties.getFrom()).thenReturn("no-reply@stirling-software.com");

        // Create a mock Email object
        Email email = new Email();
        email.setTo("test@example.com");
        email.setSubject("Test Email");
        email.setBody("This is a test email.");
        email.setFileInput(fileInput);

        // Mock MultipartFile behavior
        Mockito.when(fileInput.getOriginalFilename()).thenReturn("testFile.txt");

        // Mock MimeMessage
        MimeMessage mimeMessage = Mockito.mock(MimeMessage.class);

        // Configure mailSender to return the mocked MimeMessage
        Mockito.when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        // Call the service method
        emailService.sendEmailWithAttachment(email);

        // Verify that the email was sent using mailSender
        Mockito.verify(mailSender).send(mimeMessage);
    }

    @Test
    void testSendEmailWithAttachmentThrowsExceptionForMissingFilename() {
        Email email = new Email();
        email.setTo("test@example.com");
        email.setSubject("Test Email");
        email.setBody("This is a test email.");
        email.setFileInput(fileInput);

        Mockito.when(fileInput.isEmpty()).thenReturn(false);
        Mockito.when(fileInput.getOriginalFilename()).thenReturn("");

        try {
            emailService.sendEmailWithAttachment(email);
            Assertions.fail("Expected MessagingException to be thrown");
        } catch (MessagingException e) {
            Assertions.assertEquals("An attachment is required to send the email.", e.getMessage());
        }
    }

    @Test
    void testSendEmailWithAttachmentThrowsExceptionForMissingFilenameNull() {
        Email email = new Email();
        email.setTo("test@example.com");
        email.setSubject("Test Email");
        email.setBody("This is a test email.");
        email.setFileInput(fileInput);

        Mockito.when(fileInput.isEmpty()).thenReturn(false);
        Mockito.when(fileInput.getOriginalFilename()).thenReturn(null);

        try {
            emailService.sendEmailWithAttachment(email);
            Assertions.fail("Expected MessagingException to be thrown");
        } catch (MessagingException e) {
            Assertions.assertEquals("An attachment is required to send the email.", e.getMessage());
        }
    }

    @Test
    void testSendEmailWithAttachmentThrowsExceptionForMissingFile() {
        Email email = new Email();
        email.setTo("test@example.com");
        email.setSubject("Test Email");
        email.setBody("This is a test email.");
        email.setFileInput(fileInput);

        Mockito.when(fileInput.isEmpty()).thenReturn(true);

        try {
            emailService.sendEmailWithAttachment(email);
            Assertions.fail("Expected MessagingException to be thrown");
        } catch (MessagingException e) {
            Assertions.assertEquals("An attachment is required to send the email.", e.getMessage());
        }
    }

    @Test
    void testSendEmailWithAttachmentThrowsExceptionForMissingFileNull() {
        Email email = new Email();
        email.setTo("test@example.com");
        email.setSubject("Test Email");
        email.setBody("This is a test email.");
        email.setFileInput(null); // Missing file

        try {
            emailService.sendEmailWithAttachment(email);
            Assertions.fail("Expected MessagingException to be thrown");
        } catch (MessagingException e) {
            Assertions.assertEquals("An attachment is required to send the email.", e.getMessage());
        }
    }

    @Test
    void testSendEmailWithAttachmentThrowsExceptionForInvalidAddressNull() {
        Email email = new Email();
        email.setTo(null); // Invalid address
        email.setSubject("Test Email");
        email.setBody("This is a test email.");
        email.setFileInput(fileInput);

        try {
            emailService.sendEmailWithAttachment(email);
            Assertions.fail("Expected MailSendException to be thrown");
        } catch (MessagingException e) {
            Assertions.assertEquals("Invalid Addresses", e.getMessage());
        }
    }

    @Test
    void testSendEmailWithAttachmentThrowsExceptionForInvalidAddressEmpty() {
        Email email = new Email();
        email.setTo(""); // Invalid address
        email.setSubject("Test Email");
        email.setBody("This is a test email.");
        email.setFileInput(fileInput);

        try {
            emailService.sendEmailWithAttachment(email);
            Assertions.fail("Expected MailSendException to be thrown");
        } catch (MessagingException e) {
            Assertions.assertEquals("Invalid Addresses", e.getMessage());
        }
    }
}
