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
package org.hyperledger.besu.cli.config;

import java.math.BigInteger;
import java.util.Locale;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

/** The enum Network name. */
public enum NetworkName {
  /** Mainnet network name. */
  MAINNET("/mainnet.json", BigInteger.valueOf(1)),
  /** Sepolia network name. */
  SEPOLIA("/sepolia.json", BigInteger.valueOf(11155111)),
  /** Holešky network name. */
  HOLESKY("/holesky.json", BigInteger.valueOf(17000)),
  /** LUKSO mainnet network name. */
  LUKSO("/lukso.json", BigInteger.valueOf(42)),

  /**
   * EPHEMERY network name. The actual networkId used is calculated based on this default value and
   * the current time. https://ephemery.dev/
   */
  EPHEMERY("/ephemery.json", BigInteger.valueOf(39438135)),

  /** Dev network name. */
  DEV("/dev.json", BigInteger.valueOf(2018), false),
  /** Future EIPs network name. */
  FUTURE_EIPS("/future.json", BigInteger.valueOf(2022), false),
  /** Experimental EIPs network name. */
  EXPERIMENTAL_EIPS("/experimental.json", BigInteger.valueOf(2023), false),
  /** Classic network name. */
  CLASSIC("/classic.json", BigInteger.valueOf(1)),
  /** Mordor network name. */
  MORDOR("/mordor.json", BigInteger.valueOf(7));

  private final String genesisFile;
  private final BigInteger networkId;
  private final boolean canSnapSync;
  private final String deprecationDate;

  NetworkName(final String genesisFile, final BigInteger networkId) {
    this(genesisFile, networkId, true);
  }

  NetworkName(final String genesisFile, final BigInteger networkId, final boolean canSnapSync) {
    this.genesisFile = genesisFile;
    this.networkId = networkId;
    this.canSnapSync = canSnapSync;
    // no deprecations planned
    this.deprecationDate = null;
  }

  /**
   * Gets genesis file.
   *
   * @return the genesis file
   */
  public String getGenesisFile() {
    return genesisFile;
  }

  /**
   * Gets network id.
   *
   * @return the network id
   */
  public BigInteger getNetworkId() {
    return networkId;
  }

  /**
   * Can SNAP sync boolean.
   *
   * @return the boolean
   */
  public boolean canSnapSync() {
    return canSnapSync;
  }

  /**
   * Normalize string.
   *
   * @return the string
   */
  public String normalize() {
    return StringUtils.capitalize(name().toLowerCase(Locale.ROOT));
  }

  /**
   * Is deprecated boolean.
   *
   * @return the boolean
   */
  public boolean isDeprecated() {
    return deprecationDate != null;
  }

  /**
   * Gets deprecation date.
   *
   * @return the deprecation date
   */
  public Optional<String> getDeprecationDate() {
    return Optional.ofNullable(deprecationDate);
  }
}
