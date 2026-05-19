package com.openmanus.agentteam.infra;

import com.openmanus.agentteam.domain.model.SubTask;
import com.openmanus.agentteam.domain.model.TaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InMemoryTaskPool Tests")
class InMemoryTaskPoolTest {

    @Test
    @DisplayName("should claim pending task once")
    void shouldClaimPendingTaskOnce() {
        InMemoryTaskGroupRepository repository = new InMemoryTaskGroupRepository();
        InMemoryTaskPool taskPool = new InMemoryTaskPool(repository);
        SubTask subTask = new SubTask("task-1", "group-1", "A", "desc", 1L);

        taskPool.submit(subTask);

        Optional<SubTask> claimed = taskPool.claimNext("agent-1");

        assertThat(claimed).isPresent();
        assertThat(claimed.get().getAssignedAgentId()).isEqualTo("agent-1");
        assertThat(claimed.get().getStatus()).isEqualTo(TaskStatus.CLAIMED);
        assertThat(taskPool.claimNext("agent-2")).isEmpty();
    }

    @Test
    @DisplayName("should prevent duplicate claim under concurrency")
    void shouldPreventDuplicateClaimUnderConcurrency() throws InterruptedException {
        InMemoryTaskGroupRepository repository = new InMemoryTaskGroupRepository();
        InMemoryTaskPool taskPool = new InMemoryTaskPool(repository);
        taskPool.submit(new SubTask("task-1", "group-1", "A", "desc", 1L));

        AtomicInteger successClaims = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executorService = Executors.newFixedThreadPool(2);

        Runnable claimer = () -> {
            ready.countDown();
            try {
                start.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            if (taskPool.claimNext(Thread.currentThread().getName()).isPresent()) {
                successClaims.incrementAndGet();
            }
        };

        executorService.submit(claimer);
        executorService.submit(claimer);
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        executorService.shutdown();
        assertThat(executorService.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        assertThat(successClaims.get()).isEqualTo(1);
    }
}
