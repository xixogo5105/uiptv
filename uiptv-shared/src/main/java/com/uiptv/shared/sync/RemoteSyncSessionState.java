package com.uiptv.shared.sync;

public record RemoteSyncSessionState(String sessionId,
                                     RemoteSyncDirection direction,
                                     RemoteSyncStatus status,
                                     String verificationCode,
                                     String requesterName,
                                     String requesterAddress,
                                     RemoteSyncOptions options,
                                     String message) {
    public RemoteSyncSessionState {
        sessionId = sessionId == null ? "" : sessionId;
        if (direction == null) {
            throw new IllegalArgumentException("direction is required");
        }
        status = status == null ? RemoteSyncStatus.FAILED : status;
        verificationCode = verificationCode == null ? "" : verificationCode;
        requesterName = requesterName == null ? "" : requesterName;
        requesterAddress = requesterAddress == null ? "" : requesterAddress;
        options = options == null ? new RemoteSyncOptions(false, false) : options;
        message = message == null ? "" : message;
    }
}
