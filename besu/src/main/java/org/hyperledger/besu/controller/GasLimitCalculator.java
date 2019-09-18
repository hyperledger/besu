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

import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GasLimitCalculator implements Function<Long, Long> {
  private static final Logger LOG = LogManager.getLogger();
  public static final long ADJUSTMENT_FACTOR = 1024L;
  public static final Long DEFAULT = null;
  private final long targetGasLimit;

  public GasLimitCalculator(final Long targetGasLimit) {
    this.targetGasLimit = targetGasLimit == null ? 0L : targetGasLimit;
  }

  @Override
  public Long apply(final Long gasLimit) {
    long newGasLimit;

    if (targetGasLimit > gasLimit) {
      newGasLimit = Math.min(targetGasLimit, gasLimit + ADJUSTMENT_FACTOR);
    } else if (targetGasLimit < gasLimit) {
      newGasLimit = Math.max(targetGasLimit, gasLimit - ADJUSTMENT_FACTOR);
    } else {
      return gasLimit;
    }

    LOG.debug("Adjusting block gas limit from {} to {}", gasLimit, newGasLimit);

    return newGasLimit;
  }
}
