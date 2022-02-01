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
import org.hyperledger.besu.ethereum.mainnet.AbstractGasLimitSpecification;
import org.hyperledger.besu.ethereum.mainnet.DetachedBlockHeaderValidationRule;
import org.hyperledger.besu.ethereum.mainnet.feemarket.BaseFeeMarket;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Responsible for ensuring the gasLimit specified in the supplied block header is within bounds as
 * specified at construction. And that the gasLimit for this block is within certain bounds of its
 * parent block.
 */
public class GasLimitRangeAndDeltaValidationRule extends AbstractGasLimitSpecification
    implements DetachedBlockHeaderValidationRule {

  private static final Logger LOG =
      LoggerFactory.getLogger(GasLimitRangeAndDeltaValidationRule.class);

  private final Optional<BaseFeeMarket> baseFeeMarket;

  public GasLimitRangeAndDeltaValidationRule(
      final long minGasLimit, final long maxGasLimit, final Optional<BaseFeeMarket> baseFeeMarket) {
    super(minGasLimit, maxGasLimit);
    this.baseFeeMarket = baseFeeMarket;
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

    if (baseFeeMarket.isEmpty() && header.getBaseFee().isPresent()) {
      LOG.info(
          "Invalid block header: basefee should not be present in a block without a base fee market");
      return false;
    }

    long parentGasLimit =
        baseFeeMarket
            .filter(baseFeeMarket -> baseFeeMarket.isForkBlock(header.getNumber()))
            .map(baseFeeMarket -> parent.getGasLimit() * baseFeeMarket.getSlackCoefficient())
            .orElse(parent.getGasLimit());

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
