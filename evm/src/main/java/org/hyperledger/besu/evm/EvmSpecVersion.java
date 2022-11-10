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

public enum EvmSpecVersion {
  FRONTIER(0, true),
  HOMESTEAD(0, true),
  BYZANTIUM(0, true),
  CONSTANTINOPLE(0, true),
  ISTANBUL(0, true),
  LONDON(0, true),
  PARIS(0, true),
  SHANGHAI(1, false),

  /** Transient fork, will be removed */
  SHANDONG(1, false);

  private static final Logger LOGGER = LoggerFactory.getLogger(EvmSpecVersion.class);

  final boolean specFinalized;
  final int maxEofVersion;

  boolean versionWarned = false;

  EvmSpecVersion(final int maxEofVersion, final boolean specFinalized) {
    this.maxEofVersion = maxEofVersion;
    this.specFinalized = specFinalized;
  }

  public int getMaxEofVersion() {
    return maxEofVersion;
  }

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
