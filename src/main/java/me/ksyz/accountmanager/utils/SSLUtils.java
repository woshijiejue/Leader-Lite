package me.ksyz.accountmanager.utils;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/*
 * This file is derived from https://github.com/ksyzov/AccountManager.
 * Originally licensed under the GNU LGPL.
 *
 * This modified version is licensed under the GNU GPL v3.
 */
public class SSLUtils {
    private static final SSLContext ctx;

    public static SSLContext getSSLContext() {
        return ctx;
    }

    static {
        try {
            List<X509TrustManager> managers = new ArrayList<>();
            TrustManagerFactory system = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            system.init((KeyStore) null);
            for (TrustManager manager : system.getTrustManagers()) {
                if (manager instanceof X509TrustManager) managers.add((X509TrustManager) manager);
            }
            try (InputStream stream = SSLUtils.class.getResourceAsStream("/ssl.jks")) {
                if (stream != null) {
                    KeyStore bundled = KeyStore.getInstance("JKS");
                    bundled.load(stream, "changeit".toCharArray());
                    TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                    factory.init(bundled);
                    for (TrustManager manager : factory.getTrustManagers()) {
                        if (manager instanceof X509TrustManager) managers.add((X509TrustManager) manager);
                    }
                }
            }
            if (managers.isEmpty()) throw new IllegalStateException("No certificate trust managers are available.");
            ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[]{new CombinedTrustManager(managers)}, null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize custom SSLContext", e);
        }
    }

    private static final class CombinedTrustManager implements X509TrustManager {
        private final List<X509TrustManager> managers;

        private CombinedTrustManager(List<X509TrustManager> managers) {
            this.managers = managers;
        }

        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            check(chain, authType, false);
        }

        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            check(chain, authType, true);
        }

        private void check(X509Certificate[] chain, String authType, boolean server) throws CertificateException {
            CertificateException failure = null;
            for (X509TrustManager manager : managers) {
                try {
                    if (server) manager.checkServerTrusted(chain, authType);
                    else manager.checkClientTrusted(chain, authType);
                    return;
                } catch (CertificateException exception) {
                    failure = exception;
                }
            }
            throw failure == null ? new CertificateException("Certificate validation failed.") : failure;
        }

        public X509Certificate[] getAcceptedIssuers() {
            List<X509Certificate> issuers = new ArrayList<>();
            for (X509TrustManager manager : managers) issuers.addAll(Arrays.asList(manager.getAcceptedIssuers()));
            return issuers.toArray(new X509Certificate[0]);
        }
    }
}
