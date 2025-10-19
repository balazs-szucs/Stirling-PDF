package stirling.software.proprietary.security.configuration.ee;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import stirling.software.common.model.ApplicationProperties;

@ExtendWith(MockitoExtension.class)
class LicenseKeyCheckerTest {

    @Mock private KeygenLicenseVerifier verifier;

    @Test
    void premiumDisabled_skipsVerification() {
        ApplicationProperties props = new ApplicationProperties();
        props.getPremium().setEnabled(false);
        props.getPremium().setKey("dummy");

        LicenseKeyChecker checker = new LicenseKeyChecker(verifier, props);

        Assertions.assertEquals(
                KeygenLicenseVerifier.License.NORMAL, checker.getPremiumLicenseEnabledResult());
        Mockito.verifyNoInteractions(verifier);
    }

    @Test
    void directKey_verified() {
        ApplicationProperties props = new ApplicationProperties();
        props.getPremium().setEnabled(true);
        props.getPremium().setKey("abc");
        Mockito.when(verifier.verifyLicense("abc")).thenReturn(KeygenLicenseVerifier.License.PRO);

        LicenseKeyChecker checker = new LicenseKeyChecker(verifier, props);

        Assertions.assertEquals(
                KeygenLicenseVerifier.License.PRO, checker.getPremiumLicenseEnabledResult());
        Mockito.verify(verifier).verifyLicense("abc");
    }

    @Test
    void fileKey_verified(@TempDir Path temp) throws IOException {
        Path file = temp.resolve("license.txt");
        Files.writeString(file, "filekey");

        ApplicationProperties props = new ApplicationProperties();
        props.getPremium().setEnabled(true);
        props.getPremium().setKey("file:" + file.toString());
        Mockito.when(verifier.verifyLicense("filekey"))
                .thenReturn(KeygenLicenseVerifier.License.ENTERPRISE);

        LicenseKeyChecker checker = new LicenseKeyChecker(verifier, props);

        Assertions.assertEquals(
                KeygenLicenseVerifier.License.ENTERPRISE, checker.getPremiumLicenseEnabledResult());
        Mockito.verify(verifier).verifyLicense("filekey");
    }

    @Test
    void missingFile_resultsNormal(@TempDir Path temp) {
        Path file = temp.resolve("missing.txt");
        ApplicationProperties props = new ApplicationProperties();
        props.getPremium().setEnabled(true);
        props.getPremium().setKey("file:" + file.toString());

        LicenseKeyChecker checker = new LicenseKeyChecker(verifier, props);

        Assertions.assertEquals(
                KeygenLicenseVerifier.License.NORMAL, checker.getPremiumLicenseEnabledResult());
        Mockito.verifyNoInteractions(verifier);
    }
}
