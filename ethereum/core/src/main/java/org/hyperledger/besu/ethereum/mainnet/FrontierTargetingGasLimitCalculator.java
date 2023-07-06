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

import org.hyperledger.besu.ethereum.GasLimitCalculator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FrontierTargetingGasLimitCalculator extends AbstractGasLimitSpecification
    implements GasLimitCalculator {
  private static final Logger LOG =
      LoggerFactory.getLogger(FrontierTargetingGasLimitCalculator.class);
  private final long maxConstantAdjustmentIncrement;

  public FrontierTargetingGasLimitCalculator() {
    this(DEFAULT_MAX_CONSTANT_ADMUSTMENT_INCREMENT, DEFAULT_MIN_GAS_LIMIT, DEFAULT_MAX_GAS_LIMIT);
  }

  public FrontierTargetingGasLimitCalculator(
      final long maxConstantAdjustmentIncrement, final long minGasLimit, final long maxGasLimit) {
    super(minGasLimit, maxGasLimit);
    this.maxConstantAdjustmentIncrement = maxConstantAdjustmentIncrement;
  }

  @Override
  public long nextGasLimit(
      final long currentGasLimit, final long targetGasLimit, final long newBlockNumber) {
    final long nextGasLimit;
    if (targetGasLimit > currentGasLimit) {
      nextGasLimit = Math.min(targetGasLimit, safeAddAtMost(currentGasLimit));
    } else if (targetGasLimit < currentGasLimit) {
      nextGasLimit = Math.max(targetGasLimit, safeSubAtMost(currentGasLimit));
    } else {
      nextGasLimit = currentGasLimit;
    }

    if (nextGasLimit != currentGasLimit) {
      LOG.debug("Adjusting block gas limit from {} to {}", currentGasLimit, nextGasLimit);
    }

    return nextGasLimit;
  }

  private long adjustAmount(final long currentGasLimit) {
    final long maxProportionalAdjustmentLimit = Math.max(deltaBound(currentGasLimit) - 1, 0);
    return Math.min(maxConstantAdjustmentIncrement, maxProportionalAdjustmentLimit);
  }

  protected long safeAddAtMost(final long gasLimit) {
    try {
      return Math.addExact(gasLimit, adjustAmount(gasLimit));
    } catch (final ArithmeticException ex) {
      return Long.MAX_VALUE;
    }
  }

  protected long safeSubAtMost(final long gasLimit) {
    try {
      return Math.max(Math.subtractExact(gasLimit, adjustAmount(gasLimit)), 0);
    } catch (final ArithmeticException ex) {
      return 0;
    }
  }
}
