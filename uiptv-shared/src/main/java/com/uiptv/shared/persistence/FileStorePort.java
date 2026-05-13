package com.uiptv.shared.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface FileStorePort {
    InputStream openRead(String location) throws IOException;

    OutputStream openWrite(String location) throws IOException;

    String createTemporaryFile(String prefix, String suffix) throws IOException;

    void deleteIfExists(String location) throws IOException;
}
