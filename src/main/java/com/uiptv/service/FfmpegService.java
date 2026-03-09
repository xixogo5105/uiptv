package com.uiptv.service;

import com.uiptv.util.ServerUrlUtil;

import java.io.IOException;
import java.util.List;

public class FfmpegService extends AbstractFfmpegHlsService {
    private FfmpegService() {
    }

    private static class SingletonHelper {
        private static final FfmpegService INSTANCE = new FfmpegService();
    }

    public static FfmpegService getInstance() {
        return SingletonHelper.INSTANCE;
    }

    public boolean isTransmuxingNeeded(String url) {
        return url != null && url.contains("extension=ts");
    }

    public synchronized boolean startTransmuxing(String inputUrl) throws IOException {
        return startTransmuxing(inputUrl, false);
    }

    public synchronized boolean startTransmuxing(String inputUrl, boolean vodStylePlaylist) throws IOException {
        String outputUrl = ServerUrlUtil.getLocalServerUrl() + "/hls-upload/" + STREAM_FILENAME;
        return startManagedHlsStream(buildHlsCommand(inputUrl, outputUrl, vodStylePlaylist));
    }

    static List<String> buildHlsCommand(String inputUrl, String outputUrl, boolean vodStylePlaylist) {
        return buildCopyHlsCommand(inputUrl, outputUrl, vodStylePlaylist, 0L);
    }

    public synchronized void stopTransmuxing() {
        stopManagedHlsStream();
    }
}
