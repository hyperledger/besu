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

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.fees.EIP1559Config;
import org.hyperledger.besu.ethereum.core.fees.EIP1559Manager;
import org.hyperledger.besu.ethereum.mainnet.AttachedBlockHeaderValidationRule;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class EIP1559BlockHeaderGasPriceValidationRule<C>
    implements AttachedBlockHeaderValidationRule<C> {
  private final Logger LOG = LogManager.getLogger(CalculatedDifficultyValidationRule.class);
  private final EIP1559Manager eip1559;

  public EIP1559BlockHeaderGasPriceValidationRule(final EIP1559Manager eip1559) {
    this.eip1559 = eip1559;
  }

  @Override
  public boolean validate(
      final BlockHeader header,
      final BlockHeader parent,
      final ProtocolContext<C> protocolContext) {
    if (!eip1559.isEIP1559(header.getNumber())) {
      return true;
    }
    if (eip1559.isForkBlock(header.getNumber())) {
      return EIP1559Config.INITIAL_BASEFEE == header.getBaseFee();
    }

    if (parent.getBaseFee() == null || header.getBaseFee() == null) {
      LOG.trace(
          "Invalid block header: basefee should be specified for parent and current block header");
    }

    final long baseFee = eip1559.computeBaseFee(parent.getBaseFee(), parent.getGasUsed());
    if (baseFee != header.getBaseFee()) {
      LOG.trace(
          "Invalid block header: basefee {} does not equal expected basefee {}",
          header.getBaseFee(),
          baseFee);
      return false;
    }
    return baseFee == header.getBaseFee()
        && eip1559.isValidBaseFee(parent.getBaseFee(), header.getBaseFee());
  }
}
