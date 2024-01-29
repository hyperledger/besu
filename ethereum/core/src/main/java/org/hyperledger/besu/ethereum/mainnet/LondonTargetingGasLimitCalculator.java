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
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.ethereum.mainnet.feemarket.BaseFeeMarket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LondonTargetingGasLimitCalculator extends FrontierTargetingGasLimitCalculator {
  private static final Logger LOG =
      LoggerFactory.getLogger(LondonTargetingGasLimitCalculator.class);
  private final long londonForkBlock;
  private final BaseFeeMarket feeMarket;

  public LondonTargetingGasLimitCalculator(
      final long londonForkBlock, final BaseFeeMarket feeMarket) {
    this(DEFAULT_MIN_GAS_LIMIT, DEFAULT_MAX_GAS_LIMIT, londonForkBlock, feeMarket);
  }

  public LondonTargetingGasLimitCalculator(
      final long minGasLimit,
      final long maxGasLimit,
      final long londonForkBlock,
      final BaseFeeMarket feeMarket) {
    super(minGasLimit, maxGasLimit);
    this.londonForkBlock = londonForkBlock;
    this.feeMarket = feeMarket;
  }

  @Override
  public long nextGasLimit(
      final long currentGasLimit, final long targetGasLimit, final long newBlockNumber) {
    long nextGasLimit;
    if (targetGasLimit > currentGasLimit) {
      nextGasLimit = Math.min(targetGasLimit, safeAddAtMost(currentGasLimit));
    } else if (targetGasLimit < currentGasLimit) {
      nextGasLimit = Math.max(targetGasLimit, safeSubAtMost(currentGasLimit));
    } else {
      nextGasLimit = currentGasLimit;
    }

    if (londonForkBlock == newBlockNumber) {
      nextGasLimit = nextGasLimit * feeMarket.getSlackCoefficient();
    }

    if (nextGasLimit != currentGasLimit) {
      LOG.debug("Adjusting block gas limit from {} to {}", currentGasLimit, nextGasLimit);
    }

    return nextGasLimit;
  }
}
