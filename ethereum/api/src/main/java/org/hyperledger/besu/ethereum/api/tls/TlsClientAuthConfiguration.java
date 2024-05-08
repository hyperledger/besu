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

/** The type Tls client auth configuration. */
public class TlsClientAuthConfiguration {
  private final Optional<Path> knownClientsFile;
  private final boolean caClientsEnabled;

  private TlsClientAuthConfiguration(
      final Optional<Path> knownClientsFile, final boolean caClientsEnabled) {
    this.knownClientsFile = knownClientsFile;
    this.caClientsEnabled = caClientsEnabled;
  }

  /**
   * Gets known clients file.
   *
   * @return the known clients file
   */
  public Optional<Path> getKnownClientsFile() {
    return knownClientsFile;
  }

  /**
   * Is ca clients enabled boolean.
   *
   * @return the boolean
   */
  public boolean isCaClientsEnabled() {
    return caClientsEnabled;
  }

  /** The type Builder. */
  public static final class Builder {
    private Path knownClientsFile;
    private boolean caClientsEnabled;

    private Builder() {}

    /**
     * A tls client auth configuration builder.
     *
     * @return the builder
     */
    public static Builder aTlsClientAuthConfiguration() {
      return new Builder();
    }

    /**
     * With known clients file builder.
     *
     * @param knownClientsFile the known clients file
     * @return the builder
     */
    public Builder withKnownClientsFile(final Path knownClientsFile) {
      this.knownClientsFile = knownClientsFile;
      return this;
    }

    /**
     * With ca clients enabled builder.
     *
     * @param caClientsEnabled the ca clients enabled
     * @return the builder
     */
    public Builder withCaClientsEnabled(final boolean caClientsEnabled) {
      this.caClientsEnabled = caClientsEnabled;
      return this;
    }

    /**
     * Build tls client auth configuration.
     *
     * @return the tls client auth configuration
     */
    public TlsClientAuthConfiguration build() {
      if (!caClientsEnabled) {
        Objects.requireNonNull(knownClientsFile, "Known Clients File is required");
      }
      return new TlsClientAuthConfiguration(
          Optional.ofNullable(knownClientsFile), caClientsEnabled);
    }
  }
}
