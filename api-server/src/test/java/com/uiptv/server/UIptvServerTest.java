package com.uiptv.server;

import com.uiptv.model.Configuration;
import com.uiptv.service.ConfigurationService;
import com.uiptv.testsupport.DbBackedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import static org.junit.jupiter.api.Assertions.*;

class UIptvServerTest extends DbBackedTest {
    @TempDir
    Path tempDir;

    @Test
    void startEnsureStop_lifecycleWorks() throws Exception {
        Configuration configuration = ConfigurationService.getInstance().read();
        configuration.setServerPort("0");
        ConfigurationService.getInstance().save(configuration);

        try {
            assertTrue(UIptvServer.ensureStarted());
            assertFalse(UIptvServer.ensureStarted());
            assertTrue(UIptvServer.isRunning());
        } finally {
            UIptvServer.stop();
        }

        assertFalse(UIptvServer.isRunning());
        UIptvServer.stop();
    }

    @Test
    void getHttpPort_defaultsWhenBlank() throws Exception {
        Configuration configuration = ConfigurationService.getInstance().read();
        configuration.setServerPort("");
        ConfigurationService.getInstance().save(configuration);

        Method method = UIptvServer.class.getDeclaredMethod("getHttpPort");
        method.setAccessible(true);
        assertEquals("8888", method.invoke(null));
    }

    @Test
    void startUsesConfiguredPort() throws Exception {
        Configuration configuration = ConfigurationService.getInstance().read();
        configuration.setServerPort("0");
        ConfigurationService.getInstance().save(configuration);

        try {
            UIptvServer.start();
            assertTrue(UIptvServer.isRunning());
        } finally {
            UIptvServer.stop();
        }
    }

    @Test
    void startWithHttpsEnabled_createsLocalTlsListener() throws Exception {
        String originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", tempDir.toString());

        try {
            Configuration configuration = ConfigurationService.getInstance().read();
            configuration.setServerPort("0");
            int httpsPort = freePort();
            configuration.setHttpsServerEnabled(true);
            configuration.setHttpsServerPort(String.valueOf(httpsPort));
            ConfigurationService.getInstance().save(configuration);

            UIptvServer.start();
            assertTrue(UIptvServer.isRunning());
            assertEquals(200, httpsStatus(httpsPort));
        } finally {
            UIptvServer.stop();
            System.setProperty("user.home", originalUserHome);
        }
    }

    @Test
    void getHttpsPort_defaultsWhenBlank() throws Exception {
        Configuration configuration = ConfigurationService.getInstance().read();
        configuration.setHttpsServerPort("");
        ConfigurationService.getInstance().save(configuration);

        Method method = UIptvServer.class.getDeclaredMethod("getHttpsPort");
        method.setAccessible(true);
        assertEquals("8443", method.invoke(null));
    }

    private int freePort() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            return serverSocket.getLocalPort();
        }
    }

    private int httpsStatus(int port) throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, new TrustManager[]{trustAllManager()}, new SecureRandom());
        HttpsURLConnection connection = (HttpsURLConnection) URI.create("https://127.0.0.1:" + port + "/manifest.json")
                .toURL()
                .openConnection();
        connection.setSSLSocketFactory(sslContext.getSocketFactory());
        connection.setConnectTimeout(2_000);
        connection.setReadTimeout(2_000);
        return connection.getResponseCode();
    }

    private X509TrustManager trustAllManager() {
        return new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
                // Test-only trust manager.
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
                // Test-only trust manager.
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
    }
}
