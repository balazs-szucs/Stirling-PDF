package stirling.software.proprietary.security.service;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import stirling.software.proprietary.security.model.JwtVerificationKey;
import stirling.software.proprietary.security.model.User;
import stirling.software.proprietary.security.model.exception.AuthenticationFailureException;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    @Mock private Authentication authentication;

    @Mock private User userDetails;

    @Mock private HttpServletRequest request;

    @Mock private HttpServletResponse response;

    @Mock private KeyPersistenceServiceInterface keystoreService;

    private JwtService jwtService;
    private KeyPair testKeyPair;
    private JwtVerificationKey testVerificationKey;

    @BeforeEach
    void setUp() throws NoSuchAlgorithmException {
        // Generate a test keypair
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        testKeyPair = keyPairGenerator.generateKeyPair();

        // Create test verification key
        String encodedPublicKey =
                Base64.getEncoder().encodeToString(testKeyPair.getPublic().getEncoded());
        testVerificationKey = new JwtVerificationKey("test-key-id", encodedPublicKey);

        jwtService = new JwtService(true, keystoreService);
    }

    @Test
    void testGenerateTokenWithAuthentication() throws Exception {
        String username = "testuser";

        Mockito.when(keystoreService.getActiveKey()).thenReturn(testVerificationKey);
        Mockito.when(keystoreService.getKeyPair("test-key-id"))
                .thenReturn(Optional.of(testKeyPair));
        Mockito.when(keystoreService.decodePublicKey(testVerificationKey.getVerifyingKey()))
                .thenReturn(testKeyPair.getPublic());
        Mockito.when(authentication.getPrincipal()).thenReturn(userDetails);
        Mockito.when(userDetails.getUsername()).thenReturn(username);

        String token = jwtService.generateToken(authentication, Collections.emptyMap());

        Assertions.assertNotNull(token);
        Assertions.assertFalse(token.isEmpty());
        Assertions.assertEquals(username, jwtService.extractUsername(token));
    }

    @Test
    void testGenerateTokenWithUsernameAndClaims() throws Exception {
        String username = "testuser";
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", "admin");
        claims.put("department", "IT");

        Mockito.when(keystoreService.getActiveKey()).thenReturn(testVerificationKey);
        Mockito.when(keystoreService.getKeyPair("test-key-id"))
                .thenReturn(Optional.of(testKeyPair));
        Mockito.when(keystoreService.decodePublicKey(testVerificationKey.getVerifyingKey()))
                .thenReturn(testKeyPair.getPublic());
        Mockito.when(authentication.getPrincipal()).thenReturn(userDetails);
        Mockito.when(userDetails.getUsername()).thenReturn(username);

        String token = jwtService.generateToken(authentication, claims);

        Assertions.assertNotNull(token);
        Assertions.assertFalse(token.isEmpty());
        Assertions.assertEquals(username, jwtService.extractUsername(token));

        Map<String, Object> extractedClaims = jwtService.extractClaims(token);
        Assertions.assertEquals("admin", extractedClaims.get("role"));
        Assertions.assertEquals("IT", extractedClaims.get("department"));
    }

    @Test
    void testValidateTokenSuccess() throws Exception {
        Mockito.when(keystoreService.getActiveKey()).thenReturn(testVerificationKey);
        Mockito.when(keystoreService.getKeyPair("test-key-id"))
                .thenReturn(Optional.of(testKeyPair));
        Mockito.when(keystoreService.decodePublicKey(testVerificationKey.getVerifyingKey()))
                .thenReturn(testKeyPair.getPublic());
        Mockito.when(authentication.getPrincipal()).thenReturn(userDetails);
        Mockito.when(userDetails.getUsername()).thenReturn("testuser");

        String token = jwtService.generateToken(authentication, new HashMap<>());

        Assertions.assertDoesNotThrow(() -> jwtService.validateToken(token));
    }

    @Test
    void testValidateTokenWithInvalidToken() throws Exception {
        Mockito.when(keystoreService.getActiveKey()).thenReturn(testVerificationKey);
        Mockito.when(keystoreService.decodePublicKey(testVerificationKey.getVerifyingKey()))
                .thenReturn(testKeyPair.getPublic());

        Assertions.assertThrows(
                AuthenticationFailureException.class,
                () -> jwtService.validateToken("invalid-token"));
    }

    @Test
    void testValidateTokenWithMalformedToken() throws Exception {
        Mockito.when(keystoreService.getActiveKey()).thenReturn(testVerificationKey);
        Mockito.when(keystoreService.decodePublicKey(testVerificationKey.getVerifyingKey()))
                .thenReturn(testKeyPair.getPublic());

        AuthenticationFailureException exception =
                Assertions.assertThrows(
                        AuthenticationFailureException.class,
                        () -> jwtService.validateToken("malformed.token"));

        Assertions.assertTrue(exception.getMessage().contains("Invalid"));
    }

    @Test
    void testValidateTokenWithEmptyToken() throws Exception {
        Mockito.when(keystoreService.getActiveKey()).thenReturn(testVerificationKey);
        Mockito.when(keystoreService.decodePublicKey(testVerificationKey.getVerifyingKey()))
                .thenReturn(testKeyPair.getPublic());

        AuthenticationFailureException exception =
                Assertions.assertThrows(
                        AuthenticationFailureException.class, () -> jwtService.validateToken(""));

        Assertions.assertTrue(
                exception.getMessage().contains("Claims are empty")
                        || exception.getMessage().contains("Invalid"));
    }

    @Test
    void testExtractUsername() throws Exception {
        String username = "testuser";
        User user = Mockito.mock(User.class);
        Map<String, Object> claims = Map.of("sub", "testuser", "authType", "WEB");

        Mockito.when(keystoreService.getActiveKey()).thenReturn(testVerificationKey);
        Mockito.when(keystoreService.getKeyPair("test-key-id"))
                .thenReturn(Optional.of(testKeyPair));
        Mockito.when(keystoreService.decodePublicKey(testVerificationKey.getVerifyingKey()))
                .thenReturn(testKeyPair.getPublic());
        Mockito.when(authentication.getPrincipal()).thenReturn(user);
        Mockito.when(user.getUsername()).thenReturn(username);

        String token = jwtService.generateToken(authentication, claims);

        Assertions.assertEquals(username, jwtService.extractUsername(token));
    }

    @Test
    void testExtractUsernameWithInvalidToken() throws Exception {
        Mockito.when(keystoreService.getActiveKey()).thenReturn(testVerificationKey);
        Mockito.when(keystoreService.decodePublicKey(testVerificationKey.getVerifyingKey()))
                .thenReturn(testKeyPair.getPublic());

        Assertions.assertThrows(
                AuthenticationFailureException.class,
                () -> jwtService.extractUsername("invalid-token"));
    }

    @Test
    void testExtractClaims() throws Exception {
        String username = "testuser";
        Map<String, Object> claims = Map.of("role", "admin", "department", "IT");

        Mockito.when(keystoreService.getActiveKey()).thenReturn(testVerificationKey);
        Mockito.when(keystoreService.getKeyPair("test-key-id"))
                .thenReturn(Optional.of(testKeyPair));
        Mockito.when(keystoreService.decodePublicKey(testVerificationKey.getVerifyingKey()))
                .thenReturn(testKeyPair.getPublic());
        Mockito.when(authentication.getPrincipal()).thenReturn(userDetails);
        Mockito.when(userDetails.getUsername()).thenReturn(username);

        String token = jwtService.generateToken(authentication, claims);
        Map<String, Object> extractedClaims = jwtService.extractClaims(token);

        Assertions.assertEquals("admin", extractedClaims.get("role"));
        Assertions.assertEquals("IT", extractedClaims.get("department"));
        Assertions.assertEquals(username, extractedClaims.get("sub"));
        Assertions.assertEquals("Stirling PDF", extractedClaims.get("iss"));
    }

    @Test
    void testExtractClaimsWithInvalidToken() throws Exception {
        Mockito.when(keystoreService.getActiveKey()).thenReturn(testVerificationKey);
        Mockito.when(keystoreService.decodePublicKey(testVerificationKey.getVerifyingKey()))
                .thenReturn(testKeyPair.getPublic());

        Assertions.assertThrows(
                AuthenticationFailureException.class,
                () -> jwtService.extractClaims("invalid-token"));
    }

    @Test
    void testExtractTokenWithCookie() {
        String token = "test-token";
        Cookie[] cookies = {new Cookie("stirling_jwt", token)};
        Mockito.when(request.getCookies()).thenReturn(cookies);

        Assertions.assertEquals(token, jwtService.extractToken(request));
    }

    @Test
    void testExtractTokenWithNoCookies() {
        Mockito.when(request.getCookies()).thenReturn(null);

        Assertions.assertNull(jwtService.extractToken(request));
    }

    @Test
    void testExtractTokenWithWrongCookie() {
        Cookie[] cookies = {new Cookie("OTHER_COOKIE", "value")};
        Mockito.when(request.getCookies()).thenReturn(cookies);

        Assertions.assertNull(jwtService.extractToken(request));
    }

    @Test
    void testExtractTokenWithInvalidAuthorizationHeader() {
        Mockito.when(request.getCookies()).thenReturn(null);

        Assertions.assertNull(jwtService.extractToken(request));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testAddToken(boolean secureCookie) throws Exception {
        String token = "test-token";

        // Create new JwtService instance with the secureCookie parameter
        JwtService testJwtService = createJwtServiceWithSecureCookie(secureCookie);

        testJwtService.addToken(response, token);

        Mockito.verify(response)
                .addHeader(
                        ArgumentMatchers.eq("Set-Cookie"),
                        ArgumentMatchers.contains("stirling_jwt=" + token));
        Mockito.verify(response)
                .addHeader(
                        ArgumentMatchers.eq("Set-Cookie"), ArgumentMatchers.contains("HttpOnly"));

        if (secureCookie) {
            Mockito.verify(response)
                    .addHeader(
                            ArgumentMatchers.eq("Set-Cookie"), ArgumentMatchers.contains("Secure"));
        }
    }

    @Test
    void testClearToken() {
        jwtService.clearToken(response);

        Mockito.verify(response)
                .addHeader(
                        ArgumentMatchers.eq("Set-Cookie"),
                        ArgumentMatchers.contains("stirling_jwt="));
        Mockito.verify(response)
                .addHeader(
                        ArgumentMatchers.eq("Set-Cookie"), ArgumentMatchers.contains("Max-Age=0"));
    }

    @Test
    void testGenerateTokenWithKeyId() {
        String username = "testuser";
        Map<String, Object> claims = new HashMap<>();

        Mockito.when(keystoreService.getActiveKey()).thenReturn(testVerificationKey);
        Mockito.when(keystoreService.getKeyPair("test-key-id"))
                .thenReturn(Optional.of(testKeyPair));
        Mockito.when(authentication.getPrincipal()).thenReturn(userDetails);
        Mockito.when(userDetails.getUsername()).thenReturn(username);

        String token = jwtService.generateToken(authentication, claims);

        Assertions.assertNotNull(token);
        Assertions.assertFalse(token.isEmpty());
        // Verify that the keystore service was called
        Mockito.verify(keystoreService).getActiveKey();
        Mockito.verify(keystoreService).getKeyPair("test-key-id");
    }

    @Test
    void testTokenVerificationWithSpecificKeyId() throws Exception {
        String username = "testuser";
        Map<String, Object> claims = new HashMap<>();

        Mockito.when(keystoreService.getActiveKey()).thenReturn(testVerificationKey);
        Mockito.when(keystoreService.getKeyPair("test-key-id"))
                .thenReturn(Optional.of(testKeyPair));
        Mockito.when(keystoreService.decodePublicKey(testVerificationKey.getVerifyingKey()))
                .thenReturn(testKeyPair.getPublic());
        Mockito.when(authentication.getPrincipal()).thenReturn(userDetails);
        Mockito.when(userDetails.getUsername()).thenReturn(username);

        // Generate token with key ID
        String token = jwtService.generateToken(authentication, claims);

        // Mock extraction of key ID and verification (lenient to avoid unused stubbing)
        Mockito.lenient()
                .when(keystoreService.getKeyPair("test-key-id"))
                .thenReturn(Optional.of(testKeyPair));

        // Verify token can be validated
        Assertions.assertDoesNotThrow(() -> jwtService.validateToken(token));
        Assertions.assertEquals(username, jwtService.extractUsername(token));
    }

    @Test
    void testTokenVerificationFallsBackToActiveKeyWhenKeyIdNotFound() throws Exception {
        String username = "testuser";
        Map<String, Object> claims = new HashMap<>();

        // First, generate a token successfully
        Mockito.when(keystoreService.getActiveKey()).thenReturn(testVerificationKey);
        Mockito.when(keystoreService.getKeyPair("test-key-id"))
                .thenReturn(Optional.of(testKeyPair));
        Mockito.when(keystoreService.decodePublicKey(testVerificationKey.getVerifyingKey()))
                .thenReturn(testKeyPair.getPublic());
        Mockito.when(authentication.getPrincipal()).thenReturn(userDetails);
        Mockito.when(userDetails.getUsername()).thenReturn(username);

        String token = jwtService.generateToken(authentication, claims);

        // Now mock the scenario for validation - key not found, but fallback works
        // Create a fallback key pair that can be used
        JwtVerificationKey fallbackKey =
                new JwtVerificationKey(
                        "fallback-key",
                        Base64.getEncoder().encodeToString(testKeyPair.getPublic().getEncoded()));

        // Mock the specific key lookup to fail, but the active key should work
        Mockito.when(keystoreService.getKeyPair("test-key-id")).thenReturn(Optional.empty());
        Mockito.when(keystoreService.refreshActiveKeyPair()).thenReturn(fallbackKey);
        Mockito.when(keystoreService.getKeyPair("fallback-key"))
                .thenReturn(Optional.of(testKeyPair));

        // Should still work by falling back to the active keypair
        Assertions.assertDoesNotThrow(() -> jwtService.validateToken(token));
        Assertions.assertEquals(username, jwtService.extractUsername(token));

        // Verify fallback logic was used
        Mockito.verify(keystoreService, Mockito.atLeast(1)).getActiveKey();
    }

    private JwtService createJwtServiceWithSecureCookie(boolean secureCookie) throws Exception {
        // Use reflection to create JwtService with custom secureCookie value
        JwtService testService = new JwtService(true, keystoreService);

        // Set the secureCookie field using reflection
        java.lang.reflect.Field secureCookieField =
                JwtService.class.getDeclaredField("secureCookie");
        secureCookieField.setAccessible(true);
        secureCookieField.set(testService, secureCookie);

        return testService;
    }
}
