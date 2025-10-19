package stirling.software.common.annotations;

import java.util.function.Supplier;

import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;

import stirling.software.common.aop.AutoJobAspect;
import stirling.software.common.model.api.PDFFile;
import stirling.software.common.service.FileStorage;
import stirling.software.common.service.JobExecutorService;
import stirling.software.common.service.JobQueue;
import stirling.software.common.service.ResourceMonitor;

@ExtendWith(MockitoExtension.class)
class AutoJobPostMappingIntegrationTest {

    private AutoJobAspect autoJobAspect;

    @Mock private JobExecutorService jobExecutorService;

    @Mock private HttpServletRequest request;

    @Mock private FileStorage fileStorage;

    @Mock private ResourceMonitor resourceMonitor;

    @Mock private JobQueue jobQueue;

    @BeforeEach
    void setUp() {
        autoJobAspect = new AutoJobAspect(jobExecutorService, request, fileStorage);
    }

    @Mock private ProceedingJoinPoint joinPoint;

    @Mock private AutoJobPostMapping autoJobPostMapping;

    @Captor private ArgumentCaptor<Supplier<Object>> workCaptor;

    @Captor private ArgumentCaptor<Boolean> asyncCaptor;

    @Captor private ArgumentCaptor<Long> timeoutCaptor;

    @Captor private ArgumentCaptor<Boolean> queueableCaptor;

    @Captor private ArgumentCaptor<Integer> resourceWeightCaptor;

    @Test
    void shouldExecuteWithCustomParameters() throws Throwable {
        // Given
        PDFFile pdfFile = new PDFFile();
        pdfFile.setFileId("test-file-id");
        Object[] args = {pdfFile};

        Mockito.when(joinPoint.getArgs()).thenReturn(args);
        Mockito.when(request.getParameter("async")).thenReturn("true");
        Mockito.when(autoJobPostMapping.timeout()).thenReturn(60000L);
        Mockito.when(autoJobPostMapping.retryCount()).thenReturn(3);
        Mockito.when(autoJobPostMapping.trackProgress()).thenReturn(true);
        Mockito.when(autoJobPostMapping.queueable()).thenReturn(true);
        Mockito.when(autoJobPostMapping.resourceWeight()).thenReturn(75);

        MultipartFile mockFile = Mockito.mock(MultipartFile.class);
        Mockito.when(fileStorage.retrieveFile("test-file-id")).thenReturn(mockFile);

        Mockito.when(
                        jobExecutorService.runJobGeneric(
                                ArgumentMatchers.anyBoolean(),
                                ArgumentMatchers.any(Supplier.class),
                                ArgumentMatchers.anyLong(),
                                ArgumentMatchers.anyBoolean(),
                                ArgumentMatchers.anyInt()))
                .thenReturn(ResponseEntity.ok("success"));

        // When
        Object result = autoJobAspect.wrapWithJobExecution(joinPoint, autoJobPostMapping);

        // Then
        Assertions.assertEquals(ResponseEntity.ok("success"), result);

        Mockito.verify(jobExecutorService)
                .runJobGeneric(
                        asyncCaptor.capture(),
                        workCaptor.capture(),
                        timeoutCaptor.capture(),
                        queueableCaptor.capture(),
                        resourceWeightCaptor.capture());

        Assertions.assertTrue(asyncCaptor.getValue(), "Async should be true");
        Assertions.assertEquals(60000L, timeoutCaptor.getValue(), "Timeout should be 60000ms");
        Assertions.assertTrue(queueableCaptor.getValue(), "Queueable should be true");
        Assertions.assertEquals(
                75, resourceWeightCaptor.getValue(), "Resource weight should be 75");

        // Test that file was resolved
        Assertions.assertNotNull(pdfFile.getFileInput(), "File input should be set");
    }

    @Test
    void shouldRetryOnError() throws Throwable {
        // Given
        Mockito.when(joinPoint.getArgs()).thenReturn(new Object[0]);
        Mockito.when(request.getParameter("async")).thenReturn("false");
        Mockito.when(autoJobPostMapping.timeout()).thenReturn(-1L);
        Mockito.when(autoJobPostMapping.retryCount()).thenReturn(2);
        Mockito.when(autoJobPostMapping.trackProgress()).thenReturn(false);
        Mockito.when(autoJobPostMapping.queueable()).thenReturn(false);
        Mockito.when(autoJobPostMapping.resourceWeight()).thenReturn(50);

        // First call throws exception, second succeeds
        Mockito.when(joinPoint.proceed(ArgumentMatchers.any()))
                .thenThrow(new RuntimeException("First attempt failed"))
                .thenReturn(ResponseEntity.ok("retry succeeded"));

        // Mock jobExecutorService to execute the work immediately
        Mockito.when(
                        jobExecutorService.runJobGeneric(
                                ArgumentMatchers.anyBoolean(),
                                ArgumentMatchers.any(Supplier.class),
                                ArgumentMatchers.anyLong(),
                                ArgumentMatchers.anyBoolean(),
                                ArgumentMatchers.anyInt()))
                .thenAnswer(
                        invocation -> {
                            Supplier<Object> work = invocation.getArgument(1);
                            return work.get();
                        });

        // When
        Object result = autoJobAspect.wrapWithJobExecution(joinPoint, autoJobPostMapping);

        // Then
        Assertions.assertEquals(ResponseEntity.ok("retry succeeded"), result);

        // Verify that proceed was called twice (initial attempt + 1 retry)
        Mockito.verify(joinPoint, Mockito.times(2)).proceed(ArgumentMatchers.any());
    }

    @Test
    void shouldHandlePDFFileWithAsyncRequests() throws Throwable {
        // Given
        PDFFile pdfFile = new PDFFile();
        pdfFile.setFileInput(Mockito.mock(MultipartFile.class));
        Object[] args = {pdfFile};

        Mockito.when(joinPoint.getArgs()).thenReturn(args);
        Mockito.when(request.getParameter("async")).thenReturn("true");
        Mockito.when(autoJobPostMapping.retryCount()).thenReturn(1);

        Mockito.when(fileStorage.storeFile(ArgumentMatchers.any(MultipartFile.class)))
                .thenReturn("stored-file-id");
        Mockito.when(fileStorage.retrieveFile("stored-file-id"))
                .thenReturn(Mockito.mock(MultipartFile.class));

        // Mock job executor to return a successful response
        Mockito.when(
                        jobExecutorService.runJobGeneric(
                                ArgumentMatchers.anyBoolean(),
                                ArgumentMatchers.any(Supplier.class),
                                ArgumentMatchers.anyLong(),
                                ArgumentMatchers.anyBoolean(),
                                ArgumentMatchers.anyInt()))
                .thenReturn(ResponseEntity.ok("success"));

        // When
        autoJobAspect.wrapWithJobExecution(joinPoint, autoJobPostMapping);

        // Then
        Assertions.assertEquals(
                "stored-file-id",
                pdfFile.getFileId(),
                "FileId should be set to the stored file id");
        Assertions.assertNotNull(
                pdfFile.getFileInput(), "FileInput should be replaced with persistent file");

        // Verify storage operations
        Mockito.verify(fileStorage).storeFile(ArgumentMatchers.any(MultipartFile.class));
        Mockito.verify(fileStorage).retrieveFile("stored-file-id");
    }
}
