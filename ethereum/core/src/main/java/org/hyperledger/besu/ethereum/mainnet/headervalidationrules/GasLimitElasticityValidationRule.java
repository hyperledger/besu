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
import org.hyperledger.besu.ethereum.mainnet.DetachedBlockHeaderValidationRule;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GasLimitElasticityValidationRule implements DetachedBlockHeaderValidationRule {

  private static final int GAS_LIMIT_BOUND_DIVISOR = 1024;

  private static final Logger LOG = LogManager.getLogger(GasLimitElasticityValidationRule.class);

  private final EIP1559 eip1559;

  public GasLimitElasticityValidationRule(final EIP1559 eip1559) {
    this.eip1559 = eip1559;
  }

  @Override
  public boolean validate(final BlockHeader header, final BlockHeader parent) {
    final long parentGasTarget;
    if (eip1559.isForkBlock(header.getNumber())) {
      parentGasTarget = parent.getGasLimit();
    } else {
      parentGasTarget = parent.getGasLimit() / eip1559.getFeeMarket().getSlackCoefficient();
    }

    // check if the gas limit is at least the minimum gas limit
    if (header.getGasLimit() < eip1559.getFeeMarket().getBlockGasLimit()) {
      LOG.info("Invalid block header: block gas limit is not at least the minimum gas limit");
      return false;
    }

    // check if the block changed the gas target too much
    final long gasTarget = header.getGasLimit() / eip1559.getFeeMarket().getSlackCoefficient();
    if (gasTarget > (parentGasTarget + (parentGasTarget / GAS_LIMIT_BOUND_DIVISOR))) {
      LOG.info("Invalid block header: gas target increased too much");
      return false;
    }
    if (gasTarget < (parentGasTarget - (parentGasTarget / GAS_LIMIT_BOUND_DIVISOR))) {
      LOG.info("Invalid block header: gas target decreased too much");
      return false;
    }

    return true;
  }
}
