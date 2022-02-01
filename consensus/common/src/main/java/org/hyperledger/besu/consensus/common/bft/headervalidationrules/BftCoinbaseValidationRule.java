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
package org.hyperledger.besu.consensus.common.bft.headervalidationrules;

import org.hyperledger.besu.consensus.common.bft.BftContext;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.AttachedBlockHeaderValidationRule;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ensures that the coinbase (which corresponds to the block proposer) is included in the list of
 * validators
 */
public class BftCoinbaseValidationRule implements AttachedBlockHeaderValidationRule {

  private static final Logger LOGGER = LoggerFactory.getLogger(BftCoinbaseValidationRule.class);

  @Override
  public boolean validate(
      final BlockHeader header, final BlockHeader parent, final ProtocolContext context) {

    final Collection<Address> storedValidators =
        context
            .getConsensusContext(BftContext.class)
            .getValidatorProvider()
            .getValidatorsAfterBlock(parent);
    final Address proposer = header.getCoinbase();

    if (!storedValidators.contains(proposer)) {
      LOGGER.info(
          "Invalid block header: Block proposer is not a member of the validators. proposer={}, validators={}",
          proposer,
          storedValidators);
      return false;
    }

    return true;
  }
}
