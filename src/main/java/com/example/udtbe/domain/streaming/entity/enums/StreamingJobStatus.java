package com.example.udtbe.domain.streaming.entity.enums;

import com.example.udtbe.global.exception.RestApiException;
import com.example.udtbe.global.exception.code.EnumErrorCode;
import java.util.Arrays;

public enum StreamingJobStatus {
    COMPLETED, FAILED, PARTIAL_COMPLETED, NOOP;

    public static StreamingJobStatus from(String value) {
        return Arrays.stream(values())
                .filter(b -> b.name().equals(value))
                .findFirst()
                .orElseThrow(() -> new RestApiException(EnumErrorCode.BATCH_JOB_TYPE_BAD_REQUEST));
    }
}
