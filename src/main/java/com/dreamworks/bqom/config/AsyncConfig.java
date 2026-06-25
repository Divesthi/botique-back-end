package com.dreamworks.bqom.config;

import com.dreamworks.bqom.service.instagram.InstagramPostService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configures a dedicated thread pool for {@code @Async} Instagram publishing tasks.
 *
 * <h2>Why a dedicated pool?</h2>
 * <p>Instagram publishing involves multiple sequential external HTTP calls
 * (ImgBB upload × N, Meta container × N, Meta publish × 1). Each call can
 * take 1–5 seconds. Without a dedicated pool, heavy publishing activity would
 * exhaust Spring's default {@code SimpleAsyncTaskExecutor} (which creates an
 * unbounded number of threads) and starve other async tasks (e.g., WhatsApp
 * notifications from {@link com.dreamworks.bqom.service.notification.NotificationDispatcher}).
 *
 * <h2>Pool sizing guidance</h2>
 * <ul>
 *   <li><b>coreSize</b> — always-available threads. Set to expected steady-state
 *       concurrent posts (4 means 4 boutiques can post simultaneously without queuing).</li>
 *   <li><b>maxSize</b> — burst capacity. Threads above coreSize are created when
 *       the queue is full and destroyed after idle.</li>
 *   <li><b>queueCapacity</b> — backlog buffer. Requests queue here when all core
 *       threads are busy. If the queue fills up and maxSize is reached, the caller
 *       gets a {@code RejectedExecutionException}.</li>
 * </ul>
 *
 * <h2>Thread naming</h2>
 * <p>All threads are named {@code ig-post-N}, making them easy to identify
 * in thread dumps and APM tools.
 */
@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig {

    @Value("${spring.task.execution.pool.core-size:4}")
    private int coreSize;

    @Value("${spring.task.execution.pool.max-size:16}")
    private int maxSize;

    @Value("${spring.task.execution.pool.queue-capacity:100}")
    private int queueCapacity;

    @Value("${spring.task.execution.thread-name-prefix:ig-post-}")
    private String threadNamePrefix;

    /**
     * The bean name {@code "instagramPostExecutor"} is referenced by
     * {@code @Async("instagramPostExecutor")} in
     * {@link InstagramPostService}.
     *
     * <p>Spring also creates a default executor bean for {@code @Async} methods
     * that don't specify a name — this bean does NOT replace that default,
     * so notification dispatch and other async tasks are unaffected.
     */
    @Bean("instagramPostExecutor")
    public Executor instagramPostExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);

        // Wait for running tasks to complete on shutdown (graceful drain)
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        executor.initialize();

        log.info("[AsyncConfig] Instagram post executor initialised: " +
                        "coreSize={}, maxSize={}, queueCapacity={}, prefix={}",
                coreSize, maxSize, queueCapacity, threadNamePrefix);

        return executor;
    }
}