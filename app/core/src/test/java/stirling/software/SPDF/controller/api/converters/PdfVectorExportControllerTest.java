package stirling.software.SPDF.controller.api.converters;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import stirling.software.SPDF.config.EndpointConfiguration;
import stirling.software.SPDF.model.api.converters.PdfVectorExportRequest;
import stirling.software.common.util.ProcessExecutor;
import stirling.software.common.util.ProcessExecutor.ProcessExecutorResult;
import stirling.software.common.util.TempFileManager;

@ExtendWith(MockitoExtension.class)
class PdfVectorExportControllerTest {

    private final List<Path> tempPaths = new ArrayList<>();
    @Mock private TempFileManager tempFileManager;
    @Mock private EndpointConfiguration endpointConfiguration;
    @Mock private ProcessExecutor ghostscriptExecutor;
    @InjectMocks private PdfVectorExportController controller;
    private Map<ProcessExecutor.Processes, ProcessExecutor> originalExecutors;

    @BeforeEach
    void setup() throws Exception {
        Mockito.when(tempFileManager.createTempFile(ArgumentMatchers.any()))
                .thenAnswer(
                        invocation -> {
                            String suffix = invocation.getArgument(0);
                            Path path =
                                    Files.createTempFile(
                                            "vector_test", suffix == null ? "" : suffix);
                            tempPaths.add(path);
                            return path.toFile();
                        });

        Field instancesField = ProcessExecutor.class.getDeclaredField("instances");
        instancesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<ProcessExecutor.Processes, ProcessExecutor> instances =
                (Map<ProcessExecutor.Processes, ProcessExecutor>) instancesField.get(null);

        originalExecutors = Map.copyOf(instances);
        instances.clear();
        instances.put(ProcessExecutor.Processes.GHOSTSCRIPT, ghostscriptExecutor);
    }

    @AfterEach
    void tearDown() throws Exception {
        Field instancesField = ProcessExecutor.class.getDeclaredField("instances");
        instancesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<ProcessExecutor.Processes, ProcessExecutor> instances =
                (Map<ProcessExecutor.Processes, ProcessExecutor>) instancesField.get(null);
        instances.clear();
        if (originalExecutors != null) {
            instances.putAll(originalExecutors);
        }
        Mockito.reset(ghostscriptExecutor, tempFileManager, endpointConfiguration);
        for (Path path : tempPaths) {
            Files.deleteIfExists(path);
        }
        tempPaths.clear();
    }

    private ProcessExecutorResult mockResult() {
        ProcessExecutorResult result = Mockito.mock(ProcessExecutorResult.class);
        Mockito.lenient().when(result.getRc()).thenReturn(0);
        Mockito.lenient().when(result.getMessages()).thenReturn("");
        return result;
    }

    @Test
    void convertGhostscript_psToPdf_success() throws Exception {
        Mockito.when(endpointConfiguration.isGroupEnabled("Ghostscript")).thenReturn(true);
        ProcessExecutorResult result = mockResult();
        Mockito.when(ghostscriptExecutor.runCommandWithOutputHandling(ArgumentMatchers.any()))
                .thenReturn(result);

        MockMultipartFile file =
                new MockMultipartFile(
                        "fileInput",
                        "sample.ps",
                        MediaType.APPLICATION_OCTET_STREAM_VALUE,
                        new byte[] {1});
        PdfVectorExportRequest request = new PdfVectorExportRequest();
        request.setFileInput(file);

        ResponseEntity<byte[]> response = controller.convertGhostscriptInputsToPdf(request);

        org.assertj.core.api.Assertions.assertThat(response.getStatusCode())
                .isEqualTo(org.springframework.http.HttpStatus.OK);
        org.assertj.core.api.Assertions.assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PDF);
    }

    @Test
    void convertGhostscript_pdfPassThrough_success() throws Exception {
        Mockito.when(endpointConfiguration.isGroupEnabled("Ghostscript")).thenReturn(false);

        byte[] content = {1};
        MockMultipartFile file =
                new MockMultipartFile(
                        "fileInput", "input.pdf", MediaType.APPLICATION_PDF_VALUE, content);
        PdfVectorExportRequest request = new PdfVectorExportRequest();
        request.setFileInput(file);

        ResponseEntity<byte[]> response = controller.convertGhostscriptInputsToPdf(request);

        org.assertj.core.api.Assertions.assertThat(response.getStatusCode())
                .isEqualTo(org.springframework.http.HttpStatus.OK);
        org.assertj.core.api.Assertions.assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PDF);
        org.assertj.core.api.Assertions.assertThat(response.getBody()).contains(content);
    }

    @Test
    void convertGhostscript_unsupportedFormatThrows() {
        Mockito.when(endpointConfiguration.isGroupEnabled("Ghostscript")).thenReturn(false);
        MockMultipartFile file =
                new MockMultipartFile(
                        "fileInput", "vector.svg", MediaType.APPLICATION_XML_VALUE, new byte[] {1});
        PdfVectorExportRequest request = new PdfVectorExportRequest();
        request.setFileInput(file);

        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> controller.convertGhostscriptInputsToPdf(request));
    }
}
