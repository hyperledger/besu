/*
 * Copyright contributors to Hyperledger Besu
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
 *
 */
package org.hyperledger.besu.evm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The enum Evm spec version. */
public enum EvmSpecVersion {
  /** Frontier evm spec version. */
  FRONTIER(0, true),
  /** Homestead evm spec version. */
  HOMESTEAD(0, true),
  /** Byzantium evm spec version. */
  BYZANTIUM(0, true),
  /** Constantinople evm spec version. */
  CONSTANTINOPLE(0, true),
  /** Istanbul evm spec version. */
  ISTANBUL(0, true),
  /** London evm spec version. */
  LONDON(0, true),
  /** Paris evm spec version. */
  PARIS(0, true),
  /** Shanghai evm spec version. */
  SHANGHAI(0, true),
  /** Cancun evm spec version. */
  CANCUN(0, false),
  /** Prague evm spec version. */
  PRAGUE(0, false),
  /** Osaka evm spec version. */
  OSAKA(0, false),
  /** Bogota evm spec version. */
  BOGOTA(0, false),
  /** Development fork for unscheduled EIPs */
  FUTURE_EIPS(1, false),
  /** Development fork for EIPs not accepted to Mainnet */
  EXPERIMENTAL_EIPS(1, false);

  private static final Logger LOGGER = LoggerFactory.getLogger(EvmSpecVersion.class);

  /** The Spec finalized. */
  final boolean specFinalized;
  /** The Max eof version. */
  final int maxEofVersion;

  /** The Version warned. */
  boolean versionWarned = false;

  EvmSpecVersion(final int maxEofVersion, final boolean specFinalized) {
    this.maxEofVersion = maxEofVersion;
    this.specFinalized = specFinalized;
  }

  /**
   * Gets max eof version.
   *
   * @return the max eof version
   */
  public int getMaxEofVersion() {
    return maxEofVersion;
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
}
