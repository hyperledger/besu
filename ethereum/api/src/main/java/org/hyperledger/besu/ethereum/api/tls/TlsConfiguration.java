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
package org.hyperledger.besu.ethereum.api.tls;

import static java.util.Objects.requireNonNull;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/** The type Tls configuration. */
public class TlsConfiguration {

  private final Path keyStorePath;
  private final Supplier<String> keyStorePasswordSupplier;
  private final Optional<TlsClientAuthConfiguration> clientAuthConfiguration;
  private final Optional<Set<String>> secureTransportProtocols;
  private final Optional<Set<String>> cipherSuites;

  private TlsConfiguration(
      final Path keyStorePath,
      final Supplier<String> keyStorePasswordSupplier,
      final Optional<TlsClientAuthConfiguration> clientAuthConfiguration,
      final Optional<Set<String>> secureTransportProtocols,
      final Optional<Set<String>> cipherSuites) {
    this.keyStorePath = keyStorePath;
    this.keyStorePasswordSupplier = keyStorePasswordSupplier;
    this.clientAuthConfiguration = clientAuthConfiguration;
    this.secureTransportProtocols = secureTransportProtocols;
    this.cipherSuites = cipherSuites;
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
   * Gets key store password.
   *
   * @return the key store password
   */
  public String getKeyStorePassword() {
    return keyStorePasswordSupplier.get();
  }

  /**
   * Gets client auth configuration.
   *
   * @return the client auth configuration
   */
  public Optional<TlsClientAuthConfiguration> getClientAuthConfiguration() {
    return clientAuthConfiguration;
  }

  /**
   * Gets secure transport protocols.
   *
   * @return the secure transport protocols
   */
  public Optional<Set<String>> getSecureTransportProtocols() {
    return secureTransportProtocols;
  }

  /**
   * Gets cipher suites.
   *
   * @return the cipher suites
   */
  public Optional<Set<String>> getCipherSuites() {
    return cipherSuites;
  }

  /** The type Builder. */
  public static final class Builder {
    private Path keyStorePath;
    private Supplier<String> keyStorePasswordSupplier;
    private TlsClientAuthConfiguration clientAuthConfiguration;
    private Set<String> secureTransportProtocols;
    private Set<String> cipherSuites;

    private Builder() {}

    /**
     * A tls configuration builder.
     *
     * @return the builder
     */
    public static Builder aTlsConfiguration() {
      return new Builder();
    }

    /**
     * With key store path builder.
     *
     * @param keyStorePath the key store path
     * @return the builder
     */
    public Builder withKeyStorePath(final Path keyStorePath) {
      this.keyStorePath = keyStorePath;
      return this;
    }

    /**
     * With key store password supplier builder.
     *
     * @param keyStorePasswordSupplier the key store password supplier
     * @return the builder
     */
    public Builder withKeyStorePasswordSupplier(final Supplier<String> keyStorePasswordSupplier) {
      this.keyStorePasswordSupplier = keyStorePasswordSupplier;
      return this;
    }

    /**
     * With client auth configuration builder.
     *
     * @param clientAuthConfiguration the client auth configuration
     * @return the builder
     */
    public Builder withClientAuthConfiguration(
        final TlsClientAuthConfiguration clientAuthConfiguration) {
      this.clientAuthConfiguration = clientAuthConfiguration;
      return this;
    }

    /**
     * With secure transport protocols builder.
     *
     * @param secureTransportProtocols the secure transport protocols
     * @return the builder
     */
    public Builder withSecureTransportProtocols(final List<String> secureTransportProtocols) {
      this.secureTransportProtocols = new HashSet<>(secureTransportProtocols);
      return this;
    }

    /**
     * With cipher suites builder.
     *
     * @param cipherSuites the cipher suites
     * @return the builder
     */
    public Builder withCipherSuites(final List<String> cipherSuites) {
      this.cipherSuites = new HashSet<>(cipherSuites);
      return this;
    }

    /**
     * Build tls configuration.
     *
     * @return the tls configuration
     */
    public TlsConfiguration build() {
      requireNonNull(keyStorePath, "Key Store Path must not be null");
      requireNonNull(keyStorePasswordSupplier, "Key Store password supplier must not be null");
      return new TlsConfiguration(
          keyStorePath,
          keyStorePasswordSupplier,
          Optional.ofNullable(clientAuthConfiguration),
          Optional.ofNullable(secureTransportProtocols),
          Optional.ofNullable(cipherSuites));
    }
  }
}
