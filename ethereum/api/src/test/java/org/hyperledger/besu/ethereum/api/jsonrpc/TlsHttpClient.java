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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

public class TlsHttpClient {
  private String keyStoreResource;
  private String keyStorePasswordResource;
  private String trustStoreResource;
  private String trustStorePasswordResource;

  public HttpClient getHttpClient() {
    try {
      return HttpClient.newBuilder()
          .sslContext(getCustomSslContext())
          .version(HttpClient.Version.HTTP_1_1)
          .build();
    } catch (GeneralSecurityException e) {
      throw new RuntimeException(e);
    }
  }

  private SSLContext getCustomSslContext() throws GeneralSecurityException {
    final TrustManagerFactory tmf = getTrustManagerFactory();
    final KeyManagerFactory kmf = getKeyManagerFactory();
    final SSLContext sslContext = SSLContext.getInstance("TLS");
    /*
    SecureRandom.getStrongInstance() is causing initial handshake timeout in Virtualized CI environment
    (such as CircleCI). Hence we are using SHA1PRNG instead. This class has been excluded from errorprone
    checks as we don't allow SecureRandom.getInstance call directly.

    See https://stackoverflow.com/questions/137212/how-to-deal-with-a-slow-securerandom-generator
     */
    final SecureRandom secureRandom = SecureRandom.getInstance("SHA1PRNG");
    sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), secureRandom);
    return sslContext;
  }

  private TrustManagerFactory getTrustManagerFactory() throws GeneralSecurityException {
    final char[] password = getKeystorePassword(trustStorePasswordResource);
    final KeyStore trustStore = loadP12KeyStore(trustStoreResource, password);
    final TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("PKIX");
    trustManagerFactory.init(trustStore);
    return trustManagerFactory;
  }

  private KeyManagerFactory getKeyManagerFactory() throws GeneralSecurityException {
    final char[] password = getKeystorePassword(keyStorePasswordResource);
    final KeyStore keyStore = loadP12KeyStore(keyStoreResource, password);
    final KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("PKIX");
    keyManagerFactory.init(keyStore, password);
    return keyManagerFactory;
  }

  private char[] getKeystorePassword(final String passwordResource) {
    try {
      final File passwordFile =
          Paths.get(ClassLoader.getSystemResource(passwordResource).toURI()).toFile();

      return Files.asCharSource(passwordFile, Charsets.UTF_8).readFirstLine().toCharArray();
    } catch (URISyntaxException | IOException e) {
      throw new RuntimeException("Unable to read keystore password file", e);
    }
  }

  private KeyStore loadP12KeyStore(final String resource, final char[] password)
      throws KeyStoreException, NoSuchAlgorithmException, CertificateException {
    final KeyStore store = KeyStore.getInstance("pkcs12");
    try (final InputStream keystoreStream = ClassLoader.getSystemResource(resource).openStream()) {
      store.load(keystoreStream, password);
    } catch (IOException e) {
      throw new RuntimeException("Unable to load keystore.", e);
    }
    return store;
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

    public TlsHttpClientBuilder withKeyStoreResource(String keyStoreResource) {
      this.keyStoreResource = keyStoreResource;
      return this;
    }

    public TlsHttpClientBuilder withKeyStorePasswordResource(String keyStorePasswordResource) {
      this.keyStorePasswordResource = keyStorePasswordResource;
      return this;
    }

    public TlsHttpClientBuilder withTrustStoreResource(String trustStoreResource) {
      this.trustStoreResource = trustStoreResource;
      return this;
    }

    public TlsHttpClientBuilder withTrustStorePasswordResource(String trustStorePasswordResource) {
      this.trustStorePasswordResource = trustStorePasswordResource;
      return this;
    }

    public TlsHttpClient build() {
      TlsHttpClient tlsHttpClient = new TlsHttpClient();
      tlsHttpClient.keyStorePasswordResource = this.keyStorePasswordResource;
      tlsHttpClient.trustStoreResource = this.trustStoreResource;
      tlsHttpClient.trustStorePasswordResource = this.trustStorePasswordResource;
      tlsHttpClient.keyStoreResource = this.keyStoreResource;
      return tlsHttpClient;
    }
  }
}
