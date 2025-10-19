package stirling.software.SPDF.controller.api.pipeline;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import jakarta.servlet.ServletContext;

import stirling.software.SPDF.model.PipelineConfig;
import stirling.software.SPDF.model.PipelineOperation;
import stirling.software.SPDF.model.PipelineResult;
import stirling.software.SPDF.service.ApiDocService;
import stirling.software.common.service.UserServiceInterface;

@ExtendWith(MockitoExtension.class)
class PipelineProcessorTest {

    @Mock ApiDocService apiDocService;

    @Mock UserServiceInterface userService;

    @Mock ServletContext servletContext;

    PipelineProcessor pipelineProcessor;

    @BeforeEach
    void setUp() {
        pipelineProcessor =
                Mockito.spy(new PipelineProcessor(apiDocService, userService, servletContext));
    }

    @Test
    void runPipelineWithFilterSetsFlag() throws Exception {
        PipelineOperation op = new PipelineOperation();
        op.setOperation("/api/v1/filter/filter-page-count");
        op.setParameters(Map.of());
        PipelineConfig config = new PipelineConfig();
        config.setOperations(List.of(op));

        Resource file = new MyByteArrayResource();

        List<Resource> files = List.of(file);

        Mockito.when(apiDocService.isMultiInput("/api/v1/filter/filter-page-count"))
                .thenReturn(false);
        Mockito.when(apiDocService.getExtensionTypes(false, "/api/v1/filter/filter-page-count"))
                .thenReturn(List.of("pdf"));
        Mockito.when(
                        apiDocService.isValidOperation(
                                ArgumentMatchers.eq("/api/v1/filter/filter-page-count"),
                                ArgumentMatchers.anyMap()))
                .thenReturn(true);

        Mockito.doReturn(new ResponseEntity<>(new byte[0], HttpStatus.OK))
                .when(pipelineProcessor)
                .sendWebRequest(ArgumentMatchers.anyString(), ArgumentMatchers.any());

        PipelineResult result = pipelineProcessor.runPipelineAgainstFiles(files, config);

        Assertions.assertTrue(
                result.isFiltersApplied(),
                "Filter flag should be true when operation filters file");
        Assertions.assertFalse(result.isHasErrors(), "No errors should occur");
        Assertions.assertTrue(
                result.getOutputFiles().isEmpty(), "Filtered file list should be empty");
    }

    private static class MyByteArrayResource extends ByteArrayResource {
        public MyByteArrayResource() {
            super("data".getBytes());
        }

        @Override
        public String getFilename() {
            return "test.pdf";
        }
    }
}
