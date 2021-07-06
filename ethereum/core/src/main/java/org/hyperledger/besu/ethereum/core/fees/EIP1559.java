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

import static java.lang.Math.max;

import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.math.BigInteger;

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
    if (isForkBlock(blockNumber)) {
      return getFeeMarket().getInitialBasefee();
    }

    long gasDelta, feeDelta, baseFee;
    if (parentBlockGasUsed == targetGasUsed) {
      return parentBaseFee;
    } else if (parentBlockGasUsed > targetGasUsed) {
      gasDelta = parentBlockGasUsed - targetGasUsed;
      final BigInteger pBaseFee = BigInteger.valueOf(parentBaseFee);
      final BigInteger gDelta = BigInteger.valueOf(gasDelta);
      final BigInteger target = BigInteger.valueOf(targetGasUsed);
      final BigInteger denominator = BigInteger.valueOf(feeMarket.getBasefeeMaxChangeDenominator());
      feeDelta = max(pBaseFee.multiply(gDelta).divide(target).divide(denominator).longValue(), 1);
      baseFee = parentBaseFee + feeDelta;
    } else {
      gasDelta = targetGasUsed - parentBlockGasUsed;
      final BigInteger pBaseFee = BigInteger.valueOf(parentBaseFee);
      final BigInteger gDelta = BigInteger.valueOf(gasDelta);
      final BigInteger target = BigInteger.valueOf(targetGasUsed);
      final BigInteger denominator = BigInteger.valueOf(feeMarket.getBasefeeMaxChangeDenominator());
      feeDelta = pBaseFee.multiply(gDelta).divide(target).divide(denominator).longValue();
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

  public boolean isEIP1559(final long blockNumber) {
    return blockNumber >= initialForkBlknum;
  }

  public boolean isForkBlock(final long blockNumber) {
    return initialForkBlknum == blockNumber;
  }

  public long getForkBlock() {
    return initialForkBlknum;
  }

  public long targetGasUsed(final BlockHeader header) {
    return header.getGasLimit() / getFeeMarket().getSlackCoefficient();
  }

  public FeeMarket getFeeMarket() {
    return feeMarket;
  }
}
