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
package org.hyperledger.besu.ethereum.mainnet.headervalidationrules;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.fees.EIP1559;
import org.hyperledger.besu.ethereum.mainnet.AbstractGasLimitSpecification;
import org.hyperledger.besu.ethereum.mainnet.DetachedBlockHeaderValidationRule;

import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Responsible for ensuring the gasLimit specified in the supplied block header is within bounds as
 * specified at construction. And that the gasLimit for this block is within certain bounds of its
 * parent block.
 */
public class GasLimitRangeAndDeltaValidationRule extends AbstractGasLimitSpecification
    implements DetachedBlockHeaderValidationRule {

  private static final Logger LOG = LogManager.getLogger(GasLimitRangeAndDeltaValidationRule.class);

  private final Optional<EIP1559> eip1559;

  public GasLimitRangeAndDeltaValidationRule(
      final long minGasLimit, final long maxGasLimit, final Optional<EIP1559> eip1559) {
    super(minGasLimit, maxGasLimit);
    this.eip1559 = eip1559;
  }

  public GasLimitRangeAndDeltaValidationRule(final long minGasLimit, final long maxGasLimit) {
    this(minGasLimit, maxGasLimit, Optional.empty());
  }

  @Override
  public boolean validate(final BlockHeader header, final BlockHeader parent) {
    final long gasLimit = header.getGasLimit();

    if ((gasLimit < minGasLimit) || (gasLimit > maxGasLimit)) {
      LOG.info(
          "Invalid block header: gasLimit = {} is outside range {} --> {}",
          gasLimit,
          minGasLimit,
          maxGasLimit);
      return false;
    }

    long parentGasLimit = parent.getGasLimit();

    if (eip1559.isPresent() && eip1559.get().isForkBlock(header.getNumber())) {
      parentGasLimit = parent.getGasLimit() * eip1559.get().getFeeMarket().getSlackCoefficient();
    }

    final long difference = Math.abs(parentGasLimit - gasLimit);
    final long bounds = deltaBound(parentGasLimit);
    // this is an exclusive bound, so the difference should be strictly less:
    if (Long.compareUnsigned(difference, bounds) >= 0) {
      LOG.info(
          "Invalid block header: gas limit delta {} is out of bounds of {}", difference, bounds);
      return false;
    }

    return true;
  }
}
