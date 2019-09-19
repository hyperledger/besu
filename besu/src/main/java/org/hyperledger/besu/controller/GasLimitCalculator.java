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
 */
package org.hyperledger.besu.controller;

import java.util.Optional;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GasLimitCalculator implements Function<Long, Long> {
  private static final Logger LOG = LogManager.getLogger();
  public static final long ADJUSTMENT_FACTOR = 1024L;
  public static final Optional<Long> DEFAULT = Optional.empty();
  private final Optional<Long> targetGasLimit;

  public GasLimitCalculator(final Optional<Long> targetGasLimit) {
    if (targetGasLimit.orElse(0L) < 0L) {
      throw new IllegalArgumentException("Invalid target gas limit");
    }

    this.targetGasLimit = targetGasLimit;
  }

  @Override
  public Long apply(final Long gasLimit) {
    long newGasLimit =
        targetGasLimit
            .map(
                target -> {
                  if (target > gasLimit) {
                    return Math.min(target, safeAdd(gasLimit));
                  } else if (target < gasLimit) {
                    return Math.max(target, safeSub(gasLimit));
                  } else {
                    return gasLimit;
                  }
                })
            .orElse(gasLimit);

    if (newGasLimit != gasLimit) {
      LOG.debug("Adjusting block gas limit from {} to {}", gasLimit, newGasLimit);
    }

    return newGasLimit;
  }

  private long safeAdd(final long gasLimit) {
    if (gasLimit + ADJUSTMENT_FACTOR > gasLimit) {
      return gasLimit + ADJUSTMENT_FACTOR;
    } else {
      return Long.MAX_VALUE;
    }
  }

  private long safeSub(final long gasLimit) {
    if (gasLimit - ADJUSTMENT_FACTOR < gasLimit) {
      return Math.max(gasLimit - ADJUSTMENT_FACTOR, 0);
    } else {
      return 0;
    }
  }
}
