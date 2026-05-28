package com.example.udtbe.domain.scheduling.dto;

public record IntegrityCheckResult(
        int totalChecked,
        int missingInIndex,
        int orphanedInIndex,
        int fixed,
        boolean fullRebuildTriggered
) {

}
