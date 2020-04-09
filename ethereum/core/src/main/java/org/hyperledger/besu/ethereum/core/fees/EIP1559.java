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
import org.hyperledger.besu.ethereum.core.Transaction;

import java.util.function.LongSupplier;

public class EIP1559 {

  private final long initialForkBlknum;
  private final long finalForkBlknum;

  private final FeeMarket feeMarket = FeeMarket.eip1559();

  public EIP1559(final long forkBlockNumber) {
    initialForkBlknum = forkBlockNumber;
    finalForkBlknum = initialForkBlknum + feeMarket.getDecayRange();
  }

  public long computeBaseFee(final long parentBaseFee, final long parentBlockGasUsed) {
    guardActivation();
    long delta = parentBlockGasUsed - feeMarket.getTargetGasUsed();
    return parentBaseFee
        + ((parentBaseFee * delta)
            / feeMarket.getTargetGasUsed()
            / feeMarket.getBasefeeMaxChangeDenominator());
  }

  public boolean isValidBaseFee(final long parentBaseFee, final long baseFee) {
    guardActivation();
    return Math.abs(baseFee - parentBaseFee)
        <= Math.max(1, parentBaseFee / feeMarket.getBasefeeMaxChangeDenominator());
  }

  public long eip1559GasPool(final long blockNumber) {
    guardActivation();
    if (blockNumber >= finalForkBlknum) {
      return feeMarket.getMaxGas();
    }
    return (feeMarket.getMaxGas() / 2)
        + ((blockNumber - initialForkBlknum) * feeMarket.getGasIncrementAmount());
  }

  public long legacyGasPool(final long blockNumber) {
    guardActivation();
    return feeMarket.getMaxGas() - eip1559GasPool(blockNumber);
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

  public boolean isValid(final Transaction transaction, final LongSupplier blockNumberSupplier) {
    return isValid(transaction, blockNumberSupplier.getAsLong());
  }

  public boolean isValid(final Transaction transaction, final long blockNumber) {
    if (transaction == null) {
      return false;
    }
    if (blockNumber < getForkBlock()) {
      return transaction.isFrontierTransaction();
    } else if (isEIP1559Finalized(blockNumber)) {
      return transaction.isEIP1559Transaction();
    } else {
      return transaction.isFrontierTransaction() || transaction.isEIP1559Transaction();
    }
  }

  private void guardActivation() {
    if (!ExperimentalEIPs.eip1559Enabled) {
      throw new RuntimeException("EIP-1559 is not enabled");
    }
  }

  public FeeMarket getFeeMarket() {
    return feeMarket;
  }
}
