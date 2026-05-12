package com.example.udtbe.domain.batch.listener;

import com.example.udtbe.domain.batch.util.BatchRetryProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

@Deprecated(since = "스트리밍 전환으로 배치 완료 리스너 미사용.")
@Component
@RequiredArgsConstructor
@Slf4j
public class JobCompletionListener implements JobExecutionListener {

    private final BatchRetryProcessor batchRetryProcessor;

    @Override
    public void afterJob(JobExecution jobExecution) {
        batchRetryProcessor.resetSystemFailure();
        log.info("배치 작업 완료 서버 에러 리셋");
    }
}
