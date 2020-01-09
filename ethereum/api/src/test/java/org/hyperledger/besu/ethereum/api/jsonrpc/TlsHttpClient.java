/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.api.jsonrpc;

import static org.hyperledger.besu.crypto.SecureRandomProvider.createSecureRandom;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;

public class TlsHttpClient {
  private final String keyStoreResource;
  private final String keyStorePasswordResource;
  private final String trustStoreResource;
  private final String trustStorePasswordResource;
  private OkHttpClient client;
  private TrustManagerFactory trustManagerFactory;
  private KeyManagerFactory keyManagerFactory;

  private TlsHttpClient(
      final String keyStoreResource,
      final String keyStorePasswordResource,
      final String trustStoreResource,
      final String trustStorePasswordResource) {
    this.keyStoreResource = keyStoreResource;
    this.keyStorePasswordResource = keyStorePasswordResource;
    this.trustStoreResource = trustStoreResource;
    this.trustStorePasswordResource = trustStorePasswordResource;
  }

  public OkHttpClient getHttpClient() {
    return client;
  }

  private void initHttpClient() {
    initTrustManagerFactory();
    initKeyManagerFactory();
    try {
      client =
          new OkHttpClient.Builder()
              .sslSocketFactory(
                  getCustomSslContext().getSocketFactory(),
                  (X509TrustManager)
                      trustManagerFactory.getTrustManagers()[0]) // we only have one trust manager
              .build();
    } catch (final GeneralSecurityException e) {
      throw new RuntimeException(e);
    }
  }

  private void initTrustManagerFactory() {
    try {
      final char[] password =
          JsonRpcHttpServiceTlsTest.getKeystorePassword(trustStorePasswordResource);
      final KeyStore trustStore = loadP12KeyStore(trustStoreResource, password);
      final TrustManagerFactory trustManagerFactory =
          TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      trustManagerFactory.init(trustStore);
      this.trustManagerFactory = trustManagerFactory;
    } catch (final GeneralSecurityException gse) {
      throw new RuntimeException("Unable to load trust manager factory", gse);
    }
  }

  private void initKeyManagerFactory() {
    if (!isClientAuthRequired()) {
      return;
    }

    try {
      final char[] password =
          JsonRpcHttpServiceTlsTest.getKeystorePassword(keyStorePasswordResource);
      final KeyStore keyStore = loadP12KeyStore(keyStoreResource, password);
      final KeyManagerFactory keyManagerFactory =
          KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      keyManagerFactory.init(keyStore, password);
      this.keyManagerFactory = keyManagerFactory;
    } catch (final GeneralSecurityException gse) {
      throw new RuntimeException("Unable to load key manager factory", gse);
    }
  }

  private boolean isClientAuthRequired() {
    return keyStoreResource != null;
  }

  private KeyStore loadP12KeyStore(final String resource, final char[] password)
      throws KeyStoreException, NoSuchAlgorithmException, CertificateException {
    final KeyStore store = KeyStore.getInstance("pkcs12");
    try (final InputStream keystoreStream = ClassLoader.getSystemResource(resource).openStream()) {
      store.load(keystoreStream, password);
    } catch (final IOException e) {
      throw new RuntimeException("Unable to load keystore.", e);
    }
    return store;
  }

  private SSLContext getCustomSslContext() throws GeneralSecurityException {
    final KeyManager[] km = isClientAuthRequired() ? keyManagerFactory.getKeyManagers() : null;
    final TrustManager[] tm = trustManagerFactory.getTrustManagers();
    final SSLContext sslContext = SSLContext.getInstance("TLS");
    sslContext.init(km, tm, createSecureRandom());
    return sslContext;
  }

  public static final class TlsHttpClientBuilder {
    private String keyStoreResource;
    private String keyStorePasswordResource;
    private String trustStoreResource;
    private String trustStorePasswordResource;

    private TlsHttpClientBuilder() {}

    public static TlsHttpClientBuilder aTlsHttpClient() {
      return new TlsHttpClientBuilder();
    }

    public TlsHttpClientBuilder withKeyStoreResource(final String keyStoreResource) {
      this.keyStoreResource = keyStoreResource;
      return this;
    }

    public TlsHttpClientBuilder withKeyStorePasswordResource(
        final String keyStorePasswordResource) {
      this.keyStorePasswordResource = keyStorePasswordResource;
      return this;
    }

    public TlsHttpClientBuilder withTrustStoreResource(final String trustStoreResource) {
      this.trustStoreResource = trustStoreResource;
      return this;
    }

    public TlsHttpClientBuilder withTrustStorePasswordResource(
        final String trustStorePasswordResource) {
      this.trustStorePasswordResource = trustStorePasswordResource;
      return this;
    }

    public TlsHttpClient build() {
      final TlsHttpClient tlsHttpClient =
          new TlsHttpClient(
              keyStoreResource,
              keyStorePasswordResource,
              trustStoreResource,
              trustStorePasswordResource);
      tlsHttpClient.initHttpClient();
      return tlsHttpClient;
    }
  }
}
