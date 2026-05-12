package com.example.udtbe.domain.batch.dto;

public record IntegrityCheckResult(
        int totalChecked,
        int missingInIndex,
        int orphanedInIndex,
        int fixed,
        boolean fullRebuildTriggered
) {

}
