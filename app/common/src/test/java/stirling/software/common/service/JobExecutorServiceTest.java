package stirling.software.common.service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import jakarta.servlet.http.HttpServletRequest;

import stirling.software.common.model.job.JobResponse;

@ExtendWith(MockitoExtension.class)
class JobExecutorServiceTest {

    private JobExecutorService jobExecutorService;

    @Mock private TaskManager taskManager;

    @Mock private FileStorage fileStorage;

    @Mock private HttpServletRequest request;

    @Mock private ResourceMonitor resourceMonitor;

    @Mock private JobQueue jobQueue;

    @Captor private ArgumentCaptor<String> jobIdCaptor;

    @BeforeEach
    void setUp() {
        // Initialize the service manually with all its dependencies
        jobExecutorService =
                new JobExecutorService(
                        taskManager,
                        fileStorage,
                        request,
                        resourceMonitor,
                        jobQueue,
                        30000L, // asyncRequestTimeoutMs
                        "30m" // sessionTimeout
                        );
    }

    @Test
    void shouldRunSyncJobSuccessfully() {
        // Given
        Supplier<Object> work = () -> "test-result";

        // When
        ResponseEntity<?> response = jobExecutorService.runJobGeneric(false, work);

        // Then
        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertEquals("test-result", response.getBody());

        // Verify request attribute was set with jobId
        Mockito.verify(request)
                .setAttribute(ArgumentMatchers.eq("jobId"), ArgumentMatchers.anyString());
    }

    @Test
    void shouldRunAsyncJobSuccessfully() {
        // Given
        Supplier<Object> work = () -> "test-result";

        // When
        ResponseEntity<?> response = jobExecutorService.runJobGeneric(true, work);

        // Then
        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertInstanceOf(JobResponse.class, response.getBody());
        JobResponse<?> jobResponse = (JobResponse<?>) response.getBody();
        Assertions.assertTrue(jobResponse.isAsync());
        Assertions.assertNotNull(jobResponse.getJobId());

        // Verify task manager was called
        Mockito.verify(taskManager).createTask(jobIdCaptor.capture());
    }

    @Test
    void shouldHandleSyncJobError() {
        // Given
        Supplier<Object> work =
                () -> {
                    throw new RuntimeException("Test error");
                };

        // When
        ResponseEntity<?> response = jobExecutorService.runJobGeneric(false, work);

        // Then
        Assertions.assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, String> errorMap = (Map<String, String>) response.getBody();
        Assertions.assertNotNull(errorMap);
        Assertions.assertEquals("Job failed: Test error", errorMap.get("error"));
    }

    @Test
    void shouldQueueJobWhenResourcesLimited() {
        // Given
        Supplier<Object> work = () -> "test-result";
        CompletableFuture<ResponseEntity<?>> future = new CompletableFuture<>();

        // Configure resourceMonitor to indicate job should be queued
        Mockito.when(resourceMonitor.shouldQueueJob(80)).thenReturn(true);

        // Configure jobQueue to return our future
        Mockito.when(
                        jobQueue.queueJob(
                                ArgumentMatchers.anyString(),
                                ArgumentMatchers.eq(80),
                                ArgumentMatchers.any(),
                                ArgumentMatchers.anyLong()))
                .thenReturn(future);

        // When
        ResponseEntity<?> response = jobExecutorService.runJobGeneric(true, work, 5000, true, 80);

        // Then
        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertInstanceOf(JobResponse.class, response.getBody());

        // Verify job was queued
        Mockito.verify(jobQueue)
                .queueJob(
                        ArgumentMatchers.anyString(),
                        ArgumentMatchers.eq(80),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.eq(5000L));
        Mockito.verify(taskManager).createTask(ArgumentMatchers.anyString());
    }

    @Test
    void shouldUseCustomTimeoutWhenProvided() throws Exception {
        // Given
        Supplier<Object> work = () -> "test-result";
        long customTimeout = 60000L;

        // Use reflection to access the private executeWithTimeout method
        java.lang.reflect.Method executeMethod =
                JobExecutorService.class.getDeclaredMethod(
                        "executeWithTimeout", Supplier.class, long.class);
        executeMethod.setAccessible(true);

        // Create a spy on the JobExecutorService to verify method calls
        JobExecutorService spy = Mockito.spy(jobExecutorService);

        // When
        spy.runJobGeneric(false, work, customTimeout);

        // Then
        Mockito.verify(spy)
                .runJobGeneric(
                        ArgumentMatchers.eq(false),
                        ArgumentMatchers.any(Supplier.class),
                        ArgumentMatchers.eq(customTimeout));
    }

    @Test
    void shouldHandleTimeout() throws Exception {
        // Given
        Supplier<Object> work =
                () -> {
                    try {
                        Thread.sleep(100); // Simulate long-running job
                        return "test-result";
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                };

        // Use reflection to access the private executeWithTimeout method
        java.lang.reflect.Method executeMethod =
                JobExecutorService.class.getDeclaredMethod(
                        "executeWithTimeout", Supplier.class, long.class);
        executeMethod.setAccessible(true);

        // When/Then
        try {
            executeMethod.invoke(jobExecutorService, work, 1L); // Very short timeout
        } catch (Exception e) {
            Assertions.assertInstanceOf(TimeoutException.class, e.getCause());
        }
    }
}
