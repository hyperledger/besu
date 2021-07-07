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

package org.hyperledger.besu.ethereum.p2p.rlpx.connections.netty;

import static java.util.Objects.requireNonNull;

import java.nio.file.Path;
import java.util.function.Supplier;

public class TLSConfiguration {

  private final String keyStoreType;
  private final Path keyStorePath;
  private final Supplier<String> keyStorePasswordSupplier;
  private final Path keyStorePasswordPath;
  private final String trustStoreType;
  private final Path trustStorePath;
  private final Supplier<String> trustStorePasswordSupplier;
  private final Path trustStorePasswordPath;
  private final Path crlPath;
  private final String[] allowedProtocols;

  private TLSConfiguration(
      final String keyStoreType,
      final Path keyStorePath,
      final Supplier<String> keyStorePasswordSupplier,
      final Path keyStorePasswordPath,
      final String trustStoreType,
      final Path trustStorePath,
      final Supplier<String> trustStorePasswordSupplier,
      final Path trustStorePasswordPath,
      final Path crlPath,
      final String[] allowedProtocols) {
    this.keyStoreType = keyStoreType;
    this.keyStorePath = keyStorePath;
    this.keyStorePasswordSupplier = keyStorePasswordSupplier;
    this.keyStorePasswordPath = keyStorePasswordPath;
    this.trustStoreType = trustStoreType;
    this.trustStorePath = trustStorePath;
    this.trustStorePasswordSupplier = trustStorePasswordSupplier;
    this.trustStorePasswordPath = trustStorePasswordPath;
    this.crlPath = crlPath;
    this.allowedProtocols = allowedProtocols;
  }

  public String getKeyStoreType() {
    return keyStoreType;
  }

  public Path getKeyStorePath() {
    return keyStorePath;
  }

  public String getKeyStorePassword() {
    return null == keyStorePasswordSupplier ? null : keyStorePasswordSupplier.get();
  }

  public Path getKeyStorePasswordPath() {
    return keyStorePasswordPath;
  }

  public String getTrustStoreType() {
    return trustStoreType;
  }

  public Path getTrustStorePath() {
    return trustStorePath;
  }

  public String getTrustStorePassword() {
    return null == trustStorePasswordSupplier ? null : trustStorePasswordSupplier.get();
  }

  public Path getTrustStorePasswordPath() {
    return trustStorePasswordPath;
  }

  public Path getCrlPath() {
    return crlPath;
  }

  public String[] getAllowedProtocols() {
    return allowedProtocols;
  }

  public static final class Builder {
    private String keyStoreType;
    private Path keyStorePath;
    private Supplier<String> keyStorePasswordSupplier;
    private Path keyStorePasswordPath;
    private String trustStoreType;
    private Path trustStorePath;
    private Supplier<String> trustStorePasswordSupplier;
    private Path trustStorePasswordPath;
    private Path crlPath;
    private String[] allowedProtocols;

    private Builder() {}

    public static Builder tlsConfiguration() {
      return new Builder();
    }

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

    public Builder withKeyStorePasswordSupplier(final Supplier<String> keyStorePasswordSupplier) {
      this.keyStorePasswordSupplier = keyStorePasswordSupplier;
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

    public Builder withTrustStorePasswordSupplier(
        final Supplier<String> trustStorePasswordSupplier) {
      this.trustStorePasswordSupplier = trustStorePasswordSupplier;
      return this;
    }

    public Builder withTrustStorePasswordPath(final Path trustStorePasswordPath) {
      this.trustStorePasswordPath = trustStorePasswordPath;
      return this;
    }

    public Builder withCrlPath(final Path crlPath) {
      this.crlPath = crlPath;
      return this;
    }

    public Builder withAllowedProtocols(final String[] allowedProtocols) {
      this.allowedProtocols = allowedProtocols;
      return this;
    }

    public TLSConfiguration build() {
      requireNonNull(keyStoreType, "Key Store Type must not be null");
      requireNonNull(keyStorePasswordSupplier, "Key Store password supplier must not be null");
      return new TLSConfiguration(
          keyStoreType,
          keyStorePath,
          keyStorePasswordSupplier,
          keyStorePasswordPath,
          trustStoreType,
          trustStorePath,
          trustStorePasswordSupplier,
          trustStorePasswordPath,
          crlPath,
          allowedProtocols);
    }
  }
}
