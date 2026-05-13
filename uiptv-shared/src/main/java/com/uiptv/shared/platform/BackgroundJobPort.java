package com.uiptv.shared.platform;

public interface BackgroundJobPort {
    String enqueue(BackgroundJobRequest request) throws BackgroundJobException;

    BackgroundJobState state(String jobId) throws BackgroundJobException;

    void cancel(String jobId) throws BackgroundJobException;
}
