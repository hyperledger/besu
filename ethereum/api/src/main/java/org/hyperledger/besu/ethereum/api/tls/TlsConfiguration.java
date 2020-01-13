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

import com.google.common.annotations.VisibleForTesting;

public class TlsConfiguration {
  private final Path keyStorePath;
  private final Supplier<String> keyStorePasswordSupplier;
  private final Path knownClientsFile;

  private TlsConfiguration(
      final Path keyStorePath,
      final Supplier<String> keyStorePasswordSupplier,
      final Path knownClientsFile) {
    requireNonNull(keyStorePath, "Key Store Path must not be null");
    requireNonNull(keyStorePasswordSupplier, "Key Store password supplier must not be null");
    this.keyStorePath = keyStorePath;
    this.keyStorePasswordSupplier = keyStorePasswordSupplier;
    this.knownClientsFile = knownClientsFile;
  }

  @VisibleForTesting
  public static TlsConfiguration fromKeyStoreConfigurations(
      final Path keyStorePath, final Supplier<String> keyStorePasswordSupplier) {
    return new TlsConfiguration(keyStorePath, keyStorePasswordSupplier, null);
  }

  public static TlsConfiguration fromKeyStoreAndKnownClientConfigurations(
      final Path keyStorePath,
      final Supplier<String> keyStorePasswordSupplier,
      final Path knownClientsFile) {
    return new TlsConfiguration(keyStorePath, keyStorePasswordSupplier, knownClientsFile);
  }

  public Path getKeyStorePath() {
    return keyStorePath;
  }

  public String getKeyStorePassword() {
    return keyStorePasswordSupplier.get();
  }

  public Optional<Path> getKnownClientsFile() {
    return Optional.ofNullable(knownClientsFile);
  }
}
