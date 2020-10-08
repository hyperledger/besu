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

import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TargetingGasLimitCalculator implements GasLimitCalculator {
  private static final Logger LOG = LogManager.getLogger();
  public static final long ADJUSTMENT_FACTOR = 1024L;
  private final Long targetGasLimit;

  public TargetingGasLimitCalculator(final Long targetGasLimit) {
    checkArgument(targetGasLimit >= 0, "Invalid target gas limit");

    this.targetGasLimit = targetGasLimit;
  }

  @Override
  public long nextGasLimit(final long previousGasLimit) {
    final long nextGasLimit;
    if (targetGasLimit > previousGasLimit) {
      nextGasLimit = Math.min(targetGasLimit, safeAdd(previousGasLimit));
    } else if (targetGasLimit < previousGasLimit) {
      nextGasLimit = Math.max(targetGasLimit, safeSub(previousGasLimit));
    } else {
      nextGasLimit = previousGasLimit;
    }

    if (nextGasLimit != previousGasLimit) {
      LOG.debug("Adjusting block gas limit from {} to {}", previousGasLimit, nextGasLimit);
    }

    return nextGasLimit;
  }

  private long safeAdd(final long gasLimit) {
    try {
      return Math.addExact(gasLimit, ADJUSTMENT_FACTOR);
    } catch (final ArithmeticException ex) {
      return Long.MAX_VALUE;
    }
  }

  private long safeSub(final long gasLimit) {
    try {
      return Math.max(Math.subtractExact(gasLimit, ADJUSTMENT_FACTOR), 0);
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
