package com.uiptv.service.remotesync;

public record RemoteSyncApprovalRequest(String sessionId,
                                        RemoteSyncDirection direction,
                                        String verificationCode,
                                        String requesterName,
                                        String requesterAddress,
                                        RemoteSyncOptions options) {
}
