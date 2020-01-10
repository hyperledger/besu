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

public class TlsConfiguration {
  private final Path keyStorePath;
  private final String keyStorePassword;
  private final Path knownClientsFile;

  private TlsConfiguration(
      final Path keyStorePath, final String keyStorePassword, final Path knownClientsFile) {
    requireNonNull(keyStorePath, "Key Store Path must not be null");
    requireNonNull(keyStorePassword, "Key Store password must not be null");
    this.keyStorePath = keyStorePath;
    this.keyStorePassword = keyStorePassword;
    this.knownClientsFile = knownClientsFile;
  }

  public Path getKeyStorePath() {
    return keyStorePath;
  }

  public String getKeyStorePassword() {
    return keyStorePassword;
  }

  public Optional<Path> getKnownClientsFile() {
    return Optional.ofNullable(knownClientsFile);
  }

  public static final class TlsConfigurationBuilder {
    private Path keyStorePath;
    private String keyStorePassword;
    private Path knownClientsFile;

    private TlsConfigurationBuilder() {}

    public static TlsConfigurationBuilder aTlsConfiguration() {
      return new TlsConfigurationBuilder();
    }

    public TlsConfigurationBuilder withKeyStorePath(final Path keyStorePath) {
      this.keyStorePath = keyStorePath;
      return this;
    }

    public TlsConfigurationBuilder withKeyStorePassword(final String keyStorePassword) {
      this.keyStorePassword = keyStorePassword;
      return this;
    }

    public TlsConfigurationBuilder withKnownClientsFile(final Path knownClientsFile) {
      this.knownClientsFile = knownClientsFile;
      return this;
    }

    public TlsConfiguration build() {
      return new TlsConfiguration(keyStorePath, keyStorePassword, knownClientsFile);
    }
  }
}
