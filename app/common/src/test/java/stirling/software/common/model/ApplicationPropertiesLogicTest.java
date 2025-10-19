package stirling.software.common.model;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import stirling.software.common.model.ApplicationProperties.Driver;
import stirling.software.common.model.ApplicationProperties.Premium;
import stirling.software.common.model.ApplicationProperties.Security;
import stirling.software.common.model.exception.UnsupportedProviderException;

class ApplicationPropertiesLogicTest {

    @Test
    void system_isAnalyticsEnabled_null_false_true() {
        ApplicationProperties.System sys = new ApplicationProperties.System();

        sys.setEnableAnalytics(null);
        Assertions.assertFalse(sys.isAnalyticsEnabled());

        sys.setEnableAnalytics(Boolean.FALSE);
        Assertions.assertFalse(sys.isAnalyticsEnabled());

        sys.setEnableAnalytics(Boolean.TRUE);
        Assertions.assertTrue(sys.isAnalyticsEnabled());
    }

    @Test
    void tempFileManagement_defaults_and_overrides() {
        Function<String, String> normalize = s -> Paths.get(s).normalize().toString();
        ApplicationProperties.TempFileManagement tfm =
                new ApplicationProperties.TempFileManagement();

        String expectedBase =
                Paths.get(java.lang.System.getProperty("java.io.tmpdir"), "stirling-pdf")
                        .toString();
        Assertions.assertEquals(expectedBase, tfm.getBaseTmpDir());

        String expectedLibre = Paths.get(expectedBase, "libreoffice").toString();
        Assertions.assertEquals(expectedLibre, tfm.getLibreofficeDir());

        tfm.setBaseTmpDir("/custom/base");
        Assertions.assertEquals("/custom/base", normalize.apply(tfm.getBaseTmpDir()));

        tfm.setLibreofficeDir("/opt/libre");
        Assertions.assertEquals("/opt/libre", normalize.apply(tfm.getLibreofficeDir()));
    }

    @Test
    void oauth2_scope_parsing_and_validity() {
        Security.OAUTH2 oauth2 = new Security.OAUTH2();
        oauth2.setIssuer("https://issuer");
        oauth2.setClientId("client");
        oauth2.setClientSecret("secret");
        oauth2.setUseAsUsername("email");
        oauth2.setScopes("openid, profile ,email");
        Assertions.assertTrue(oauth2.isSettingsValid());
    }

    @Test
    void security_login_method_flags() {
        Security sec = new Security();

        sec.getOauth2().setEnabled(true);
        sec.getSaml2().setEnabled(true);

        Assertions.assertTrue(sec.isUserPass());
        Assertions.assertTrue(sec.isOauth2Active());
        Assertions.assertTrue(sec.isSaml2Active());

        sec.setLoginMethod(Security.LoginMethods.NORMAL.toString());
        Assertions.assertTrue(sec.isUserPass());
        Assertions.assertFalse(sec.isOauth2Active());
        Assertions.assertFalse(sec.isSaml2Active());
    }

    @Test
    void security_isAltLogin_reflects_oauth2_or_saml2() {
        Security sec = new Security();

        Assertions.assertFalse(sec.isAltLogin());

        sec.getOauth2().setEnabled(true);
        sec.getSaml2().setEnabled(false);
        Assertions.assertTrue(sec.isAltLogin());

        sec.getOauth2().setEnabled(false);
        sec.getSaml2().setEnabled(true);
        Assertions.assertTrue(sec.isAltLogin());

        sec.getOauth2().setEnabled(true);
        sec.getSaml2().setEnabled(true);
        Assertions.assertTrue(sec.isAltLogin());
    }

    @Test
    void oauth2_client_provider_mapping_and_unsupported() throws UnsupportedProviderException {
        Security.OAUTH2.Client client = new Security.OAUTH2.Client();

        Assertions.assertNotNull(client.get("google"));
        Assertions.assertNotNull(client.get("github"));
        Assertions.assertNotNull(client.get("keycloak"));

        UnsupportedProviderException ex =
                Assertions.assertThrows(
                        UnsupportedProviderException.class, () -> client.get("unknown"));
        Assertions.assertTrue(ex.getMessage().toLowerCase().contains("not supported"));
    }

    @Test
    void premium_google_drive_getters_return_empty_string_on_null_or_blank() {
        Premium.ProFeatures.GoogleDrive gd = new Premium.ProFeatures.GoogleDrive();

        Assertions.assertEquals("", gd.getClientId());
        Assertions.assertEquals("", gd.getApiKey());
        Assertions.assertEquals("", gd.getAppId());

        gd.setClientId(" id ");
        gd.setApiKey(" key ");
        gd.setAppId(" app ");
        Assertions.assertEquals(" id ", gd.getClientId());
        Assertions.assertEquals(" key ", gd.getApiKey());
        Assertions.assertEquals(" app ", gd.getAppId());
    }

    @Test
    void ui_getters_return_null_for_blank() {
        ApplicationProperties.Ui ui = new ApplicationProperties.Ui();
        ui.setAppName("   ");
        ui.setHomeDescription("");
        ui.setAppNameNavbar(null);

        Assertions.assertNull(ui.getAppName());
        Assertions.assertNull(ui.getHomeDescription());
        Assertions.assertNull(ui.getAppNameNavbar());

        ui.setAppName("Stirling-PDF");
        ui.setHomeDescription("Home");
        ui.setAppNameNavbar("Nav");
        Assertions.assertEquals("Stirling-PDF", ui.getAppName());
        Assertions.assertEquals("Home", ui.getHomeDescription());
        Assertions.assertEquals("Nav", ui.getAppNameNavbar());
    }

    @Test
    void driver_toString_contains_driver_name() {
        Assertions.assertTrue(Driver.H2.toString().contains("h2"));
        Assertions.assertTrue(Driver.POSTGRESQL.toString().contains("postgresql"));
    }

    @Test
    void session_limits_and_timeouts_have_reasonable_defaults() {
        ApplicationProperties.ProcessExecutor pe = new ApplicationProperties.ProcessExecutor();

        ApplicationProperties.ProcessExecutor.SessionLimit s = pe.getSessionLimit();
        Assertions.assertEquals(2, s.getQpdfSessionLimit());
        Assertions.assertEquals(1, s.getTesseractSessionLimit());
        Assertions.assertEquals(1, s.getLibreOfficeSessionLimit());
        Assertions.assertEquals(1, s.getPdfToHtmlSessionLimit());
        Assertions.assertEquals(8, s.getPythonOpenCvSessionLimit());
        Assertions.assertEquals(16, s.getWeasyPrintSessionLimit());
        Assertions.assertEquals(1, s.getInstallAppSessionLimit());
        Assertions.assertEquals(1, s.getCalibreSessionLimit());
        Assertions.assertEquals(8, s.getGhostscriptSessionLimit());
        Assertions.assertEquals(2, s.getOcrMyPdfSessionLimit());

        ApplicationProperties.ProcessExecutor.TimeoutMinutes t = pe.getTimeoutMinutes();
        Assertions.assertEquals(30, t.getTesseractTimeoutMinutes());
        Assertions.assertEquals(30, t.getQpdfTimeoutMinutes());
        Assertions.assertEquals(30, t.getLibreOfficeTimeoutMinutes());
        Assertions.assertEquals(20, t.getPdfToHtmlTimeoutMinutes());
        Assertions.assertEquals(30, t.getPythonOpenCvTimeoutMinutes());
        Assertions.assertEquals(30, t.getWeasyPrintTimeoutMinutes());
        Assertions.assertEquals(60, t.getInstallAppTimeoutMinutes());
        Assertions.assertEquals(30, t.getCalibreTimeoutMinutes());
        Assertions.assertEquals(30, t.getGhostscriptTimeoutMinutes());
        Assertions.assertEquals(30, t.getOcrMyPdfTimeoutMinutes());
    }

    @Deprecated(since = "0.45.0")
    @Test
    void enterprise_metadata_defaults() {
        ApplicationProperties.EnterpriseEdition ee = new ApplicationProperties.EnterpriseEdition();
        ApplicationProperties.EnterpriseEdition.CustomMetadata eMeta = ee.getCustomMetadata();
        eMeta.setCreator("  ");
        eMeta.setProducer(null);
        Assertions.assertEquals("Stirling-PDF", eMeta.getCreator());
        Assertions.assertEquals("Stirling-PDF", eMeta.getProducer());
    }

    @Test
    void premium_metadata_defaults() {
        Premium.ProFeatures pf = new Premium.ProFeatures();
        Premium.ProFeatures.CustomMetadata pMeta = pf.getCustomMetadata();
        pMeta.setCreator("");
        pMeta.setProducer("");
        Assertions.assertEquals("Stirling-PDF", pMeta.getCreator());
        Assertions.assertEquals("Stirling-PDF", pMeta.getProducer());
    }

    @Test
    void premium_metadata_awesome() {
        Premium.ProFeatures pf = new Premium.ProFeatures();
        Premium.ProFeatures.CustomMetadata pMeta = pf.getCustomMetadata();
        pMeta.setCreator("Awesome PDF Tool");
        pMeta.setProducer("Awesome PDF Tool");
        Assertions.assertEquals("Awesome PDF Tool", pMeta.getCreator());
        Assertions.assertEquals("Awesome PDF Tool", pMeta.getProducer());
    }

    @Test
    void string_isValid_handles_null_empty_blank_and_trimmed() {
        ApplicationProperties.Security.OAUTH2 oauth2 = new ApplicationProperties.Security.OAUTH2();

        Assertions.assertFalse(oauth2.isValid((String) null, "issuer"));
        Assertions.assertFalse(oauth2.isValid("", "issuer"));
        Assertions.assertFalse(oauth2.isValid("   ", "issuer"));

        Assertions.assertTrue(oauth2.isValid("x", "issuer"));
        Assertions.assertTrue(oauth2.isValid("  x  ", "issuer")); // trimmt intern
    }

    @Test
    void collection_isValid_handles_null_and_empty() {
        ApplicationProperties.Security.OAUTH2 oauth2 = new ApplicationProperties.Security.OAUTH2();

        Collection<String> nullColl = null;
        Collection<String> empty = List.of();

        Assertions.assertFalse(oauth2.isValid(nullColl, "scopes"));
        Assertions.assertFalse(oauth2.isValid(empty, "scopes"));
    }

    @Test
    void collection_isValid_true_when_non_empty_even_if_element_is_blank() {
        ApplicationProperties.Security.OAUTH2 oauth2 = new ApplicationProperties.Security.OAUTH2();

        // Aktuelles Verhalten: prüft NUR !isEmpty(), nicht Inhalt
        Collection<String> oneBlank = new ArrayList<>();
        oneBlank.add("   ");

        Assertions.assertTrue(
                oauth2.isValid(oneBlank, "scopes"),
                "Dokumentiert aktuelles Verhalten: nicht-leere Liste gilt als gültig, auch wenn Element leer/blank ist");
    }
}
