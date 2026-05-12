package com.example.udtbe.domain.batch.util;

import java.time.LocalDateTime;

public class TimeUtil {


    public static final int SCHEDULED_HOUR = 4;
    public static final int SCHEDULED_MINUTE = 0;
    public static final int SCHEDULED_SECOND = 0;
    public static final int SCHEDULED_NANO = 0;

    public static LocalDateTime getScheduledAt() {
        return LocalDateTime.now();
    }
}
