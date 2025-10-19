package stirling.software.common.service;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import stirling.software.common.service.ResourceMonitor.ResourceStatus;

@ExtendWith(MockitoExtension.class)
class JobQueueTest {

    private JobQueue jobQueue;

    @Mock private ResourceMonitor resourceMonitor;

    private final AtomicReference<ResourceStatus> statusRef =
            new AtomicReference<>(ResourceStatus.OK);

    @BeforeEach
    void setUp() {
        // Mark stubbing as lenient to avoid UnnecessaryStubbingException
        Mockito.lenient()
                .when(
                        resourceMonitor.calculateDynamicQueueCapacity(
                                ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt()))
                .thenReturn(10);
        Mockito.lenient().when(resourceMonitor.getCurrentStatus()).thenReturn(statusRef);

        // Initialize JobQueue with mocked ResourceMonitor
        jobQueue = new JobQueue(resourceMonitor);
    }

    @Test
    void shouldQueueJob() {
        String jobId = "test-job-1";
        int resourceWeight = 50;
        Supplier<Object> work = () -> "test-result";
        long timeoutMs = 1000;

        jobQueue.queueJob(jobId, resourceWeight, work, timeoutMs);

        Assertions.assertTrue(jobQueue.isJobQueued(jobId));
        Assertions.assertEquals(1, jobQueue.getTotalQueuedJobs());
    }

    @Test
    void shouldCancelJob() {
        String jobId = "test-job-2";
        Supplier<Object> work = () -> "test-result";

        jobQueue.queueJob(jobId, 50, work, 1000);
        boolean cancelled = jobQueue.cancelJob(jobId);

        Assertions.assertTrue(cancelled);
        Assertions.assertFalse(jobQueue.isJobQueued(jobId));
    }

    @Test
    void shouldGetQueueStats() {
        Mockito.when(resourceMonitor.getCurrentStatus()).thenReturn(statusRef);

        jobQueue.queueJob("job1", 50, () -> "ok", 1000);
        jobQueue.queueJob("job2", 50, () -> "ok", 1000);
        jobQueue.cancelJob("job2");

        Map<String, Object> stats = jobQueue.getQueueStats();

        Assertions.assertEquals(2, stats.get("totalQueuedJobs"));
        Assertions.assertTrue(stats.containsKey("queuedJobs"));
        Assertions.assertTrue(stats.containsKey("resourceStatus"));
    }

    @Test
    void shouldCalculateQueueCapacity() {
        Mockito.when(resourceMonitor.calculateDynamicQueueCapacity(5, 2)).thenReturn(8);
        int capacity = resourceMonitor.calculateDynamicQueueCapacity(5, 2);
        Assertions.assertEquals(8, capacity);
    }

    @Test
    void shouldCheckIfJobIsQueued() {
        String jobId = "job-123";
        Supplier<Object> work = () -> "hello";

        jobQueue.queueJob(jobId, 40, work, 500);

        Assertions.assertTrue(jobQueue.isJobQueued(jobId));
        Assertions.assertFalse(jobQueue.isJobQueued("nonexistent"));
    }
}
