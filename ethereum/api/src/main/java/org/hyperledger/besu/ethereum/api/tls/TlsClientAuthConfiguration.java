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
import java.util.Objects;
import java.util.Optional;

public class TlsClientAuthConfiguration {
  private final Optional<Path> knownClientsFile;
  private final boolean caClientsEnabled;

  private TlsClientAuthConfiguration(
      final Optional<Path> knownClientsFile, final boolean caClientsEnabled) {
    this.knownClientsFile = knownClientsFile;
    this.caClientsEnabled = caClientsEnabled;
  }

  public Optional<Path> getKnownClientsFile() {
    return knownClientsFile;
  }

  public boolean isCaClientsEnabled() {
    return caClientsEnabled;
  }

  public static final class Builder {
    private Path knownClientsFile;
    private boolean caClientsEnabled;

    private Builder() {}

    public static Builder aTlsClientAuthConfiguration() {
      return new Builder();
    }

    public Builder withKnownClientsFile(final Path knownClientsFile) {
      this.knownClientsFile = knownClientsFile;
      return this;
    }

    public Builder withCaClientsEnabled(final boolean caClientsEnabled) {
      this.caClientsEnabled = caClientsEnabled;
      return this;
    }

    public TlsClientAuthConfiguration build() {
      if (!caClientsEnabled) {
        Objects.requireNonNull(knownClientsFile, "Known Clients File is required");
      }
      return new TlsClientAuthConfiguration(
          Optional.ofNullable(knownClientsFile), caClientsEnabled);
    }
  }
}
