package stirling.software.common.controller;

import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpSession;

import jakarta.servlet.http.HttpServletRequest;

import stirling.software.common.model.job.JobResult;
import stirling.software.common.service.FileStorage;
import stirling.software.common.service.JobQueue;
import stirling.software.common.service.TaskManager;

class JobControllerTest {

    @Mock private TaskManager taskManager;

    @Mock private FileStorage fileStorage;

    @Mock private JobQueue jobQueue;

    @Mock private HttpServletRequest request;

    private MockHttpSession session;

    @InjectMocks private JobController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Setup mock session for tests
        session = new MockHttpSession();
        Mockito.when(request.getSession()).thenReturn(session);
    }

    @Test
    void testGetJobStatus_ExistingJob() {
        // Arrange
        String jobId = "test-job-id";
        JobResult mockResult = new JobResult();
        mockResult.setJobId(jobId);
        Mockito.when(taskManager.getJobResult(jobId)).thenReturn(mockResult);

        // Act
        ResponseEntity<?> response = controller.getJobStatus(jobId);

        // Assert
        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertEquals(mockResult, response.getBody());
    }

    @Test
    void testGetJobStatus_ExistingJobInQueue() {
        // Arrange
        String jobId = "test-job-id";
        JobResult mockResult = new JobResult();
        mockResult.setJobId(jobId);
        mockResult.setComplete(false);
        Mockito.when(taskManager.getJobResult(jobId)).thenReturn(mockResult);
        Mockito.when(jobQueue.isJobQueued(jobId)).thenReturn(true);
        Mockito.when(jobQueue.getJobPosition(jobId)).thenReturn(3);

        // Act
        ResponseEntity<?> response = controller.getJobStatus(jobId);

        // Assert
        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
        Assertions.assertEquals(mockResult, Objects.requireNonNull(responseBody).get("jobResult"));

        @SuppressWarnings("unchecked")
        Map<String, Object> queueInfo = (Map<String, Object>) responseBody.get("queueInfo");
        Assertions.assertTrue((Boolean) queueInfo.get("inQueue"));
        Assertions.assertEquals(3, queueInfo.get("position"));
    }

    @Test
    void testGetJobStatus_NonExistentJob() {
        // Arrange
        String jobId = "non-existent-job";
        Mockito.when(taskManager.getJobResult(jobId)).thenReturn(null);

        // Act
        ResponseEntity<?> response = controller.getJobStatus(jobId);

        // Assert
        Assertions.assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void testGetJobResult_CompletedSuccessfulWithObject() {
        // Arrange
        String jobId = "test-job-id";
        JobResult mockResult = new JobResult();
        mockResult.setJobId(jobId);
        mockResult.setComplete(true);
        String resultObject = "Test result";
        mockResult.completeWithResult(resultObject);

        Mockito.when(taskManager.getJobResult(jobId)).thenReturn(mockResult);

        // Act
        ResponseEntity<?> response = controller.getJobResult(jobId);

        // Assert
        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertEquals(resultObject, response.getBody());
    }

    @Test
    void testGetJobResult_CompletedSuccessfulWithFile() throws Exception {
        // Arrange
        String jobId = "test-job-id";
        String fileId = "file-id";
        String originalFileName = "test.pdf";
        String contentType = MediaType.APPLICATION_PDF_VALUE;
        byte[] fileContent = "Test file content".getBytes();

        JobResult mockResult = new JobResult();
        mockResult.setJobId(jobId);
        mockResult.completeWithSingleFile(
                fileId, originalFileName, contentType, fileContent.length);

        Mockito.when(taskManager.getJobResult(jobId)).thenReturn(mockResult);
        Mockito.when(fileStorage.retrieveBytes(fileId)).thenReturn(fileContent);

        // Act
        ResponseEntity<?> response = controller.getJobResult(jobId);

        // Assert
        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertEquals(contentType, response.getHeaders().getFirst("Content-Type"));
        Assertions.assertTrue(
                Objects.requireNonNull(response.getHeaders().getFirst("Content-Disposition"))
                        .contains(originalFileName));
        Assertions.assertEquals(fileContent, response.getBody());
    }

    @Test
    void testGetJobResult_CompletedWithError() {
        // Arrange
        String jobId = "test-job-id";
        String errorMessage = "Test error";

        JobResult mockResult = new JobResult();
        mockResult.setJobId(jobId);
        mockResult.failWithError(errorMessage);

        Mockito.when(taskManager.getJobResult(jobId)).thenReturn(mockResult);

        // Act
        ResponseEntity<?> response = controller.getJobResult(jobId);

        // Assert
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Assertions.assertTrue(
                Objects.requireNonNull(response.getBody()).toString().contains(errorMessage));
    }

    @Test
    void testGetJobResult_IncompleteJob() {
        // Arrange
        String jobId = "test-job-id";

        JobResult mockResult = new JobResult();
        mockResult.setJobId(jobId);
        mockResult.setComplete(false);

        Mockito.when(taskManager.getJobResult(jobId)).thenReturn(mockResult);

        // Act
        ResponseEntity<?> response = controller.getJobResult(jobId);

        // Assert
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Assertions.assertTrue(
                Objects.requireNonNull(response.getBody()).toString().contains("not complete"));
    }

    @Test
    void testGetJobResult_NonExistentJob() {
        // Arrange
        String jobId = "non-existent-job";
        Mockito.when(taskManager.getJobResult(jobId)).thenReturn(null);

        // Act
        ResponseEntity<?> response = controller.getJobResult(jobId);

        // Assert
        Assertions.assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void testGetJobResult_ErrorRetrievingFile() throws Exception {
        // Arrange
        String jobId = "test-job-id";
        String fileId = "file-id";
        String originalFileName = "test.pdf";
        String contentType = MediaType.APPLICATION_PDF_VALUE;

        JobResult mockResult = new JobResult();
        mockResult.setJobId(jobId);
        mockResult.completeWithSingleFile(fileId, originalFileName, contentType, 1024L);

        Mockito.when(taskManager.getJobResult(jobId)).thenReturn(mockResult);
        Mockito.when(fileStorage.retrieveBytes(fileId))
                .thenThrow(new RuntimeException("File not found"));

        // Act
        ResponseEntity<?> response = controller.getJobResult(jobId);

        // Assert
        Assertions.assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        Assertions.assertTrue(
                Objects.requireNonNull(response.getBody())
                        .toString()
                        .contains("Error retrieving file"));
    }

    /*
     * @Test void testGetJobStats() { // Arrange JobStats mockStats =
     * JobStats.builder() .totalJobs(10) .activeJobs(3) .completedJobs(7) .build();
     *
     * when(taskManager.getJobStats()).thenReturn(mockStats);
     *
     * // Act ResponseEntity<?> response = controller.getJobStats();
     *
     * // Assert assertEquals(HttpStatus.OK, response.getStatusCode());
     * assertEquals(mockStats, response.getBody()); }
     *
     * @Test void testCleanupOldJobs() { // Arrange when(taskManager.getJobStats())
     * .thenReturn(JobStats.builder().totalJobs(10).build())
     * .thenReturn(JobStats.builder().totalJobs(7).build());
     *
     * // Act ResponseEntity<?> response = controller.cleanupOldJobs();
     *
     * // Assert assertEquals(HttpStatus.OK, response.getStatusCode());
     *
     * @SuppressWarnings("unchecked") Map<String, Object> responseBody =
     * (Map<String, Object>) response.getBody(); assertEquals("Cleanup complete",
     * responseBody.get("message")); assertEquals(3,
     * responseBody.get("removedJobs")); assertEquals(7,
     * responseBody.get("remainingJobs"));
     *
     * verify(taskManager).cleanupOldJobs(); }
     *
     * @Test void testGetQueueStats() { // Arrange Map<String, Object>
     * mockQueueStats = Map.of( "queuedJobs", 5, "queueCapacity", 10,
     * "resourceStatus", "OK" );
     *
     * when(jobQueue.getQueueStats()).thenReturn(mockQueueStats);
     *
     * // Act ResponseEntity<?> response = controller.getQueueStats();
     *
     * // Assert assertEquals(HttpStatus.OK, response.getStatusCode());
     * assertEquals(mockQueueStats, response.getBody());
     * verify(jobQueue).getQueueStats(); }
     */
    @Test
    void testCancelJob_InQueue() {
        // Arrange
        String jobId = "job-in-queue";

        // Setup user session with job authorization
        java.util.Set<String> userJobIds = new java.util.HashSet<>();
        userJobIds.add(jobId);
        session.setAttribute("userJobIds", userJobIds);

        Mockito.when(jobQueue.isJobQueued(jobId)).thenReturn(true);
        Mockito.when(jobQueue.getJobPosition(jobId)).thenReturn(2);
        Mockito.when(jobQueue.cancelJob(jobId)).thenReturn(true);

        // Act
        ResponseEntity<?> response = controller.cancelJob(jobId);

        // Assert
        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
        Assertions.assertEquals(
                "Job cancelled successfully", Objects.requireNonNull(responseBody).get("message"));
        Assertions.assertTrue((Boolean) responseBody.get("wasQueued"));
        Assertions.assertEquals(2, responseBody.get("queuePosition"));

        Mockito.verify(jobQueue).cancelJob(jobId);
        Mockito.verify(taskManager, Mockito.never())
                .setError(ArgumentMatchers.anyString(), ArgumentMatchers.anyString());
    }

    @Test
    void testCancelJob_Running() {
        // Arrange
        String jobId = "job-running";
        JobResult jobResult = new JobResult();
        jobResult.setJobId(jobId);
        jobResult.setComplete(false);

        // Setup user session with job authorization
        java.util.Set<String> userJobIds = new java.util.HashSet<>();
        userJobIds.add(jobId);
        session.setAttribute("userJobIds", userJobIds);

        Mockito.when(jobQueue.isJobQueued(jobId)).thenReturn(false);
        Mockito.when(taskManager.getJobResult(jobId)).thenReturn(jobResult);

        // Act
        ResponseEntity<?> response = controller.cancelJob(jobId);

        // Assert
        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
        Assertions.assertEquals(
                "Job cancelled successfully", Objects.requireNonNull(responseBody).get("message"));
        Assertions.assertFalse((Boolean) responseBody.get("wasQueued"));
        Assertions.assertEquals("n/a", responseBody.get("queuePosition"));

        Mockito.verify(jobQueue, Mockito.never()).cancelJob(jobId);
        Mockito.verify(taskManager).setError(jobId, "Job was cancelled by user");
    }

    @Test
    void testCancelJob_NotFound() {
        // Arrange
        String jobId = "non-existent-job";

        // Setup user session with job authorization
        java.util.Set<String> userJobIds = new java.util.HashSet<>();
        userJobIds.add(jobId);
        session.setAttribute("userJobIds", userJobIds);

        Mockito.when(jobQueue.isJobQueued(jobId)).thenReturn(false);
        Mockito.when(taskManager.getJobResult(jobId)).thenReturn(null);

        // Act
        ResponseEntity<?> response = controller.cancelJob(jobId);

        // Assert
        Assertions.assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void testCancelJob_AlreadyComplete() {
        // Arrange
        String jobId = "completed-job";
        JobResult jobResult = new JobResult();
        jobResult.setJobId(jobId);
        jobResult.setComplete(true);

        // Setup user session with job authorization
        java.util.Set<String> userJobIds = new java.util.HashSet<>();
        userJobIds.add(jobId);
        session.setAttribute("userJobIds", userJobIds);

        Mockito.when(jobQueue.isJobQueued(jobId)).thenReturn(false);
        Mockito.when(taskManager.getJobResult(jobId)).thenReturn(jobResult);

        // Act
        ResponseEntity<?> response = controller.cancelJob(jobId);

        // Assert
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
        Assertions.assertEquals(
                "Cannot cancel job that is already complete",
                Objects.requireNonNull(responseBody).get("message"));
    }

    @Test
    void testCancelJob_Unauthorized() {
        // Arrange
        String jobId = "unauthorized-job";

        // Setup user session with other job IDs but not this one
        java.util.Set<String> userJobIds = new java.util.HashSet<>();
        userJobIds.add("other-job-1");
        userJobIds.add("other-job-2");
        session.setAttribute("userJobIds", userJobIds);

        // Act
        ResponseEntity<?> response = controller.cancelJob(jobId);

        // Assert
        Assertions.assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());

        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
        Assertions.assertEquals(
                "You are not authorized to cancel this job",
                Objects.requireNonNull(responseBody).get("message"));

        // Verify no cancellation attempts were made
        Mockito.verify(jobQueue, Mockito.never()).isJobQueued(ArgumentMatchers.anyString());
        Mockito.verify(jobQueue, Mockito.never()).cancelJob(ArgumentMatchers.anyString());
        Mockito.verify(taskManager, Mockito.never()).getJobResult(ArgumentMatchers.anyString());
        Mockito.verify(taskManager, Mockito.never())
                .setError(ArgumentMatchers.anyString(), ArgumentMatchers.anyString());
    }
}
