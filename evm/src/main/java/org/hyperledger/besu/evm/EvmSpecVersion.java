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

import java.util.Comparator;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The enum Evm spec version. */
public enum EvmSpecVersion {
  /** Frontier evm spec version. */
  FRONTIER(Integer.MAX_VALUE, Integer.MAX_VALUE, 0, true, "Frontier", "Finalized"),
  /** Homestead evm spec version. */
  HOMESTEAD(Integer.MAX_VALUE, Integer.MAX_VALUE, 0, true, "Homestead", "Finalized"),
  /** Tangerine Whistle evm spec version. */
  TANGERINE_WHISTLE(
      Integer.MAX_VALUE, Integer.MAX_VALUE, 0, true, "Tangerine Whistle", "Finalized"),
  /** Spurious Dragon evm spec version. */
  SPURIOUS_DRAGON(0x6000, Integer.MAX_VALUE, 0, true, "Spuruous Dragon", "Finalized"),
  /** Byzantium evm spec version. */
  BYZANTIUM(0x6000, Integer.MAX_VALUE, 0, true, "Byzantium", "Finalized"),
  /** Constantinople evm spec version. */
  CONSTANTINOPLE(0x6000, Integer.MAX_VALUE, 0, true, "Constantinople", "Did not reach Mainnet"),
  /** Petersburg / ConstantinopleFix evm spec version. */
  PETERSBURG(
      0x6000,
      Integer.MAX_VALUE,
      0,
      true,
      "ConstantinopleFix",
      "Finalized (also called Petersburg)"),
  /** Istanbul evm spec version. */
  ISTANBUL(0x6000, Integer.MAX_VALUE, 0, true, "Istanbul", "Finalized"),
  /** Berlin evm spec version */
  BERLIN(0x6000, Integer.MAX_VALUE, 0, true, "Berlin", "Finalized"),
  /** London evm spec version. */
  LONDON(0x6000, Integer.MAX_VALUE, 0, true, "London", "Finalized"),
  /** Paris evm spec version. */
  PARIS(0x6000, Integer.MAX_VALUE, 0, true, "Merge", "Finalized (also called Paris)"),
  /** Shanghai evm spec version. */
  SHANGHAI(0x6000, 0xc000, 0, true, "Shanghai", "Finalized"),
  /** Cancun evm spec version. */
  CANCUN(0x6000, 0xc000, 0, true, "Cancun", "Finalized"),
  /** Cancun evm spec version. */
  CANCUN_EOF(0x6000, 0xc000, 1, false, "CancunEOF", "For Testing"),
  /** Prague evm spec version. */
  PRAGUE(0x6000, 0xc000, 0, false, "Prague", "In Development"),
  /** PragueEOF evm spec version. */
  PRAGUE_EOF(0x6000, 0xc000, 1, false, "PragueEOF", "Prague + EOF.  In Development"),
  /** Osaka evm spec version. */
  OSAKA(0x6000, 0xc000, 1, false, "Osaka", "Placeholder"),
  /** Amstedam evm spec version. */
  AMSTERDAM(0x6000, 0xc000, 1, false, "Amsterdam", "Placeholder"),
  /** Bogota evm spec version. */
  BOGOTA(0x6000, 0xc000, 1, false, "Bogota", "Placeholder"),
  /** Polis evm spec version. */
  POLIS(0x6000, 0xc000, 1, false, "Polis", "Placeholder"),
  /** Bogota evm spec version. */
  BANGKOK(0x6000, 0xc000, 1, false, "Bangkok", "Placeholder"),
  /** Development fork for unscheduled EIPs */
  FUTURE_EIPS(
      0x6000, 0xc000, 1, false, "Future_EIPs", "Development, for accepted and unscheduled EIPs"),
  /** Development fork for EIPs not accepted to Mainnet */
  EXPERIMENTAL_EIPS(
      0x6000, 0xc000, 1, false, "Experimental_EIPs", "Development, for experimental EIPs"),
  /** Linea evm spec version */
  LINEA(0x6000, 0xc000, Integer.MAX_VALUE, false, "Linea", "Linea");
  private static final Logger LOGGER = LoggerFactory.getLogger(EvmSpecVersion.class);

  /** The Spec finalized. */
  final boolean specFinalized;

  /** The Max eof version. */
  final int maxEofVersion;

  /** Maximum size of deployed code */
  final int maxCodeSize;

  /** Maximum size of initcode */
  final int maxInitcodeSize;

  /** Public name matching execution-spec-tests name */
  final String name;

  /** A brief description of the state of the fork */
  final String description;

  /** The Version warned. */
  boolean versionWarned = false;

  EvmSpecVersion(
      final int maxCodeSize,
      final int maxInitcodeSize,
      final int maxEofVersion,
      final boolean specFinalized,
      final String name,
      final String description) {
    this.maxEofVersion = maxEofVersion;
    this.maxCodeSize = maxCodeSize;
    this.maxInitcodeSize = maxInitcodeSize;
    this.specFinalized = specFinalized;
    this.name = name;
    this.description = description;
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
      if (version.specFinalized) {
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
    return name;
  }

  /**
   * Description of the fork
   *
   * @return description
   */
  public String getDescription() {
    return description;
  }

  /** Maybe warn version. */
  @SuppressWarnings("AlreadyChecked") // false positive
  public void maybeWarnVersion() {
    if (versionWarned) {
      return;
    }

    if (!specFinalized) {
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
    // TODO remove once PragueEOF settles
    if ("prague".equalsIgnoreCase(name)) {
      return EvmSpecVersion.PRAGUE_EOF;
    }
    // TODO remove once PragueEOF settles
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
        .filter(v -> v.specFinalized)
        .max(Comparator.naturalOrder())
        .orElseThrow();
  }
}
