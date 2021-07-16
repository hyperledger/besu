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
package org.hyperledger.besu.enclave;

import org.hyperledger.besu.util.InvalidConfigurationException;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

import com.google.common.io.Files;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.net.PfxOptions;
import org.apache.tuweni.net.tls.VertxTrustOptions;

public class EnclaveFactory {

  private final Vertx vertx;
  private static final int CONNECT_TIMEOUT = 1000;
  private static final boolean TRUST_CA = false;

  public EnclaveFactory(final Vertx vertx) {
    this.vertx = vertx;
  }

  public Enclave createVertxEnclave(final URI enclaveUri) {
    final HttpClientOptions clientOptions = createNonTlsClientOptions(enclaveUri);

    final RequestTransmitter vertxTransmitter =
        new VertxRequestTransmitter(vertx.createHttpClient(clientOptions));

    return new Enclave(vertxTransmitter);
  }

  public Enclave createVertxEnclave(
      final URI enclaveUri,
      final Path privacyKeyStoreFile,
      final Path privacyKeyStorePasswordFile,
      final Path privacyAllowlistFile) {

    final HttpClientOptions clientOptions =
        createTlsClientOptions(
            enclaveUri, privacyKeyStoreFile, privacyKeyStorePasswordFile, privacyAllowlistFile);

    final RequestTransmitter vertxTransmitter =
        new VertxRequestTransmitter(vertx.createHttpClient(clientOptions));

    return new Enclave(vertxTransmitter);
  }

  public GoQuorumEnclave createGoQuorumEnclave(final URI enclaveUri) {
    final HttpClientOptions clientOptions = createNonTlsClientOptions(enclaveUri);

    final RequestTransmitter vertxTransmitter =
        new VertxRequestTransmitter(vertx.createHttpClient(clientOptions));

    return new GoQuorumEnclave(vertxTransmitter);
  }

  public GoQuorumEnclave createGoQuorumEnclave(
      final URI enclaveUri,
      final Path privacyKeyStoreFile,
      final Path privacyKeyStorePasswordFile,
      final Path privacyAllowlistFile) {

    final HttpClientOptions clientOptions =
        createTlsClientOptions(
            enclaveUri, privacyKeyStoreFile, privacyKeyStorePasswordFile, privacyAllowlistFile);

    final RequestTransmitter vertxTransmitter =
        new VertxRequestTransmitter(vertx.createHttpClient(clientOptions));

    return new GoQuorumEnclave(vertxTransmitter);
  }

  private HttpClientOptions createNonTlsClientOptions(final URI enclaveUri) {

    if (enclaveUri.getPort() == -1) {
      throw new EnclaveIOException("Illegal URI - no port specified");
    }

    final HttpClientOptions clientOptions = new HttpClientOptions();
    clientOptions.setDefaultHost(enclaveUri.getHost());
    clientOptions.setDefaultPort(enclaveUri.getPort());
    clientOptions.setConnectTimeout(CONNECT_TIMEOUT);
    return clientOptions;
  }

  private HttpClientOptions createTlsClientOptions(
      final URI enclaveUri,
      final Path privacyKeyStoreFile,
      final Path privacyKeyStorePasswordFile,
      final Path privacyAllowlistFile) {

    final HttpClientOptions clientOptions = createNonTlsClientOptions(enclaveUri);
    try {
      if (privacyKeyStoreFile != null && privacyKeyStorePasswordFile != null) {
        clientOptions.setSsl(true);
        clientOptions.setPfxKeyCertOptions(
            convertFrom(privacyKeyStoreFile, privacyKeyStorePasswordFile));
      }
      clientOptions.setTrustOptions(
          VertxTrustOptions.allowlistServers(privacyAllowlistFile, TRUST_CA));
    } catch (final NoSuchFileException e) {
      throw new InvalidConfigurationException(
          "Requested file " + e.getMessage() + " does not exist at specified location.");
    } catch (final AccessDeniedException e) {
      throw new InvalidConfigurationException(
          "Current user does not have permissions to access " + e.getMessage());
    } catch (final IllegalArgumentException e) {
      throw new InvalidConfigurationException("Illegally formatted client fingerprint file.");
    } catch (final IOException e) {
      throw new InvalidConfigurationException("Failed to load TLS files " + e.getMessage());
    }
    return clientOptions;
  }

  private static PfxOptions convertFrom(final Path keystoreFile, final Path keystorePasswordFile)
      throws IOException {
    final String password = readSecretFromFile(keystorePasswordFile);
    return new PfxOptions().setPassword(password).setPath(keystoreFile.toString());
  }

  static String readSecretFromFile(final Path path) throws IOException {
    final String password =
        Files.asCharSource(path.toFile(), StandardCharsets.UTF_8).readFirstLine();
    if (password == null || password.isEmpty()) {
      throw new InvalidConfigurationException("Keystore password file is empty: " + path);
    }
    return password;
  }
}
