/*
 * Copyright 2018 ConsenSys AG.
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
package org.hyperledger.besu.ethereum.mainnet.headervalidationrules;

import static com.google.common.base.Preconditions.checkArgument;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.DetachedBlockHeaderValidationRule;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Responsible for ensuring the gasLimit specified in the supplied block header is within bounds as
 * specified at construction. And that the gasLimit for this block is within certain bounds of its
 * parent block.
 */
public class GasLimitRangeAndDeltaValidationRule implements DetachedBlockHeaderValidationRule {

  private static final Logger LOG = LogManager.getLogger(GasLimitRangeAndDeltaValidationRule.class);
  private static final int GASLIMIT_BOUND_DIVISOR = 1024;
  private final long minGasLimit;
  private final long maxGasLimit;

  public GasLimitRangeAndDeltaValidationRule(final long minGasLimit, final long maxGasLimit) {
    checkArgument(
        minGasLimit >= GASLIMIT_BOUND_DIVISOR,
        "minGasLimit of "
            + minGasLimit
            + " is below the bound divisor of "
            + GASLIMIT_BOUND_DIVISOR);
    this.minGasLimit = minGasLimit;
    this.maxGasLimit = maxGasLimit;
  }

  @Override
  public boolean validate(final BlockHeader header, final BlockHeader parent) {
    final long gasLimit = header.getGasLimit();

    if ((gasLimit < minGasLimit) || (gasLimit > maxGasLimit)) {
      LOG.trace(
          "Header gasLimit = {}, outside range {} --> {}", gasLimit, minGasLimit, maxGasLimit);
      return false;
    }

    final long parentGasLimit = parent.getGasLimit();
    final long difference = Math.abs(parentGasLimit - gasLimit);
    final long bounds = Long.divideUnsigned(parentGasLimit, GASLIMIT_BOUND_DIVISOR);
    if (Long.compareUnsigned(difference, bounds) >= 0) {
      LOG.trace(
          "Invalid block header: gas limit delta {} is out of bounds of {}", gasLimit, bounds);
      return false;
    }

    return true;
  }
}
