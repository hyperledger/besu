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
package org.hyperledger.besu.consensus.ibft.headervalidationrules;

import org.hyperledger.besu.consensus.common.ValidatorProvider;
import org.hyperledger.besu.consensus.ibft.IbftContext;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.AttachedBlockHeaderValidationRule;

import java.util.Collection;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Ensures that the coinbase (which corresponds to the block proposer) is included in the list of
 * validators
 */
public class IbftCoinbaseValidationRule implements AttachedBlockHeaderValidationRule<IbftContext> {

  private static final Logger LOGGER = LogManager.getLogger(IbftCoinbaseValidationRule.class);

  @Override
  public boolean validate(
      final BlockHeader header,
      final BlockHeader parent,
      final ProtocolContext<IbftContext> context) {

    final ValidatorProvider validatorProvider =
        context.getConsensusState().getVoteTallyCache().getVoteTallyAfterBlock(parent);
    final Address proposer = header.getCoinbase();

    final Collection<Address> storedValidators = validatorProvider.getValidators();

    if (!storedValidators.contains(proposer)) {
      LOGGER.trace(
          "Block proposer is not a member of the validators. proposer={}, validators={}",
          proposer,
          storedValidators);
      return false;
    }

    return true;
  }
}
