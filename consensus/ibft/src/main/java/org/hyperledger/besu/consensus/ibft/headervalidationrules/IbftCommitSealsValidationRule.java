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
package org.hyperledger.besu.consensus.ibft.headervalidationrules;

import static org.hyperledger.besu.consensus.ibft.IbftHelpers.calculateRequiredValidatorQuorum;

import org.hyperledger.besu.consensus.common.ValidatorProvider;
import org.hyperledger.besu.consensus.ibft.IbftBlockHashing;
import org.hyperledger.besu.consensus.ibft.IbftContext;
import org.hyperledger.besu.consensus.ibft.IbftExtraData;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.AttachedBlockHeaderValidationRule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Ensures the commit seals in the block header were created by known validators (as determined by
 * tracking votes and validator state on the blockchain).
 *
 * <p>This also ensures sufficient commit seals exist in the block to make it valid.
 */
public class IbftCommitSealsValidationRule
    implements AttachedBlockHeaderValidationRule<IbftContext> {

  private static final Logger LOGGER = LogManager.getLogger();

  @Override
  public boolean validate(
      final BlockHeader header,
      final BlockHeader parent,
      final ProtocolContext<IbftContext> protocolContext) {
    final ValidatorProvider validatorProvider =
        protocolContext.getConsensusState().getVoteTallyCache().getVoteTallyAfterBlock(parent);
    final IbftExtraData ibftExtraData = IbftExtraData.decode(header);

    final List<Address> committers =
        IbftBlockHashing.recoverCommitterAddresses(header, ibftExtraData);
    final List<Address> committersWithoutDuplicates = new ArrayList<>(new HashSet<>(committers));

    if (committers.size() != committersWithoutDuplicates.size()) {
      LOGGER.trace("Duplicated seals found in header.");
      return false;
    }

    return validateCommitters(committersWithoutDuplicates, validatorProvider.getValidators());
  }

  private boolean validateCommitters(
      final Collection<Address> committers, final Collection<Address> storedValidators) {

    final int minimumSealsRequired = calculateRequiredValidatorQuorum(storedValidators.size());
    if (committers.size() < minimumSealsRequired) {
      LOGGER.trace(
          "Insufficient committers to seal block. (Required {}, received {})",
          minimumSealsRequired,
          committers.size());
      return false;
    }

    if (!storedValidators.containsAll(committers)) {
      LOGGER.trace(
          "Not all committers are in the locally maintained validator list. validators={} committers={}",
          storedValidators,
          committers);
      return false;
    }

    return true;
  }

  @Override
  public boolean includeInLightValidation() {
    return false;
  }
}
