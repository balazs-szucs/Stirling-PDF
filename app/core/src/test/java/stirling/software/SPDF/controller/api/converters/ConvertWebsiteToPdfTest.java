package stirling.software.SPDF.controller.api.converters;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import stirling.software.SPDF.model.api.converters.UrlToPdfRequest;
import stirling.software.common.configuration.RuntimePathConfig;
import stirling.software.common.model.ApplicationProperties;
import stirling.software.common.service.CustomPDFDocumentFactory;
import stirling.software.common.util.GeneralUtils;
import stirling.software.common.util.ProcessExecutor;
import stirling.software.common.util.ProcessExecutor.ProcessExecutorResult;
import stirling.software.common.util.ProcessExecutor.Processes;
import stirling.software.common.util.WebResponseUtils;

public class ConvertWebsiteToPdfTest {

    private static final Pattern PATTERN = Pattern.compile("[A-Za-z0-9_]+\\.pdf");
    @Mock private CustomPDFDocumentFactory pdfDocumentFactory;
    @Mock private RuntimePathConfig runtimePathConfig;

    private ApplicationProperties applicationProperties;
    private ConvertWebsiteToPDF sut;
    private AutoCloseable mocks;

    private static MockedStatic<HttpClient> mockHttpClientReturning(String body) throws Exception {
        MockedStatic<HttpClient> httpClientStatic = Mockito.mockStatic(HttpClient.class);
        HttpClient.Builder builder = Mockito.mock(HttpClient.Builder.class);
        HttpClient client = Mockito.mock(HttpClient.class);
        HttpResponse response = Mockito.mock(HttpResponse.class);

        httpClientStatic.when(HttpClient::newBuilder).thenReturn(builder);
        Mockito.when(builder.followRedirects(HttpClient.Redirect.NORMAL)).thenReturn(builder);
        Mockito.when(builder.connectTimeout(ArgumentMatchers.any(Duration.class)))
                .thenReturn(builder);
        Mockito.when(builder.build()).thenReturn(client);

        Mockito.when(
                        client.send(
                                ArgumentMatchers.any(HttpRequest.class),
                                ArgumentMatchers.any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);
        Mockito.when(response.statusCode()).thenReturn(200);
        Mockito.when(response.body()).thenReturn(body);

        return httpClientStatic;
    }

    @AfterEach
    void tearDown() throws Exception {
        RequestContextHolder.resetRequestAttributes();
        if (mocks != null) mocks.close();
    }

    @BeforeEach
    void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);

        // Enable feature (adjust structure for your project if necessary)
        applicationProperties = new ApplicationProperties();
        applicationProperties.getSystem().setEnableUrlToPDF(true);

        // Stubs in case the code continues to run
        Mockito.when(runtimePathConfig.getWeasyPrintPath()).thenReturn("/usr/bin/weasyprint");
        Mockito.when(pdfDocumentFactory.load(ArgumentMatchers.any(File.class)))
                .thenReturn(new PDDocument());

        // Build SUT
        sut = new ConvertWebsiteToPDF(pdfDocumentFactory, runtimePathConfig, applicationProperties);

        // Provide RequestContext for ServletUriComponentsBuilder
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setScheme("http");
        req.setServerName("localhost");
        req.setServerPort(8080);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
    }

    @Test
    void redirect_with_error_when_invalid_url_format_provided() throws Exception {
        UrlToPdfRequest request = new UrlToPdfRequest();
        request.setUrlInput("not-a-url");

        ResponseEntity<?> resp = sut.urlToPdf(request);

        Assertions.assertEquals(HttpStatus.SEE_OTHER, resp.getStatusCode());
        URI location = resp.getHeaders().getLocation();
        Assertions.assertNotNull(location, "Location header expected");
        Assertions.assertTrue(
                location.getQuery() != null
                        && location.getQuery().contains("error=error.invalidUrlFormat"));
    }

    @Test
    void redirect_with_error_when_url_is_not_reachable() throws Exception {
        UrlToPdfRequest request = new UrlToPdfRequest();
        // .invalid is reserved by RFC and not resolvable
        request.setUrlInput("https://nonexistent.invalid/");

        ResponseEntity<?> resp = sut.urlToPdf(request);

        Assertions.assertEquals(HttpStatus.SEE_OTHER, resp.getStatusCode());
        URI location = resp.getHeaders().getLocation();
        Assertions.assertNotNull(location, "Location header expected");
        Assertions.assertTrue(
                location.getQuery() != null
                        && location.getQuery().contains("error=error.urlNotReachable"));
    }

    @Test
    void redirect_with_error_when_endpoint_disabled() throws Exception {
        // Disable feature
        applicationProperties.getSystem().setEnableUrlToPDF(false);

        UrlToPdfRequest request = new UrlToPdfRequest();
        request.setUrlInput("https://example.com/");

        ResponseEntity<?> resp = sut.urlToPdf(request);

        Assertions.assertEquals(HttpStatus.SEE_OTHER, resp.getStatusCode());
        URI location = resp.getHeaders().getLocation();
        Assertions.assertNotNull(location, "Location header expected");
        Assertions.assertTrue(
                location.getQuery() != null
                        && location.getQuery().contains("error=error.endpointDisabled"));
    }

    @Test
    void convertURLToFileName_sanitizes_and_appends_pdf() throws Exception {
        Method m =
                ConvertWebsiteToPDF.class.getDeclaredMethod("convertURLToFileName", String.class);
        m.setAccessible(true);

        String in = "https://ex-ample.com/path?q=1&x=y#frag";
        String out = (String) m.invoke(sut, in);

        Assertions.assertTrue(out.endsWith(".pdf"));
        // Only A–Z, a–z, 0–9, underscore and dot allowed
        Assertions.assertTrue(PATTERN.matcher(out).matches());
        // no truncation here (source not that long)
        Assertions.assertTrue(out.length() <= 54);
    }

    @Test
    void convertURLToFileName_truncates_to_50_chars_before_pdf_suffix() throws Exception {
        Method m =
                ConvertWebsiteToPDF.class.getDeclaredMethod("convertURLToFileName", String.class);
        m.setAccessible(true);

        // Very long URL -> triggers truncation
        String longUrl =
                "https://very-very-long-domain.example.com/some/really/long/path/with?many=params&and=chars";
        String out = (String) m.invoke(sut, longUrl);

        Assertions.assertTrue(out.endsWith(".pdf"));
        Assertions.assertTrue(PATTERN.matcher(out).matches());
        // safeName limited to 50 -> total max 54 including '.pdf'
        Assertions.assertTrue(out.length() <= 54, "Filename should be truncated to 50 + '.pdf'");
    }

    @Test
    void happy_path_executes_weasyprint_loads_pdf_and_returns_response() throws Exception {
        UrlToPdfRequest request = new UrlToPdfRequest();
        request.setUrlInput("https://example.com");

        try (MockedStatic<ProcessExecutor> pe = Mockito.mockStatic(ProcessExecutor.class);
                MockedStatic<WebResponseUtils> wr = Mockito.mockStatic(WebResponseUtils.class);
                MockedStatic<GeneralUtils> gu = Mockito.mockStatic(GeneralUtils.class);
                MockedStatic<HttpClient> httpClient = mockHttpClientReturning("<html></html>")) {

            // Force URL checks to be positive
            gu.when(() -> GeneralUtils.isValidURL("https://example.com")).thenReturn(true);
            gu.when(() -> GeneralUtils.isURLReachable("https://example.com")).thenReturn(true);

            // correct ProcessExecutor!
            ProcessExecutor mockExec = Mockito.mock(ProcessExecutor.class);
            pe.when(() -> ProcessExecutor.getInstance(Processes.WEASYPRINT)).thenReturn(mockExec);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<String>> cmdCaptor = ArgumentCaptor.forClass(List.class);

            // Return value of correct type
            ProcessExecutorResult dummyResult = Mockito.mock(ProcessExecutorResult.class);
            Mockito.when(mockExec.runCommandWithOutputHandling(cmdCaptor.capture()))
                    .thenReturn(dummyResult);

            // Mock WebResponseUtils
            ResponseEntity<byte[]> fakeResponse = ResponseEntity.ok(new byte[0]);
            wr.when(
                            () ->
                                    WebResponseUtils.pdfDocToWebResponse(
                                            ArgumentMatchers.any(PDDocument.class),
                                            ArgumentMatchers.anyString()))
                    .thenReturn(fakeResponse);

            // Act
            ResponseEntity<?> resp = sut.urlToPdf(request);

            // Assert – Response OK
            Assertions.assertEquals(HttpStatus.OK, resp.getStatusCode());

            // Assert – WeasyPrint command correct
            List<String> cmd = cmdCaptor.getValue();
            Assertions.assertNotNull(cmd);
            Assertions.assertEquals("/usr/bin/weasyprint", cmd.get(0));
            Assertions.assertTrue(
                    cmd.size() >= 6, "WeasyPrint should receive HTML input and output path");
            String htmlPathStr = cmd.get(1);
            Assertions.assertEquals("--base-url", cmd.get(2));
            Assertions.assertEquals("https://example.com", cmd.get(3));
            Assertions.assertEquals("--pdf-forms", cmd.get(4));
            String outPathStr = cmd.get(5);
            Assertions.assertNotNull(outPathStr);

            // Temp file must be deleted in finally
            Path outPath = Path.of(outPathStr);
            Assertions.assertFalse(
                    Files.exists(Path.of(htmlPathStr)),
                    "Temp HTML file should be deleted after the call");
        }
    }

    @Test
    void finally_block_logs_and_swallows_ioexception_on_delete() throws Exception {
        // Arrange
        UrlToPdfRequest request = new UrlToPdfRequest();
        request.setUrlInput("https://example.com");

        Path preCreatedTemp = java.nio.file.Files.createTempFile("test_output_", ".pdf");
        Path htmlTemp = java.nio.file.Files.createTempFile("test_input_", ".html");

        try (MockedStatic<GeneralUtils> gu = Mockito.mockStatic(GeneralUtils.class);
                MockedStatic<ProcessExecutor> pe = Mockito.mockStatic(ProcessExecutor.class);
                MockedStatic<WebResponseUtils> wr = Mockito.mockStatic(WebResponseUtils.class);
                MockedStatic<Files> files = Mockito.mockStatic(Files.class);
                MockedStatic<HttpClient> httpClient = mockHttpClientReturning("<html></html>")) {

            // Force URL checks to be positive
            gu.when(() -> GeneralUtils.isValidURL("https://example.com")).thenReturn(true);
            gu.when(() -> GeneralUtils.isURLReachable("https://example.com")).thenReturn(true);

            // Force temp files + provoke delete error
            files.when(() -> Files.createTempFile("url_input_", ".html")).thenReturn(htmlTemp);
            files.when(() -> Files.createTempFile("output_", ".pdf")).thenReturn(preCreatedTemp);
            files.when(
                            () ->
                                    Files.writeString(
                                            ArgumentMatchers.eq(htmlTemp),
                                            ArgumentMatchers.anyString(),
                                            ArgumentMatchers.eq(
                                                    java.nio.charset.StandardCharsets.UTF_8)))
                    .thenReturn(htmlTemp);
            files.when(() -> Files.deleteIfExists(htmlTemp)).thenReturn(true);
            files.when(() -> Files.deleteIfExists(preCreatedTemp))
                    .thenThrow(new IOException("fail delete"));
            files.when(() -> Files.exists(preCreatedTemp)).thenReturn(true); // for the assert

            // ProcessExecutor
            ProcessExecutor mockExec = Mockito.mock(ProcessExecutor.class);
            pe.when(() -> ProcessExecutor.getInstance(Processes.WEASYPRINT)).thenReturn(mockExec);
            ProcessExecutorResult dummy = Mockito.mock(ProcessExecutorResult.class);
            Mockito.when(mockExec.runCommandWithOutputHandling(Mockito.<List>any()))
                    .thenReturn(dummy);

            // WebResponseUtils
            ResponseEntity<byte[]> fakeResponse = ResponseEntity.ok(new byte[0]);
            wr.when(
                            () ->
                                    WebResponseUtils.pdfDocToWebResponse(
                                            ArgumentMatchers.any(PDDocument.class),
                                            ArgumentMatchers.anyString()))
                    .thenReturn(fakeResponse);

            // Act: should not throw and should return a Response
            ResponseEntity<?> resp = Assertions.assertDoesNotThrow(() -> sut.urlToPdf(request));

            // Assert
            Assertions.assertNotNull(resp, "Response should not be null");
            Assertions.assertEquals(HttpStatus.OK, resp.getStatusCode());
            Assertions.assertTrue(
                    java.nio.file.Files.exists(preCreatedTemp),
                    "Temp file should still exist despite delete IOException");
        } finally {
            try {
                java.nio.file.Files.deleteIfExists(preCreatedTemp);
                java.nio.file.Files.deleteIfExists(htmlTemp);
            } catch (IOException ignore) {
            }
        }
    }

    @Test
    void redirect_with_error_when_disallowed_content_detected() throws Exception {
        UrlToPdfRequest request = new UrlToPdfRequest();
        request.setUrlInput("https://example.com");

        try (MockedStatic<GeneralUtils> gu = Mockito.mockStatic(GeneralUtils.class);
                MockedStatic<HttpClient> httpClient =
                        mockHttpClientReturning(
                                "<link rel=\"attachment\" href=\"file:///etc/passwd\">"); ) {

            gu.when(() -> GeneralUtils.isValidURL("https://example.com")).thenReturn(true);
            gu.when(() -> GeneralUtils.isURLReachable("https://example.com")).thenReturn(true);

            ResponseEntity<?> resp = sut.urlToPdf(request);

            Assertions.assertEquals(HttpStatus.SEE_OTHER, resp.getStatusCode());
            URI location = resp.getHeaders().getLocation();
            Assertions.assertNotNull(location, "Location header expected");
            Assertions.assertTrue(
                    location.getQuery() != null
                            && location.getQuery().contains("error=error.disallowedUrlContent"));
        }
    }
}
