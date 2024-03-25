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

import static com.google.common.base.Preconditions.checkArgument;

/** Specification for the block gasLimit. */
public abstract class AbstractGasLimitSpecification {

  public static final long DEFAULT_MIN_GAS_LIMIT = 5000L;
  public static final long DEFAULT_MAX_GAS_LIMIT = Long.MAX_VALUE;

  private static final int GAS_LIMIT_BOUND_DIVISOR = 1024;
  protected final long minGasLimit;
  protected final long maxGasLimit;

  protected AbstractGasLimitSpecification(final long minGasLimit, final long maxGasLimit) {
    checkArgument(
        minGasLimit >= GAS_LIMIT_BOUND_DIVISOR,
        String.format(
            "minGasLimit of %d is below the bound divisor of %d",
            minGasLimit, GAS_LIMIT_BOUND_DIVISOR));
    this.minGasLimit = minGasLimit;
    this.maxGasLimit = maxGasLimit;
  }

  public static long deltaBound(final long currentGasLimit) {
    return Long.divideUnsigned(currentGasLimit, GAS_LIMIT_BOUND_DIVISOR);
  }

  /**
   * Verify that the target gas limit is within the allowed bounds.
   *
   * @param targetGasLimit the target gas limit to validate
   * @return true if within bounds
   */
  @SuppressWarnings("ComparisonOutOfRange")
  public static boolean isValidTargetGasLimit(final long targetGasLimit) {
    return DEFAULT_MIN_GAS_LIMIT <= targetGasLimit && DEFAULT_MAX_GAS_LIMIT >= targetGasLimit;
  }
}
