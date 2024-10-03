/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.evm;

import org.hyperledger.besu.datatypes.HardforkId;
import org.hyperledger.besu.datatypes.HardforkId.MainnetHardforkId;

import java.util.Comparator;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The enum Evm spec version. */
public enum EvmSpecVersion {
  /** Frontier evm spec version. */
  FRONTIER(MainnetHardforkId.FRONTIER, Integer.MAX_VALUE, Integer.MAX_VALUE, 0),
  /** Homestead evm spec version. */
  HOMESTEAD(MainnetHardforkId.HOMESTEAD, Integer.MAX_VALUE, Integer.MAX_VALUE, 0),
  /** Tangerine Whistle evm spec version. */
  TANGERINE_WHISTLE(MainnetHardforkId.TANGERINE_WHISTLE, Integer.MAX_VALUE, Integer.MAX_VALUE, 0),
  /** Spurious Dragon evm spec version. */
  SPURIOUS_DRAGON(MainnetHardforkId.SPURIOUS_DRAGON, 0x6000, Integer.MAX_VALUE, 0),
  /** Byzantium evm spec version. */
  BYZANTIUM(MainnetHardforkId.BYZANTIUM, 0x6000, Integer.MAX_VALUE, 0),
  /** Constantinople evm spec version. */
  CONSTANTINOPLE(MainnetHardforkId.CONSTANTINOPLE, 0x6000, Integer.MAX_VALUE, 0),
  /** Petersburg / ConstantinopleFix evm spec version. */
  PETERSBURG(MainnetHardforkId.PETERSBURG, 0x6000, Integer.MAX_VALUE, 0),
  /** Istanbul evm spec version. */
  ISTANBUL(MainnetHardforkId.ISTANBUL, 0x6000, Integer.MAX_VALUE, 0),
  /** Berlin evm spec version */
  BERLIN(MainnetHardforkId.BERLIN, 0x6000, Integer.MAX_VALUE, 0),
  /** London evm spec version. */
  LONDON(MainnetHardforkId.LONDON, 0x6000, Integer.MAX_VALUE, 0),
  /** Paris evm spec version. */
  PARIS(MainnetHardforkId.PARIS, 0x6000, Integer.MAX_VALUE, 0),
  /** Shanghai evm spec version. */
  SHANGHAI(MainnetHardforkId.SHANGHAI, 0x6000, 0xc000, 0),
  /** Cancun evm spec version. */
  CANCUN(MainnetHardforkId.CANCUN, 0x6000, 0xc000, 0),
  /** Cancun evm spec version. */
  CANCUN_EOF(MainnetHardforkId.CANCUN_EOF, 0x6000, 0xc000, 1),
  /** Prague evm spec version. */
  PRAGUE(MainnetHardforkId.PRAGUE, 0x6000, 0xc000, 0),
  /** Osaka evm spec version. */
  OSAKA(MainnetHardforkId.OSAKA, 0x6000, 0xc000, 1),
  /** Amsterdam evm spec version. */
  AMSTERDAM(MainnetHardforkId.AMSTERDAM, 0x6000, 0xc000, 1),
  /** Bogota evm spec version. */
  BOGOTA(MainnetHardforkId.BOGOTA, 0x6000, 0xc000, 1),
  /** Polis evm spec version. */
  POLIS(MainnetHardforkId.POLIS, 0x6000, 0xc000, 1),
  /** Bangkok evm spec version. */
  BANGKOK(MainnetHardforkId.BANGKOK, 0x6000, 0xc000, 1),
  /** Development fork for unscheduled EIPs */
  FUTURE_EIPS(MainnetHardforkId.FUTURE_EIPS, 0x6000, 0xc000, 1),
  /** Development fork for EIPs that are not yet accepted to Mainnet */
  EXPERIMENTAL_EIPS(MainnetHardforkId.EXPERIMENTAL_EIPS, 0x6000, 0xc000, 1);

  private static final Logger LOGGER = LoggerFactory.getLogger(EvmSpecVersion.class);

  /** What hardfork did this VM version first show up in? */
  final HardforkId initialHardfork;

  /** The Max eof version. */
  final int maxEofVersion;

  /** Maximum size of deployed code */
  final int maxCodeSize;

  /** Maximum size of initcode */
  final int maxInitcodeSize;

  /** The Version warned. */
  boolean versionWarned = false;

  EvmSpecVersion(
      final HardforkId initialHarfork,
      final int maxCodeSize,
      final int maxInitcodeSize,
      final int maxEofVersion) {
    this.initialHardfork = initialHarfork;
    this.maxEofVersion = maxEofVersion;
    this.maxCodeSize = maxCodeSize;
    this.maxInitcodeSize = maxInitcodeSize;
  }

  /**
   * What is the "default" version of EVM that should be made. Newer versions of Besu will adjust
   * this to reflect mainnet fork development.
   *
   * @return the current mainnet for as of the release of this version of Besu
   */
  public static EvmSpecVersion defaultVersion() {
    EvmSpecVersion answer = null;
    for (EvmSpecVersion version : EvmSpecVersion.values()) {
      if (version.initialHardfork.finalized()) {
        answer = version;
      }
    }
    return answer;
  }

  /**
   * Gets max eof version.
   *
   * @return the max eof version
   */
  public int getMaxEofVersion() {
    return maxEofVersion;
  }

  /**
   * Gets max deployed code size this EVM supports.
   *
   * @return the max eof version
   */
  public int getMaxCodeSize() {
    return maxCodeSize;
  }

  /**
   * Gets max initcode size this EVM supports.
   *
   * @return the max eof version
   */
  public int getMaxInitcodeSize() {
    return maxInitcodeSize;
  }

  /**
   * Name of the fork, in execution-spec-tests form
   *
   * @return name of the fork
   */
  public String getName() {
    return initialHardfork.name();
  }

  /**
   * Description of the fork
   *
   * @return description
   */
  public String getDescription() {
    return initialHardfork.description();
  }

  /** Maybe warn version. */
  @SuppressWarnings("AlreadyChecked") // false positive
  public void maybeWarnVersion() {
    if (versionWarned) {
      return;
    }

    if (!initialHardfork.finalized()) {
      LOGGER.error(
          "****** Not for Production Network Use ******\nExecuting code from EVM Spec Version {}, which has not been finalized.\n****** Not for Production Network Use ******",
          this.name());
    }
    versionWarned = true;
  }

  /**
   * Calculate a spec version from a text fork name.
   *
   * @param name The name of the fork, such as "shanghai" or "berlin"
   * @return the EVM spec version for that fork, or null if no fork matched.
   */
  public static EvmSpecVersion fromName(final String name) {
    // TODO remove once CancunEOF tests are removed from EEST
    if ("prague".equalsIgnoreCase(name)) {
      return EvmSpecVersion.OSAKA;
    }
    // TODO remove once CancunEOF tests are removed from EEST
    if ("cancuneof".equalsIgnoreCase(name)) {
      return EvmSpecVersion.CANCUN_EOF;
    }
    for (var version : EvmSpecVersion.values()) {
      if (version.name().equalsIgnoreCase(name)) {
        return version;
      }
    }
    return null;
  }

  /**
   * The most recent deployed evm supported by the library. This will change across versions and
   * will be updated after mainnet activations.
   *
   * @return the most recently activated mainnet spec.
   */
  public static EvmSpecVersion mostRecent() {
    return Stream.of(EvmSpecVersion.values())
        .filter(v -> v.initialHardfork.finalized())
        .max(Comparator.naturalOrder())
        .orElseThrow();
  }
}
