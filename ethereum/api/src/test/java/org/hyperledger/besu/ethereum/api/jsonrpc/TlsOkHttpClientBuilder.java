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

import org.hyperledger.besu.ethereum.api.tls.SelfSignedP12Certificate;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.List;
import java.util.Optional;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;
import okhttp3.Protocol;

public final class TlsOkHttpClientBuilder {
  private SelfSignedP12Certificate besuCertificate;
  private SelfSignedP12Certificate okHttpCertificate;
  private boolean useHttp1 = false;

  private TlsOkHttpClientBuilder() {}

  public static TlsOkHttpClientBuilder anOkHttpClient() {
    return new TlsOkHttpClientBuilder();
  }

  public TlsOkHttpClientBuilder withBesuCertificate(
      final SelfSignedP12Certificate besuCertificate) {
    this.besuCertificate = besuCertificate;
    return this;
  }

  public TlsOkHttpClientBuilder withOkHttpCertificate(
      final SelfSignedP12Certificate okHttpCertificate) {
    this.okHttpCertificate = okHttpCertificate;
    return this;
  }

  public TlsOkHttpClientBuilder withHttp1(final boolean useHttp1) {
    this.useHttp1 = useHttp1;
    return this;
  }

  public OkHttpClient build() {
    return buildHttpClient();
  }

  private OkHttpClient buildHttpClient() {
    final TrustManagerFactory trustManagerFactory = getTrustManagerFactory();
    final Optional<KeyManagerFactory> keyManagerFactory = getKeyManagerFactory();
    try {
      final OkHttpClient.Builder builder =
          new OkHttpClient.Builder()
              .sslSocketFactory(
                  getCustomSslContext(trustManagerFactory, keyManagerFactory).getSocketFactory(),
                  (X509TrustManager)
                      trustManagerFactory.getTrustManagers()[0]); // we only have one trust manager
      if (useHttp1) {
        builder.protocols(List.of(Protocol.HTTP_1_1));
      }
      return builder.build();
    } catch (final GeneralSecurityException e) {
      throw new RuntimeException(e);
    }
  }

  private Optional<KeyManagerFactory> getKeyManagerFactory() {
    if (okHttpCertificate == null) {
      return Optional.empty();
    }

    try {
      final char[] keyStorePassword = okHttpCertificate.getPassword();
      final KeyStore keyStore =
          KeyStore.getInstance(okHttpCertificate.getKeyStoreFile().toFile(), keyStorePassword);
      final KeyManagerFactory keyManagerFactory =
          KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      keyManagerFactory.init(keyStore, keyStorePassword);
      return Optional.of(keyManagerFactory);
    } catch (final IOException | GeneralSecurityException e) {
      throw new RuntimeException("Unable to load key manager factory", e);
    }
  }

  private TrustManagerFactory getTrustManagerFactory() {
    try {
      final KeyStore trustStore =
          KeyStore.getInstance(
              besuCertificate.getTrustStoreFile().toFile(), besuCertificate.getPassword());
      final TrustManagerFactory trustManagerFactory =
          TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      trustManagerFactory.init(trustStore);
      return trustManagerFactory;
    } catch (final IOException | GeneralSecurityException e) {
      throw new RuntimeException("Unable to load trust manager factory", e);
    }
  }

  private SSLContext getCustomSslContext(
      final TrustManagerFactory trustManagerFactory,
      final Optional<KeyManagerFactory> keyManagerFactory)
      throws GeneralSecurityException {
    final KeyManager[] km = keyManagerFactory.map(KeyManagerFactory::getKeyManagers).orElse(null);
    final TrustManager[] tm = trustManagerFactory.getTrustManagers();
    final SSLContext sslContext = SSLContext.getInstance("TLS");
    sslContext.init(km, tm, createSecureRandom());
    return sslContext;
  }
}
