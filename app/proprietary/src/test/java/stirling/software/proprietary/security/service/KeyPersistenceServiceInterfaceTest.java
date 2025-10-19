package stirling.software.proprietary.security.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Optional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import stirling.software.common.configuration.InstallationPathConfig;
import stirling.software.common.model.ApplicationProperties;
import stirling.software.proprietary.security.model.JwtVerificationKey;

@ExtendWith(MockitoExtension.class)
class KeyPersistenceServiceInterfaceTest {

    @Mock private ApplicationProperties applicationProperties;

    @Mock private ApplicationProperties.Security security;

    @Mock private ApplicationProperties.Security.Jwt jwtConfig;

    @TempDir Path tempDir;

    private KeyPersistenceService keyPersistenceService;
    private KeyPair testKeyPair;
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() throws NoSuchAlgorithmException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        testKeyPair = keyPairGenerator.generateKeyPair();

        cacheManager = new ConcurrentMapCacheManager("verifyingKeys");

        Mockito.lenient().when(applicationProperties.getSecurity()).thenReturn(security);
        Mockito.lenient().when(security.getJwt()).thenReturn(jwtConfig);
        Mockito.lenient().when(jwtConfig.isEnabled()).thenReturn(true);
    }

    @Test
    void testGetActiveKeypairWhenNoActiveKeyExists() {
        try (MockedStatic<InstallationPathConfig> mockedStatic =
                Mockito.mockStatic(InstallationPathConfig.class)) {
            mockedStatic
                    .when(InstallationPathConfig::getPrivateKeyPath)
                    .thenReturn(tempDir.toString());
            keyPersistenceService = new KeyPersistenceService(applicationProperties, cacheManager);
            keyPersistenceService.initializeKeystore();

            JwtVerificationKey result = keyPersistenceService.getActiveKey();

            Assertions.assertNotNull(result);
            Assertions.assertNotNull(result.getKeyId());
            Assertions.assertNotNull(result.getVerifyingKey());
        }
    }

    @Test
    void testGetActiveKeyPairWithExistingKey() throws Exception {
        String keyId = "test-key-2024-01-01-120000";
        String publicKeyBase64 =
                Base64.getEncoder().encodeToString(testKeyPair.getPublic().getEncoded());
        String privateKeyBase64 =
                Base64.getEncoder().encodeToString(testKeyPair.getPrivate().getEncoded());

        JwtVerificationKey existingKey = new JwtVerificationKey(keyId, publicKeyBase64);

        Path keyFile = tempDir.resolve(keyId + ".key");
        Files.writeString(keyFile, privateKeyBase64);

        try (MockedStatic<InstallationPathConfig> mockedStatic =
                Mockito.mockStatic(InstallationPathConfig.class)) {
            mockedStatic
                    .when(InstallationPathConfig::getPrivateKeyPath)
                    .thenReturn(tempDir.toString());
            keyPersistenceService = new KeyPersistenceService(applicationProperties, cacheManager);
            keyPersistenceService.initializeKeystore();

            JwtVerificationKey result = keyPersistenceService.getActiveKey();

            Assertions.assertNotNull(result);
            Assertions.assertNotNull(result.getKeyId());
        }
    }

    @Test
    void testGetKeyPair() throws Exception {
        String keyId = "test-key-123";
        String publicKeyBase64 =
                Base64.getEncoder().encodeToString(testKeyPair.getPublic().getEncoded());
        String privateKeyBase64 =
                Base64.getEncoder().encodeToString(testKeyPair.getPrivate().getEncoded());

        JwtVerificationKey signingKey = new JwtVerificationKey(keyId, publicKeyBase64);

        Path keyFile = tempDir.resolve(keyId + ".key");
        Files.writeString(keyFile, privateKeyBase64);

        try (MockedStatic<InstallationPathConfig> mockedStatic =
                Mockito.mockStatic(InstallationPathConfig.class)) {
            mockedStatic
                    .when(InstallationPathConfig::getPrivateKeyPath)
                    .thenReturn(tempDir.toString());
            keyPersistenceService = new KeyPersistenceService(applicationProperties, cacheManager);

            keyPersistenceService
                    .getClass()
                    .getDeclaredField("verifyingKeyCache")
                    .setAccessible(true);
            var cache = cacheManager.getCache("verifyingKeys");
            Assertions.assertNotNull(cache);
            cache.put(keyId, signingKey);

            Optional<KeyPair> result = keyPersistenceService.getKeyPair(keyId);

            Assertions.assertTrue(result.isPresent());
            Assertions.assertNotNull(result.get().getPublic());
            Assertions.assertNotNull(result.get().getPrivate());
        }
    }

    @Test
    void testGetKeyPairNotFound() {
        String keyId = "non-existent-key";

        try (MockedStatic<InstallationPathConfig> mockedStatic =
                Mockito.mockStatic(InstallationPathConfig.class)) {
            mockedStatic
                    .when(InstallationPathConfig::getPrivateKeyPath)
                    .thenReturn(tempDir.toString());
            keyPersistenceService = new KeyPersistenceService(applicationProperties, cacheManager);

            Optional<KeyPair> result = keyPersistenceService.getKeyPair(keyId);

            Assertions.assertFalse(result.isPresent());
        }
    }

    @Test
    void testGetKeyPairWhenKeystoreDisabled() {
        Mockito.when(jwtConfig.isEnabled()).thenReturn(false);

        try (MockedStatic<InstallationPathConfig> mockedStatic =
                Mockito.mockStatic(InstallationPathConfig.class)) {
            mockedStatic
                    .when(InstallationPathConfig::getPrivateKeyPath)
                    .thenReturn(tempDir.toString());
            keyPersistenceService = new KeyPersistenceService(applicationProperties, cacheManager);

            Optional<KeyPair> result = keyPersistenceService.getKeyPair("any-key");

            Assertions.assertFalse(result.isPresent());
        }
    }

    @Test
    void testInitializeKeystoreCreatesDirectory() {
        try (MockedStatic<InstallationPathConfig> mockedStatic =
                Mockito.mockStatic(InstallationPathConfig.class)) {
            mockedStatic
                    .when(InstallationPathConfig::getPrivateKeyPath)
                    .thenReturn(tempDir.toString());
            keyPersistenceService = new KeyPersistenceService(applicationProperties, cacheManager);
            keyPersistenceService.initializeKeystore();

            Assertions.assertTrue(Files.exists(tempDir));
            Assertions.assertTrue(Files.isDirectory(tempDir));
        }
    }

    @Test
    void testLoadExistingKeypairWithMissingPrivateKeyFile() {
        String keyId = "test-key-missing-file";
        String publicKeyBase64 =
                Base64.getEncoder().encodeToString(testKeyPair.getPublic().getEncoded());

        JwtVerificationKey existingKey = new JwtVerificationKey(keyId, publicKeyBase64);

        try (MockedStatic<InstallationPathConfig> mockedStatic =
                Mockito.mockStatic(InstallationPathConfig.class)) {
            mockedStatic
                    .when(InstallationPathConfig::getPrivateKeyPath)
                    .thenReturn(tempDir.toString());
            keyPersistenceService = new KeyPersistenceService(applicationProperties, cacheManager);
            keyPersistenceService.initializeKeystore();

            JwtVerificationKey result = keyPersistenceService.getActiveKey();
            Assertions.assertNotNull(result);
            Assertions.assertNotNull(result.getKeyId());
            Assertions.assertNotNull(result.getVerifyingKey());
        }
    }
}
