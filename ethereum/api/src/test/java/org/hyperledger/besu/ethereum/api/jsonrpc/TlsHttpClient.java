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

import org.hyperledger.besu.ethereum.api.tls.TlsConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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
  private final TlsConfiguration serverTrustConfiguration;
  private final TlsConfiguration clientCertConfiguration;
  private TrustManagerFactory trustManagerFactory;
  private KeyManagerFactory keyManagerFactory;
  private OkHttpClient client;

  private TlsHttpClient(
      final TlsConfiguration serverTrustConfiguration,
      final TlsConfiguration clientCertConfiguration) {
    this.serverTrustConfiguration = serverTrustConfiguration;
    this.clientCertConfiguration = clientCertConfiguration;
  }

  public static TlsHttpClient fromServerTrustConfiguration(
      final TlsConfiguration serverTrustConfiguration) {
    final TlsHttpClient tlsHttpClient = new TlsHttpClient(serverTrustConfiguration, null);
    tlsHttpClient.initHttpClient();
    return tlsHttpClient;
  }

  public static TlsHttpClient fromServerTrustAndClientCertConfiguration(
      final TlsConfiguration serverTrustConfiguration,
      final TlsConfiguration clientCertConfiguration) {
    final TlsHttpClient tlsHttpClient =
        new TlsHttpClient(serverTrustConfiguration, clientCertConfiguration);
    tlsHttpClient.initHttpClient();
    return tlsHttpClient;
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
      final KeyStore trustStore =
          loadP12KeyStore(
              serverTrustConfiguration.getKeyStorePath(),
              serverTrustConfiguration.getKeyStorePassword().toCharArray());
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
      final char[] keyStorePassword = clientCertConfiguration.getKeyStorePassword().toCharArray();
      final KeyStore keyStore =
          loadP12KeyStore(clientCertConfiguration.getKeyStorePath(), keyStorePassword);
      final KeyManagerFactory keyManagerFactory =
          KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      keyManagerFactory.init(keyStore, keyStorePassword);
      this.keyManagerFactory = keyManagerFactory;
    } catch (final GeneralSecurityException gse) {
      throw new RuntimeException("Unable to load key manager factory", gse);
    }
  }

  private boolean isClientAuthRequired() {
    return clientCertConfiguration != null;
  }

  private KeyStore loadP12KeyStore(final Path keyStore, final char[] password)
      throws KeyStoreException, NoSuchAlgorithmException, CertificateException {
    final KeyStore store = KeyStore.getInstance("pkcs12");
    try (final InputStream keystoreStream = Files.newInputStream(keyStore)) {
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
}
