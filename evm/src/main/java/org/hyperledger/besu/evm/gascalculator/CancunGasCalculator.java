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
package org.hyperledger.besu.evm.gascalculator;

import org.hyperledger.besu.datatypes.DataGas;

/** The Cancun gas calculator. */
public class CancunGasCalculator extends LondonGasCalculator {

  public static final int CANCUN_DATA_GAS_PER_BLOB = 131072; // 2^17
  public static final DataGas CANCUN_TARGET_DATA_GAS_PER_BLOCK = DataGas.of(262144); // 2^18

  public static final int CANCUN_MAX_DATA_GAS_PER_BLOCK = 524288; // 2^19

  @Override
  public int dataGasCost(final int blobCount) {
    return CANCUN_DATA_GAS_PER_BLOB * blobCount;
  }

  /**
   * Compute the new excess data gas for the block, using the parent value and the number of new
   * blobs
   *
   * @param parentExcessDataGas the excess data gas value from the parent block
   * @param newBlobs the number of blobs in the new block
   * @return the new excess data gas value
   */
  @Override
  public DataGas computeExcessDataGas(final DataGas parentExcessDataGas, final int newBlobs) {
    final int consumedDataGas = newBlobs * CANCUN_DATA_GAS_PER_BLOB;
    final DataGas currentExcessDataGas = parentExcessDataGas.add(consumedDataGas);

    if (currentExcessDataGas.lessThan(CANCUN_TARGET_DATA_GAS_PER_BLOCK)) {
      return DataGas.ZERO;
    }
    return currentExcessDataGas.add(CANCUN_TARGET_DATA_GAS_PER_BLOCK);
  }

  @Override
  public long getDataGasLimit() {
    return CANCUN_MAX_DATA_GAS_PER_BLOCK;
  }
}
