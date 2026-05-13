package com.uiptv.service.remotesync;

public record RemoteSyncRequest(RemoteSyncDirection direction,
                                String verificationCode,
                                String requesterName,
                                RemoteSyncOptions options) {
}
