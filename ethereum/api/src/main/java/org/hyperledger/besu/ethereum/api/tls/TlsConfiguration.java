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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

public class TlsConfiguration {

  public static final List<String> DEFAULT_TLS_PROTOCOLS = Arrays.asList("TLSv1.2");
  public static final List<String> JDK_ENABLED_CIPHERSUITES;
  public static final List<String> JDK_ENABLED_PROTOCOLS;

  private final Path keyStorePath;
  private final Supplier<String> keyStorePasswordSupplier;
  private final Optional<TlsClientAuthConfiguration> clientAuthConfiguration;
  private final Optional<Set<String>> secureTransportProtocols;
  private final Optional<Set<String>> cipherSuites;

  /* “Adapted from: eclipse-vertx/vert.x DefaultJDKCipherSuite.java” */
  static {
    ArrayList<String> protocols = new ArrayList<>();
    ArrayList<String> ciphersuites = new ArrayList<>();
    try {
      SSLContext context = SSLContext.getInstance("TLS");
      context.init(null, null, null);
      SSLEngine engine = context.createSSLEngine();
      Collections.addAll(ciphersuites, engine.getEnabledCipherSuites());
      Collections.addAll(protocols, engine.getEnabledProtocols());
    } catch (Throwable e) {
      ciphersuites = null;
      protocols = null;
    }

    JDK_ENABLED_CIPHERSUITES =
        ciphersuites != null ? Collections.unmodifiableList(ciphersuites) : null;
    JDK_ENABLED_PROTOCOLS = protocols != null ? Collections.unmodifiableList(protocols) : null;
  }

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

  public Path getKeyStorePath() {
    return keyStorePath;
  }

  public String getKeyStorePassword() {
    return keyStorePasswordSupplier.get();
  }

  public Optional<TlsClientAuthConfiguration> getClientAuthConfiguration() {
    return clientAuthConfiguration;
  }

  public Optional<Set<String>> getSecureTransportProtocols() {
    return secureTransportProtocols;
  }

  public Optional<Set<String>> getCipherSuites() {
    return cipherSuites;
  }

  public static final class Builder {
    private Path keyStorePath;
    private Supplier<String> keyStorePasswordSupplier;
    private TlsClientAuthConfiguration clientAuthConfiguration;
    private Set<String> secureTransportProtocols;
    private Set<String> cipherSuites;

    private Builder() {}

    public static Builder aTlsConfiguration() {
      return new Builder();
    }

    public Builder withKeyStorePath(final Path keyStorePath) {
      this.keyStorePath = keyStorePath;
      return this;
    }

    public Builder withKeyStorePasswordSupplier(final Supplier<String> keyStorePasswordSupplier) {
      this.keyStorePasswordSupplier = keyStorePasswordSupplier;
      return this;
    }

    public Builder withClientAuthConfiguration(
        final TlsClientAuthConfiguration clientAuthConfiguration) {
      this.clientAuthConfiguration = clientAuthConfiguration;
      return this;
    }

    public Builder withSecureTransportProtocols(final List<String> secureTransportProtocols) {
      this.secureTransportProtocols = new HashSet<String>(secureTransportProtocols);
      return this;
    }

    public Builder withCipherSuites(final List<String> cipherSuites) {
      this.cipherSuites = new HashSet<String>(cipherSuites);
      return this;
    }

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
