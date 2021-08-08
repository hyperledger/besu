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

package org.hyperledger.besu.consensus.qbft.pki;

import org.hyperledger.besu.pki.config.PkiKeyStoreConfiguration;
import org.hyperledger.besu.pki.keystore.KeyStoreWrapper;
import org.hyperledger.besu.pki.keystore.SoftwareKeyStoreWrapper;

import java.nio.file.Path;
import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PkiBlockCreationConfigurationProvider {

  private static final Logger LOG = LogManager.getLogger();

  private static PkiBlockCreationConfiguration pkiBlockCreationConfig;

  private final KeyStoreWrapperProvider keyStoreWrapperProvider;

  public PkiBlockCreationConfigurationProvider() {
    this(SoftwareKeyStoreWrapper::new);
  }

  @VisibleForTesting
  PkiBlockCreationConfigurationProvider(final KeyStoreWrapperProvider keyStoreWrapperProvider) {
    this.keyStoreWrapperProvider = keyStoreWrapperProvider;
  }

  /*
   This method is only called once during the application startup.
  */
  public void load(final PkiKeyStoreConfiguration configuration) {
    KeyStoreWrapper keyStore;
    try {
      keyStore =
          keyStoreWrapperProvider.apply(
              configuration.getKeyStoreType(),
              configuration.getKeyStorePath(),
              configuration.getKeyStorePassword(),
              null);
      LOG.info("Loaded PKI Block Creation KeyStore {}", configuration.getKeyStorePath());
    } catch (Exception e) {
      final String message = "Error loading PKI Block Creation KeyStore";
      LOG.error(message, e);
      throw new RuntimeException(message, e);
    }

    KeyStoreWrapper trustStore;
    try {
      trustStore =
          keyStoreWrapperProvider.apply(
              configuration.getTrustStoreType(),
              configuration.getTrustStorePath(),
              configuration.getTrustStorePassword(),
              configuration.getCrlFilePath().orElse(null));
      LOG.info("Loaded PKI Block Creation TrustStore {}", configuration.getTrustStorePath());
    } catch (Exception e) {
      final String message = "Error loading PKI Block Creation TrustStore";
      LOG.error(message, e);
      throw new RuntimeException(message, e);
    }

    pkiBlockCreationConfig =
        new PkiBlockCreationConfiguration(
            keyStore, trustStore, configuration.getCertificateAlias());
  }

  public static Optional<PkiBlockCreationConfiguration> getIfLoaded() {
    return Optional.ofNullable(pkiBlockCreationConfig);
  }

  @FunctionalInterface
  interface KeyStoreWrapperProvider {

    KeyStoreWrapper apply(
        final String keyStoreType,
        final Path keyStorePath,
        final String keyStorePassword,
        final Path crl);
  }
}
