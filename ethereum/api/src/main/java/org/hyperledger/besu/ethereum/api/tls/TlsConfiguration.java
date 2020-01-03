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

import java.nio.file.Path;
import java.util.Optional;

import io.vertx.core.net.PfxOptions;
import io.vertx.core.net.TrustOptions;
import org.apache.tuweni.net.tls.VertxTrustOptions;

public class TlsConfiguration {
  private final Path keyStorePath;
  private final String keyStorePassword;
  private final Path knownClientsFile;
  private Optional<TrustOptions> trustOptions;
  private PfxOptions pfxKeyCertOptions;

  private TlsConfiguration(
      final Path keyStorePath, final String keyStorePassword, final Path knownClientsFile) {
    this.keyStorePath = keyStorePath;
    this.keyStorePassword = keyStorePassword;
    this.knownClientsFile = knownClientsFile;
  }

  private void init() {
    this.pfxKeyCertOptions =
        new PfxOptions().setPath(keyStorePath.toString()).setPassword(keyStorePassword);
    this.trustOptions =
        Optional.ofNullable(knownClientsFile).map(VertxTrustOptions::whitelistClients);
  }

  public PfxOptions getPfxKeyCertOptions() {
    return pfxKeyCertOptions;
  }

  public Optional<TrustOptions> getTrustOptions() {
    return trustOptions;
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
      final TlsConfiguration tlsConfiguration =
          new TlsConfiguration(keyStorePath, keyStorePassword, knownClientsFile);
      tlsConfiguration.init();
      return tlsConfiguration;
    }
  }
}
