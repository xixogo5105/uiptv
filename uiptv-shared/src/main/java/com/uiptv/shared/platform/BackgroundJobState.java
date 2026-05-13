package com.uiptv.shared.platform;

public record BackgroundJobState(String jobId,
                                 BackgroundJobStatus status,
                                 int progressPercent,
                                 String message) {
    public BackgroundJobState {
        if (jobId == null || jobId.isBlank()) {
            throw new IllegalArgumentException("jobId is required");
        }
        status = status == null ? BackgroundJobStatus.UNKNOWN : status;
        if (progressPercent < 0 || progressPercent > 100) {
            throw new IllegalArgumentException("progressPercent must be between 0 and 100");
        }
        message = message == null ? "" : message;
    }
}
