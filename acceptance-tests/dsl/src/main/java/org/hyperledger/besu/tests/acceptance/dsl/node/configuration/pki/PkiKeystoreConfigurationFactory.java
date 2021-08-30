/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 *  the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.besu.tests.acceptance.dsl.node.configuration.pki;

import static org.hyperledger.besu.pki.util.TestCertificateUtils.createKeyPair;
import static org.hyperledger.besu.pki.util.TestCertificateUtils.createSelfSignedCertificate;
import static org.hyperledger.besu.pki.util.TestCertificateUtils.issueCertificate;

import org.hyperledger.besu.pki.config.PkiKeyStoreConfiguration;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public class PkiKeystoreConfigurationFactory {

  public static final String KEYSTORE_DEFAULT_TYPE = "PKCS12";
  public static final String KEYSTORE_DEFAULT_PASSWORD = "password";
  public static final String KEYSTORE_DEFAULT_CERT_ALIAS = "validator";

  private KeyPair caKeyPair;
  private X509Certificate caCertificate;
  private Path trustStoreFile;
  private Path passwordFile;

  public PkiKeyStoreConfiguration createPkiConfig() {
    PkiKeyStoreConfiguration.Builder pkiKeyStoreConfigBuilder =
        new PkiKeyStoreConfiguration.Builder();

    pkiKeyStoreConfigBuilder.withTrustStoreType(KEYSTORE_DEFAULT_TYPE);
    pkiKeyStoreConfigBuilder.withTrustStorePath(createTrustStore());
    pkiKeyStoreConfigBuilder.withTrustStorePasswordPath(passwordFile);

    pkiKeyStoreConfigBuilder.withKeyStoreType(KEYSTORE_DEFAULT_TYPE);
    pkiKeyStoreConfigBuilder.withKeyStorePath(createKeyStore());
    pkiKeyStoreConfigBuilder.withKeyStorePasswordPath(passwordFile);

    pkiKeyStoreConfigBuilder.withCertificateAlias(KEYSTORE_DEFAULT_CERT_ALIAS);

    return pkiKeyStoreConfigBuilder.build();
  }

  private Path createTrustStore() {
    // Only create the truststore if this is the first time this method is being called
    if (caKeyPair == null) {
      try {
        caKeyPair = createKeyPair();
        caCertificate = createSelfSignedCertificate("ca", notBefore(), notAfter(), caKeyPair);

        final KeyStore truststore = KeyStore.getInstance(KEYSTORE_DEFAULT_TYPE);
        truststore.load(null, null);
        truststore.setCertificateEntry("ca", caCertificate);

        final String uniqueId = UUID.randomUUID().toString();
        trustStoreFile = writeKeyStoreFile(truststore, "truststore", uniqueId);
        passwordFile = writePasswordFile(KEYSTORE_DEFAULT_PASSWORD, "password", uniqueId);
      } catch (final Exception e) {
        throw new RuntimeException("Error creating truststore for Acceptance Test", e);
      }
    }

    return trustStoreFile;
  }

  private Path createKeyStore() {
    if (caKeyPair == null) {
      createTrustStore();
    }

    final KeyPair kp = createKeyPair();
    final X509Certificate certificate =
        issueCertificate(caCertificate, caKeyPair, "validator", notBefore(), notAfter(), kp, false);

    try {
      final KeyStore keyStore = KeyStore.getInstance(KEYSTORE_DEFAULT_TYPE);
      keyStore.load(null, null);
      keyStore.setKeyEntry(
          "validator",
          kp.getPrivate(),
          KEYSTORE_DEFAULT_PASSWORD.toCharArray(),
          new Certificate[] {certificate, caCertificate});

      final String id = UUID.randomUUID().toString();
      return writeKeyStoreFile(keyStore, "keystore", id);
    } catch (final Exception e) {
      throw new RuntimeException("Error creating keystore for Acceptance Test", e);
    }
  }

  private Path writeKeyStoreFile(
      final KeyStore keyStore, final String prefix, final String suffix) {
    try {
      final Path file = Files.createTempFile(prefix, suffix != null ? suffix : "");
      file.toFile().deleteOnExit();
      final FileOutputStream keyStoreFOS = new FileOutputStream(file.toFile());
      keyStore.store(keyStoreFOS, KEYSTORE_DEFAULT_PASSWORD.toCharArray());

      return file;
    } catch (final Exception e) {
      throw new RuntimeException("Error creating keystore file", e);
    }
  }

  private Path writePasswordFile(final String password, final String prefix, final String suffix) {
    try {
      final Path file = Files.createTempFile(prefix, suffix);
      file.toFile().deleteOnExit();
      Files.write(file, password.getBytes(StandardCharsets.UTF_8));
      return file;
    } catch (final IOException e) {
      throw new RuntimeException("Error creating password file", e);
    }
  }

  private Instant notBefore() {
    return Instant.now().minus(1, ChronoUnit.DAYS);
  }

  private Instant notAfter() {
    return Instant.now().plus(10, ChronoUnit.DAYS);
  }
}
