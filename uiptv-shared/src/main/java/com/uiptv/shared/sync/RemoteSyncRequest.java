package com.uiptv.shared.sync;

public record RemoteSyncRequest(RemoteSyncDirection direction,
                                String verificationCode,
                                String requesterName,
                                RemoteSyncOptions options) {
    public RemoteSyncRequest {
        if (direction == null) {
            throw new IllegalArgumentException("direction is required");
        }
        verificationCode = verificationCode == null ? "" : verificationCode;
        requesterName = requesterName == null ? "" : requesterName;
        options = options == null ? new RemoteSyncOptions(false, false) : options;
    }
}
