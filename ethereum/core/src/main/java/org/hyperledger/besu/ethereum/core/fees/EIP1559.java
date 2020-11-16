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
import static java.lang.Math.max;

import org.hyperledger.besu.config.experimental.ExperimentalEIPs;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class EIP1559 {
  private static final Logger LOG = LogManager.getLogger();

  private final long initialForkBlknum;

  private final FeeMarket feeMarket = FeeMarket.eip1559();

  public EIP1559(final long forkBlockNumber) {
    initialForkBlknum = forkBlockNumber;
  }

  public long computeBaseFee(
      final long blockNumber,
      final long parentBaseFee,
      final long parentBlockGasUsed,
      final long targetGasUsed) {
    guardActivation();
    long gasDelta, feeDelta, baseFee;
    if (parentBlockGasUsed == targetGasUsed) {
      return parentBaseFee;
    } else if (parentBlockGasUsed > targetGasUsed) {
      gasDelta = parentBlockGasUsed - targetGasUsed;
      feeDelta =
          max(
              floorDiv(
                  floorDiv(parentBaseFee * gasDelta, targetGasUsed),
                  feeMarket.getBasefeeMaxChangeDenominator()),
              1);
      baseFee = parentBaseFee + feeDelta;
    } else {
      gasDelta = targetGasUsed - parentBlockGasUsed;
      feeDelta =
          floorDiv(
              floorDiv(parentBaseFee * gasDelta, targetGasUsed),
              feeMarket.getBasefeeMaxChangeDenominator());
      baseFee = parentBaseFee - feeDelta;
    }
    LOG.trace(
        "block #{} parentBaseFee: {} parentGasUsed: {} parentGasTarget: {} baseFee: {}",
        blockNumber,
        parentBaseFee,
        parentBlockGasUsed,
        targetGasUsed,
        baseFee);
    return baseFee;
  }

  public boolean isValidBaseFee(final long parentBaseFee, final long baseFee) {
    guardActivation();
    return Math.abs(baseFee - parentBaseFee)
        <= Math.max(1, parentBaseFee / feeMarket.getBasefeeMaxChangeDenominator());
  }

  public boolean isEIP1559(final long blockNumber) {
    guardActivation();
    return blockNumber >= initialForkBlknum;
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

  public long targetGasUsed(final BlockHeader header) {
    guardActivation();
    return header.getGasLimit();
  }

  public FeeMarket getFeeMarket() {
    return feeMarket;
  }
}
