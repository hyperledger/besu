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

import static java.lang.Math.floorDiv;
import static org.hyperledger.besu.ethereum.core.AcceptedTransactionTypes.FEE_MARKET_TRANSACTIONS;
import static org.hyperledger.besu.ethereum.core.AcceptedTransactionTypes.FEE_MARKET_TRANSITIONAL_TRANSACTIONS;
import static org.hyperledger.besu.ethereum.core.AcceptedTransactionTypes.FRONTIER_TRANSACTIONS;

import org.hyperledger.besu.config.experimental.ExperimentalEIPs;
import org.hyperledger.besu.ethereum.core.AcceptedTransactionTypes;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;

public class EIP1559 {
  private final long initialForkBlknum;
  private final long finalForkBlknum;

  private final FeeMarket feeMarket = FeeMarket.eip1559();

  public EIP1559(final long forkBlockNumber) {
    initialForkBlknum = forkBlockNumber;
    finalForkBlknum = initialForkBlknum + feeMarket.getMigrationDurationInBlocks();
  }

  public long computeBaseFee(
      final long parentBaseFee, final long parentBlockGasUsed, final long targetGasUsed) {
    guardActivation();
    assert targetGasUsed != 0L;
    long delta = parentBlockGasUsed - targetGasUsed;
    long baseFee =
        parentBaseFee
            + floorDiv(
                floorDiv(parentBaseFee * delta, targetGasUsed),
                feeMarket.getBasefeeMaxChangeDenominator());
    boolean neg = false;
    long diff = baseFee - parentBaseFee;
    if (diff < 0) {
      neg = true;
      diff = -diff;
    }

    long max = floorDiv(parentBaseFee, feeMarket.getBasefeeMaxChangeDenominator());
    if (max < 1) {
      max = 1;
    }
    if (diff > max) {
      if (neg) {
        max = -max;
      }
      baseFee = parentBaseFee + max;
    }

    if (baseFee <= 0) {
      baseFee = Wei.ONE.toLong();
    }

    return baseFee;
  }

  public boolean isValidBaseFee(final long parentBaseFee, final long baseFee) {
    guardActivation();
    return baseFee > 0
        && Math.abs(baseFee - parentBaseFee)
            <= Math.max(1, parentBaseFee / feeMarket.getBasefeeMaxChangeDenominator());
  }

  public long eip1559GasPool(final long blockNumber, final long gasLimit) {

    guardActivation();
    final long eip1559GasTarget;
    if (blockNumber >= finalForkBlknum) {
      eip1559GasTarget = gasLimit * 2;
    } else {
      eip1559GasTarget =
          (gasLimit / 2)
              + (gasLimit / 2)
                  * (blockNumber - initialForkBlknum)
                  / feeMarket.getMigrationDurationInBlocks();
    }
    return eip1559GasTarget * 2;
  }

  public long legacyGasPool(final long blockNumber, final long gasLimit) {
    guardActivation();
    return gasLimit - (eip1559GasPool(blockNumber, gasLimit) / 2);
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

  public boolean isValidFormat(
      final Transaction transaction, final AcceptedTransactionTypes acceptedTransactionTypes) {
    if (transaction == null) {
      return false;
    }
    switch (acceptedTransactionTypes) {
      case FRONTIER_TRANSACTIONS:
        return transaction.isFrontierTransaction();
      case FEE_MARKET_TRANSITIONAL_TRANSACTIONS:
        return transaction.isFrontierTransaction() || transaction.isEIP1559Transaction();
      case FEE_MARKET_TRANSACTIONS:
        return transaction.isEIP1559Transaction();
      default:
        return false;
    }
  }

  public boolean isValidTransaction(final long blockNumber, final Transaction transaction) {
    return isValidFormat(
        transaction,
        isEIP1559Finalized(blockNumber)
            ? FEE_MARKET_TRANSACTIONS
            : isEIP1559(blockNumber)
                ? FEE_MARKET_TRANSITIONAL_TRANSACTIONS
                : FRONTIER_TRANSACTIONS);
  }

  private void guardActivation() {
    if (!ExperimentalEIPs.eip1559Enabled) {
      throw new RuntimeException("EIP-1559 is not enabled");
    }
  }

  public long targetGasUsed(final BlockHeader header) {
    guardActivation();
    final long blockNumber = header.getNumber();
    final long migrationDuration = feeMarket.getMigrationDurationInBlocks();
    final long gasTarget = header.getGasLimit();
    return targetGasUsed(blockNumber, migrationDuration, gasTarget, initialForkBlknum);
  }

  public static long targetGasUsed(
      final long blockNumber,
      final long migrationDuration,
      final long gasTarget,
      final long forkBlock) {
    final long blocksSinceStartOfMigration = blockNumber - forkBlock;
    final long halfGasTarget = floorDiv(gasTarget, 2L);
    return (blockNumber < forkBlock)
        ? 0L
        : (blockNumber > forkBlock + migrationDuration)
            ? gasTarget
            : halfGasTarget
                + floorDiv(halfGasTarget * blocksSinceStartOfMigration, migrationDuration);
  }

  public FeeMarket getFeeMarket() {
    return feeMarket;
  }
}
