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

/** The Pki key store configuration. */
public class PkiKeyStoreConfiguration {

  /** The constant DEFAULT_KEYSTORE_TYPE. */
  public static String DEFAULT_KEYSTORE_TYPE = "PKCS12";
  /** The constant DEFAULT_CERTIFICATE_ALIAS. */
  public static String DEFAULT_CERTIFICATE_ALIAS = "validator";

  private final String keyStoreType;
  private final Path keyStorePath;
  private final Path keyStorePasswordPath;
  private final String certificateAlias;
  private final String trustStoreType;
  private final Path trustStorePath;
  private final Path trustStorePasswordPath;
  private final Optional<Path> crlFilePath;

  /**
   * Instantiates a new Pki key store configuration.
   *
   * @param keyStoreType the key store type
   * @param keyStorePath the key store path
   * @param keyStorePasswordPath the key store password path
   * @param certificateAlias the certificate alias
   * @param trustStoreType the trust store type
   * @param trustStorePath the trust store path
   * @param trustStorePasswordPath the trust store password path
   * @param crlFilePath the crl file path
   */
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

  /**
   * Gets key store type.
   *
   * @return the key store type
   */
  public String getKeyStoreType() {
    return keyStoreType;
  }

  /**
   * Gets key store path.
   *
   * @return the key store path
   */
  public Path getKeyStorePath() {
    return keyStorePath;
  }

  /**
   * Gets key store password path.
   *
   * @return the key store password path
   */
  @VisibleForTesting
  public Path getKeyStorePasswordPath() {
    return keyStorePasswordPath;
  }

  /**
   * Gets key store password.
   *
   * @return the key store password
   */
  public String getKeyStorePassword() {
    return readPasswordFromFile(keyStorePasswordPath);
  }

  /**
   * Gets certificate alias.
   *
   * @return the certificate alias
   */
  public String getCertificateAlias() {
    return certificateAlias;
  }

  /**
   * Gets trust store type.
   *
   * @return the trust store type
   */
  public String getTrustStoreType() {
    return trustStoreType;
  }

  /**
   * Gets trust store path.
   *
   * @return the trust store path
   */
  public Path getTrustStorePath() {
    return trustStorePath;
  }

  /**
   * Gets trust store password path.
   *
   * @return the trust store password path
   */
  @VisibleForTesting
  public Path getTrustStorePasswordPath() {
    return trustStorePasswordPath;
  }

  /**
   * Gets trust store password.
   *
   * @return the trust store password
   */
  public String getTrustStorePassword() {
    return readPasswordFromFile(trustStorePasswordPath);
  }

  /**
   * Gets CRL file path.
   *
   * @return the crl file path
   */
  public Optional<Path> getCrlFilePath() {
    return crlFilePath;
  }

  /** The Builder. */
  public static final class Builder {

    private String keyStoreType = DEFAULT_KEYSTORE_TYPE;
    private Path keyStorePath;
    private Path keyStorePasswordPath;
    private String certificateAlias = DEFAULT_CERTIFICATE_ALIAS;
    private String trustStoreType = DEFAULT_KEYSTORE_TYPE;
    private Path trustStorePath;
    private Path trustStorePasswordPath;
    private Path crlFilePath;

    /** Instantiates a new Builder. */
    public Builder() {}

    /**
     * With key store type.
     *
     * @param keyStoreType the key store type
     * @return the builder
     */
    public Builder withKeyStoreType(final String keyStoreType) {
      this.keyStoreType = keyStoreType;
      return this;
    }

    /**
     * With key store path.
     *
     * @param keyStorePath the key store path
     * @return the builder
     */
    public Builder withKeyStorePath(final Path keyStorePath) {
      this.keyStorePath = keyStorePath;
      return this;
    }

    /**
     * With key store password path.
     *
     * @param keyStorePasswordPath the key store password path
     * @return the builder
     */
    public Builder withKeyStorePasswordPath(final Path keyStorePasswordPath) {
      this.keyStorePasswordPath = keyStorePasswordPath;
      return this;
    }

    /**
     * With certificate alias.
     *
     * @param certificateAlias the certificate alias
     * @return the builder
     */
    public Builder withCertificateAlias(final String certificateAlias) {
      this.certificateAlias = certificateAlias;
      return this;
    }

    /**
     * With trust store type.
     *
     * @param trustStoreType the trust store type
     * @return the builder
     */
    public Builder withTrustStoreType(final String trustStoreType) {
      this.trustStoreType = trustStoreType;
      return this;
    }

    /**
     * With trust store path.
     *
     * @param trustStorePath the trust store path
     * @return the builder
     */
    public Builder withTrustStorePath(final Path trustStorePath) {
      this.trustStorePath = trustStorePath;
      return this;
    }

    /**
     * With trust store password path.
     *
     * @param trustStorePasswordPath the trust store password path
     * @return the builder
     */
    public Builder withTrustStorePasswordPath(final Path trustStorePasswordPath) {
      this.trustStorePasswordPath = trustStorePasswordPath;
      return this;
    }

    /**
     * With crl file path.
     *
     * @param filePath the file path
     * @return the builder
     */
    public Builder withCrlFilePath(final Path filePath) {
      this.crlFilePath = filePath;
      return this;
    }

    /**
     * Build pki key store configuration.
     *
     * @return the pki key store configuration
     */
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
