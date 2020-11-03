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
package org.hyperledger.besu.controller;

import static com.google.common.base.Preconditions.checkArgument;

import org.hyperledger.besu.ethereum.blockcreation.GasLimitCalculator;
import org.hyperledger.besu.ethereum.mainnet.AbstractGasLimitSpecification;

import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TargetingGasLimitCalculator extends AbstractGasLimitSpecification
    implements GasLimitCalculator {
  private static final Logger LOG = LogManager.getLogger();
  private final long maxConstantAdjustmentIncrement;
  private Long targetGasLimit;

  public TargetingGasLimitCalculator(final long targetGasLimit) {
    this(targetGasLimit, 1024L, 5000L, Long.MAX_VALUE);
  }

  public TargetingGasLimitCalculator(
      final long targetGasLimit,
      final long maxConstantAdjustmentIncrement,
      final long minGasLimit,
      final long maxGasLimit) {
    super(minGasLimit, maxGasLimit);
    this.maxConstantAdjustmentIncrement = maxConstantAdjustmentIncrement;
    changeTargetGasLimit(targetGasLimit);
  }

  @Override
  public long nextGasLimit(final long currentGasLimit) {
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

  @Override
  public void changeTargetGasLimit(final Long targetGasLimit) {
    checkArgument(
        targetGasLimit >= minGasLimit,
        "targetGasLimit of " + targetGasLimit + " is below the minGasLimit of " + minGasLimit);

    checkArgument(
        targetGasLimit <= maxGasLimit,
        "targetGasLimit of " + targetGasLimit + " is above the maxGasLimit of " + maxGasLimit);
    this.targetGasLimit = targetGasLimit;
  }

  private long adjustAmount(final long currentGasLimit) {
    final long maxProportionalAdjustmentLimit = Math.max(deltaBound(currentGasLimit) - 1, 0);
    return Math.min(maxConstantAdjustmentIncrement, maxProportionalAdjustmentLimit);
  }

  private long safeAddAtMost(final long gasLimit) {
    try {
      return Math.addExact(gasLimit, adjustAmount(gasLimit));
    } catch (final ArithmeticException ex) {
      return Long.MAX_VALUE;
    }
  }

  private long safeSubAtMost(final long gasLimit) {
    try {
      return Math.max(Math.subtractExact(gasLimit, adjustAmount(gasLimit)), 0);
    } catch (final ArithmeticException ex) {
      return 0;
    }
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TargetingGasLimitCalculator that = (TargetingGasLimitCalculator) o;
    return Objects.equals(targetGasLimit, that.targetGasLimit);
  }

  @Override
  public int hashCode() {
    return Objects.hash(targetGasLimit);
  }
}
