package stirling.software.SPDF.service;

import java.security.PublicKey;
import java.security.cert.CertificateExpiredException;
import java.security.cert.X509Certificate;

import javax.security.auth.x500.X500Principal;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** Tests for the CertificateValidationService using mocked certificates. */
class CertificateValidationServiceTest {

    private CertificateValidationService validationService;
    private X509Certificate validCertificate;
    private X509Certificate expiredCertificate;

    @BeforeEach
    void setUp() throws Exception {
        validationService = new CertificateValidationService();

        // Create mock certificates
        validCertificate = Mockito.mock(X509Certificate.class);
        expiredCertificate = Mockito.mock(X509Certificate.class);

        // Set up behaviors for valid certificate
        Mockito.doNothing().when(validCertificate).checkValidity(); // No exception means valid

        // Set up behaviors for expired certificate
        Mockito.doThrow(new CertificateExpiredException("Certificate expired"))
                .when(expiredCertificate)
                .checkValidity();
    }

    @Test
    void testIsRevoked_ValidCertificate() {
        // When certificate is valid (not expired)
        boolean result = validationService.isRevoked(validCertificate);

        // Then it should not be considered revoked
        Assertions.assertFalse(result, "Valid certificate should not be considered revoked");
    }

    @Test
    void testIsRevoked_ExpiredCertificate() {
        // When certificate is expired
        boolean result = validationService.isRevoked(expiredCertificate);

        // Then it should be considered revoked
        Assertions.assertTrue(result, "Expired certificate should be considered revoked");
    }

    @Test
    void testValidateTrustWithCustomCert_Match() {
        // Create certificates with matching issuer and subject
        X509Certificate issuingCert = Mockito.mock(X509Certificate.class);
        X509Certificate signedCert = Mockito.mock(X509Certificate.class);

        // Create X500Principal objects for issuer and subject
        X500Principal issuerPrincipal = new X500Principal("CN=Test Issuer");

        // Mock the issuer of the signed certificate to match the subject of the issuing certificate
        Mockito.when(signedCert.getIssuerX500Principal()).thenReturn(issuerPrincipal);
        Mockito.when(issuingCert.getSubjectX500Principal()).thenReturn(issuerPrincipal);

        // When validating trust with custom cert
        boolean result = validationService.validateTrustWithCustomCert(signedCert, issuingCert);

        // Then validation should succeed
        Assertions.assertTrue(
                result, "Certificate with matching issuer and subject should validate");
    }

    @Test
    void testValidateTrustWithCustomCert_NoMatch() {
        // Create certificates with non-matching issuer and subject
        X509Certificate issuingCert = Mockito.mock(X509Certificate.class);
        X509Certificate signedCert = Mockito.mock(X509Certificate.class);

        // Create X500Principal objects for issuer and subject
        X500Principal issuerPrincipal = new X500Principal("CN=Test Issuer");
        X500Principal differentPrincipal = new X500Principal("CN=Different Name");

        // Mock the issuer of the signed certificate to NOT match the subject of the issuing
        // certificate
        Mockito.when(signedCert.getIssuerX500Principal()).thenReturn(issuerPrincipal);
        Mockito.when(issuingCert.getSubjectX500Principal()).thenReturn(differentPrincipal);

        // When validating trust with custom cert
        boolean result = validationService.validateTrustWithCustomCert(signedCert, issuingCert);

        // Then validation should fail
        Assertions.assertFalse(
                result, "Certificate with non-matching issuer and subject should not validate");
    }

    @Test
    void testValidateCertificateChainWithCustomCert_Success() throws Exception {
        // Setup mock certificates
        X509Certificate signedCert = Mockito.mock(X509Certificate.class);
        X509Certificate signingCert = Mockito.mock(X509Certificate.class);
        PublicKey publicKey = Mockito.mock(PublicKey.class);

        Mockito.when(signingCert.getPublicKey()).thenReturn(publicKey);

        // When verifying the certificate with the signing cert's public key, don't throw exception
        Mockito.doNothing().when(signedCert).verify(Mockito.any());

        // When validating certificate chain with custom cert
        boolean result =
                validationService.validateCertificateChainWithCustomCert(signedCert, signingCert);

        // Then validation should succeed
        Assertions.assertTrue(result, "Certificate chain with proper signing should validate");
    }

    @Test
    void testValidateCertificateChainWithCustomCert_Failure() throws Exception {
        // Setup mock certificates
        X509Certificate signedCert = Mockito.mock(X509Certificate.class);
        X509Certificate signingCert = Mockito.mock(X509Certificate.class);
        PublicKey publicKey = Mockito.mock(PublicKey.class);

        Mockito.when(signingCert.getPublicKey()).thenReturn(publicKey);

        // When verifying the certificate with the signing cert's public key, throw exception
        // Need to use a specific exception that verify() can throw
        Mockito.doThrow(new java.security.SignatureException("Verification failed"))
                .when(signedCert)
                .verify(Mockito.any());

        // When validating certificate chain with custom cert
        boolean result =
                validationService.validateCertificateChainWithCustomCert(signedCert, signingCert);

        // Then validation should fail
        Assertions.assertFalse(result, "Certificate chain with failed signing should not validate");
    }
}
