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
import java.util.Optional;
import java.util.function.Supplier;

public class TlsConfiguration {
  private final Path keyStorePath;
  private final Supplier<String> keyStorePasswordSupplier;
  private final Optional<TlsClientAuthConfiguration> clientAuthConfiguration;

  private TlsConfiguration(
      final Path keyStorePath,
      final Supplier<String> keyStorePasswordSupplier,
      final Optional<TlsClientAuthConfiguration> clientAuthConfiguration) {
    this.keyStorePath = keyStorePath;
    this.keyStorePasswordSupplier = keyStorePasswordSupplier;
    this.clientAuthConfiguration = clientAuthConfiguration;
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

  public static final class Builder {
    private Path keyStorePath;
    private Supplier<String> keyStorePasswordSupplier;
    private TlsClientAuthConfiguration clientAuthConfiguration;

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

    public TlsConfiguration build() {
      requireNonNull(keyStorePath, "Key Store Path must not be null");
      requireNonNull(keyStorePasswordSupplier, "Key Store password supplier must not be null");
      return new TlsConfiguration(
          keyStorePath, keyStorePasswordSupplier, Optional.ofNullable(clientAuthConfiguration));
    }
  }
}
