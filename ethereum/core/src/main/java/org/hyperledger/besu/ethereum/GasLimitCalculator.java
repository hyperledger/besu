/*
 * Copyright 2019 ConsenSys AG.
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
package org.hyperledger.besu.ethereum;

/** The GasLimitCalculator interface defines methods for calculating the gas limit. */
public interface GasLimitCalculator {

  /**
   * The constant BLOB_GAS_LIMIT represents the gas limit for blob data. Defaults to the Cancun
   * value where it was first introduced as part of EIP-4844
   */
  long BLOB_GAS_LIMIT = 0xC0000;

  /**
   * Calculates the next gas limit based on the current gas limit, target gas limit, and new block
   * number.
   *
   * @param currentGasLimit the current gas limit
   * @param targetGasLimit the target gas limit
   * @param newBlockNumber the new block number
   * @return the calculated next gas limit
   */
  long nextGasLimit(long currentGasLimit, long targetGasLimit, long newBlockNumber);

  /**
   * Returns a GasLimitCalculator that always returns the current gas limit.
   *
   * @return a GasLimitCalculator that always returns the current gas limit
   */
  static GasLimitCalculator constant() {
    return (currentGasLimit, targetGasLimit, newBlockNumber) -> currentGasLimit;
  }

  /**
   * Returns the current blob gas limit.
   *
   * @return the current blob gas limit
   */
  default long currentBlobGasLimit() {
    return BLOB_GAS_LIMIT;
  }
}
