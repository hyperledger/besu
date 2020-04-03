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
package org.hyperledger.besu.ethereum.core.fees;

import org.hyperledger.besu.config.experimental.ExperimentalEIPs;

import java.util.OptionalLong;

public class EIP1559 {

  private final long initialForkBlknum;
  private final long finalForkBlknum;

  public EIP1559(final OptionalLong forkBlockNumber) {
    this(forkBlockNumber.orElse(0));
  }

  public EIP1559(final long forkBlockNumber) {
    initialForkBlknum = forkBlockNumber;
    finalForkBlknum = initialForkBlknum + FeeMarket.eip1559DecayRange();
  }

  public long computeBaseFee(final long parentBaseFee, final long parentBlockGasUsed) {
    guardActivation();
    long delta = parentBlockGasUsed - FeeMarket.eip1559TargetGasUsed();
    return parentBaseFee
        + ((parentBaseFee * delta)
            / FeeMarket.eip1559TargetGasUsed()
            / FeeMarket.eip1559BasefeeMaxChangeDenominator());
  }

  public boolean isValidBaseFee(final long parentBaseFee, final long baseFee) {
    guardActivation();
    return Math.abs(baseFee - parentBaseFee)
        <= Math.max(1, parentBaseFee / FeeMarket.eip1559BasefeeMaxChangeDenominator());
  }

  public long eip1559GasPool(final long blockNumber) {
    guardActivation();
    if (blockNumber >= finalForkBlknum) {
      return FeeMarket.eip1559MaxGas();
    }
    return (FeeMarket.eip1559MaxGas() / 2)
        + ((blockNumber - initialForkBlknum) * FeeMarket.eip1559GasIncrementAmount());
  }

  public long legacyGasPool(final long blockNumber) {
    guardActivation();
    return FeeMarket.eip1559MaxGas() - eip1559GasPool(blockNumber);
  }

  public boolean isEIP1559(final long blockNumber) {
    guardActivation();
    return blockNumber >= initialForkBlknum;
  }

  public boolean isEIP1559Finalized(final long blockNumber) {
    guardActivation();
    return blockNumber >= finalForkBlknum;
  }

  public boolean isForkBlock(final long blockNumber) {
    guardActivation();
    return initialForkBlknum == blockNumber;
  }

  public long getForkBlock() {
    guardActivation();
    return initialForkBlknum;
  }

  private void guardActivation() {
    if (!ExperimentalEIPs.eip1559Enabled) {
      throw new RuntimeException("EIP-1559 is not enabled");
    }
  }
}
