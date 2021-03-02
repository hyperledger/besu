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

import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Validates the gas used in executing the block (defined by the supplied header) is less than or
 * equal to the current gas limit.
 */
public class GasUsageValidationRule implements DetachedBlockHeaderValidationRule {

  private static final Logger LOG = LogManager.getLogger();

  private final Optional<EIP1559> eip1559;

  public GasUsageValidationRule() {
    this.eip1559 = Optional.empty();
  }

  public GasUsageValidationRule(final Optional<EIP1559> eip1559) {
    this.eip1559 = eip1559;
  }

  @Override
  public boolean validate(final BlockHeader header, final BlockHeader parent) {
    long slackCoefficient = 1;
    if (eip1559.isPresent() && eip1559.get().isEIP1559(header.getNumber())) {
      slackCoefficient = eip1559.get().getFeeMarket().getSlackCoefficient();
    }
    if (header.getGasUsed() > (header.getGasLimit() * slackCoefficient)) {
      LOG.info(
          "Invalid block header: gas used {} exceeds gas limit {}",
          header.getGasUsed(),
          header.getGasLimit());
      return false;
    }
    return true;
  }
}
