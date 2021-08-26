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
package org.hyperledger.besu.pki.config;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

import com.google.common.annotations.VisibleForTesting;

public class PkiKeyStoreConfiguration {

  public static String DEFAULT_KEYSTORE_TYPE = "PKCS12";
  public static String DEFAULT_CERTIFICATE_ALIAS = "validator";

  private final String keyStoreType;
  private final Path keyStorePath;
  private final Path keyStorePasswordPath;
  private final String certificateAlias;
  private final String trustStoreType;
  private final Path trustStorePath;
  private final Path trustStorePasswordPath;
  private final Optional<Path> crlFilePath;

  public PkiKeyStoreConfiguration(
      final String keyStoreType,
      final Path keyStorePath,
      final Path keyStorePasswordPath,
      final String certificateAlias,
      final String trustStoreType,
      final Path trustStorePath,
      final Path trustStorePasswordPath,
      final Optional<Path> crlFilePath) {
    this.keyStoreType = keyStoreType;
    this.keyStorePath = keyStorePath;
    this.keyStorePasswordPath = keyStorePasswordPath;
    this.certificateAlias = certificateAlias;
    this.trustStoreType = trustStoreType;
    this.trustStorePath = trustStorePath;
    this.trustStorePasswordPath = trustStorePasswordPath;
    this.crlFilePath = crlFilePath;
  }

  public String getKeyStoreType() {
    return keyStoreType;
  }

  public Path getKeyStorePath() {
    return keyStorePath;
  }

  @VisibleForTesting
  public Path getKeyStorePasswordPath() {
    return keyStorePasswordPath;
  }

  public String getKeyStorePassword() {
    return readPasswordFromFile(keyStorePasswordPath);
  }

  public String getCertificateAlias() {
    return certificateAlias;
  }

  public String getTrustStoreType() {
    return trustStoreType;
  }

  public Path getTrustStorePath() {
    return trustStorePath;
  }

  @VisibleForTesting
  public Path getTrustStorePasswordPath() {
    return trustStorePasswordPath;
  }

  public String getTrustStorePassword() {
    return readPasswordFromFile(trustStorePasswordPath);
  }

  public Optional<Path> getCrlFilePath() {
    return crlFilePath;
  }

  public static final class Builder {

    private String keyStoreType = DEFAULT_KEYSTORE_TYPE;
    private Path keyStorePath;
    private Path keyStorePasswordPath;
    private String certificateAlias = DEFAULT_CERTIFICATE_ALIAS;
    private String trustStoreType = DEFAULT_KEYSTORE_TYPE;
    private Path trustStorePath;
    private Path trustStorePasswordPath;
    private Path crlFilePath;

    public Builder() {}

    public Builder withKeyStoreType(final String keyStoreType) {
      this.keyStoreType = keyStoreType;
      return this;
    }

    public Builder withKeyStorePath(final Path keyStorePath) {
      this.keyStorePath = keyStorePath;
      return this;
    }

    public Builder withKeyStorePasswordPath(final Path keyStorePasswordPath) {
      this.keyStorePasswordPath = keyStorePasswordPath;
      return this;
    }

    public Builder withCertificateAlias(final String certificateAlias) {
      this.certificateAlias = certificateAlias;
      return this;
    }

    public Builder withTrustStoreType(final String trustStoreType) {
      this.trustStoreType = trustStoreType;
      return this;
    }

    public Builder withTrustStorePath(final Path trustStorePath) {
      this.trustStorePath = trustStorePath;
      return this;
    }

    public Builder withTrustStorePasswordPath(final Path trustStorePasswordPath) {
      this.trustStorePasswordPath = trustStorePasswordPath;
      return this;
    }

    public Builder withCrlFilePath(final Path filePath) {
      this.crlFilePath = filePath;
      return this;
    }

    public PkiKeyStoreConfiguration build() {
      requireNonNull(keyStoreType, "Key Store Type must not be null");
      requireNonNull(keyStorePasswordPath, "Key Store password file must not be null");

      return new PkiKeyStoreConfiguration(
          keyStoreType,
          keyStorePath,
          keyStorePasswordPath,
          certificateAlias,
          trustStoreType,
          trustStorePath,
          trustStorePasswordPath,
          Optional.ofNullable(crlFilePath));
    }
  }

  private String readPasswordFromFile(final Path passwordFile) {
    try (final Stream<String> fileStream = Files.lines(passwordFile)) {
      return fileStream.findFirst().orElseThrow(() -> errorReadingFileException(passwordFile));
    } catch (final IOException e) {
      throw errorReadingFileException(passwordFile);
    }
  }

  private RuntimeException errorReadingFileException(final Path path) {
    return new RuntimeException(String.format("Unable to read keystore password from %s", path));
  }
}
