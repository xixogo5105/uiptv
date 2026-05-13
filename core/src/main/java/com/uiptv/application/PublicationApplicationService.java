package com.uiptv.application;

import com.uiptv.service.M3U8PublicationService;

@SuppressWarnings("java:S6548")
public class PublicationApplicationService {
    private final M3U8PublicationService publicationService = M3U8PublicationService.getInstance();

    private PublicationApplicationService() {
    }

    public static PublicationApplicationService getInstance() {
        return SingletonHelper.INSTANCE;
    }

    public String getPublishedM3u8(String requestHost) {
        return publicationService.getPublishedM3u8(requestHost);
    }

    private static class SingletonHelper {
        private static final PublicationApplicationService INSTANCE = new PublicationApplicationService();
    }
}
