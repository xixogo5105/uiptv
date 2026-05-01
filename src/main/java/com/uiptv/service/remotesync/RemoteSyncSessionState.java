package com.uiptv.service.remotesync;

public record RemoteSyncSessionState(String sessionId,
                                     RemoteSyncDirection direction,
                                     RemoteSyncStatus status,
                                     String verificationCode,
                                     String requesterName,
                                     String requesterAddress,
                                     RemoteSyncOptions options,
                                     String message) {
}
