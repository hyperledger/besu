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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.hyperledger.besu.enclave.TlsHelpers.populateFingerprintFile;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Optional;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TlsEnabledEnclaveTest {

  private TlsEnabledHttpServerFactory serverFactory;
  private Vertx vertx;

  final TlsCertificateDefinition httpServerCert =
      TlsCertificateDefinition.loadFromResource("tls/cert1.pfx", "password");
  final TlsCertificateDefinition besuCert =
      TlsCertificateDefinition.loadFromResource("tls/cert2.pfx", "password2");

  public void shutdown() {
    vertx.close();
  }

  @BeforeEach
  public void setup() {
    serverFactory = new TlsEnabledHttpServerFactory();
    this.vertx = Vertx.vertx();
  }

  @AfterEach
  public void cleanup() {
    serverFactory.shutdown();
    this.shutdown();
  }

  private Enclave createEnclave(
      final int httpServerPort, final Path workDir, final boolean tlsEnabled) throws IOException {

    final Path serverFingerprintFile = workDir.resolve("server_known_clients");
    final Path besuCertPasswordFile = workDir.resolve("password_file");
    try {
      populateFingerprintFile(serverFingerprintFile, httpServerCert, Optional.of(httpServerPort));
      Files.write(besuCertPasswordFile, besuCert.getPassword().getBytes(Charset.defaultCharset()));

      final EnclaveFactory factory = new EnclaveFactory(vertx);
      if (tlsEnabled) {
        final URI httpServerUri = new URI("https://localhost:" + httpServerPort);
        return factory.createVertxEnclave(
            httpServerUri,
            besuCert.getPkcs12File().toPath(),
            besuCertPasswordFile,
            serverFingerprintFile);
      } else {
        return factory.createVertxEnclave(new URI("http://localhost:" + httpServerPort));
      }
    } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException e) {
      fail("unable to populate fingerprint file");
      return null;
    } catch (URISyntaxException e) {
      fail("unable to create URI");
      return null;
    }
  }

  @Test
  public void nonTlsEnclaveCannotConnectToTlsServer() throws IOException {

    Path workDir = Files.createTempDirectory("test-certs");

    // Note: the HttpServer always responds with a JsonRpcSuccess, result="I'm up".
    final HttpServer httpServer = serverFactory.create(httpServerCert, besuCert, workDir, true);

    final Enclave enclave = createEnclave(httpServer.actualPort(), workDir, false);

    assertThat(enclave.upCheck()).isEqualTo(false);
  }

  @Test
  public void nonTlsEnclaveCanConnectToNonTlsServer() throws IOException {

    Path workDir = Files.createTempDirectory("test-certs");

    // Note: the HttpServer always responds with a JsonRpcSuccess, result="I'm up".
    final HttpServer httpServer = serverFactory.create(httpServerCert, besuCert, workDir, false);

    final Enclave enclave = createEnclave(httpServer.actualPort(), workDir, false);

    assertThat(enclave.upCheck()).isEqualTo(true);
  }

  @Test
  public void tlsEnclaveCannotConnectToNonTlsServer() throws IOException {

    Path workDir = Files.createTempDirectory("test-certs");

    // Note: the HttpServer always responds with a JsonRpcSuccess, result="I'm up!".
    final HttpServer httpServer = serverFactory.create(httpServerCert, besuCert, workDir, false);

    final Enclave enclave = createEnclave(httpServer.actualPort(), workDir, true);

    assertThat(enclave.upCheck()).isEqualTo(false);
  }

  @Test
  public void tlsEnclaveCanConnectToTlsServer() throws IOException {

    Path workDir = Files.createTempDirectory("test-certs");

    // Note: the HttpServer always responds with a JsonRpcSuccess, result="I'm up".
    final HttpServer httpServer = serverFactory.create(httpServerCert, besuCert, workDir, true);

    final Enclave enclave = createEnclave(httpServer.actualPort(), workDir, true);

    assertThat(enclave.upCheck()).isEqualTo(true);
  }
}
