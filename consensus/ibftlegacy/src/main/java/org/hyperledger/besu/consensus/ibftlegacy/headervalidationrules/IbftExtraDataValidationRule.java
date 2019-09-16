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
package org.hyperledger.besu.consensus.ibftlegacy.headervalidationrules;

import org.hyperledger.besu.consensus.common.ValidatorProvider;
import org.hyperledger.besu.consensus.ibft.IbftContext;
import org.hyperledger.besu.consensus.ibftlegacy.IbftBlockHashing;
import org.hyperledger.besu.consensus.ibftlegacy.IbftExtraData;
import org.hyperledger.besu.consensus.ibftlegacy.IbftHelpers;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.AttachedBlockHeaderValidationRule;
import org.hyperledger.besu.ethereum.rlp.RLPException;

import java.util.Collection;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import com.google.common.collect.Iterables;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Ensures the byte content of the extraData field can be deserialised into an appropriate
 * structure, and that the structure created contains data matching expectations from preceding
 * blocks.
 */
public class IbftExtraDataValidationRule implements AttachedBlockHeaderValidationRule<IbftContext> {

  private static final Logger LOG = LogManager.getLogger();

  private final boolean validateCommitSeals;

  public IbftExtraDataValidationRule(final boolean validateCommitSeals) {
    this.validateCommitSeals = validateCommitSeals;
  }

  @Override
  public boolean validate(
      final BlockHeader header,
      final BlockHeader parent,
      final ProtocolContext<IbftContext> context) {
    try {
      final ValidatorProvider validatorProvider =
          context.getConsensusState().getVoteTallyCache().getVoteTallyAfterBlock(parent);
      final IbftExtraData ibftExtraData = IbftExtraData.decode(header);

      final Address proposer = IbftBlockHashing.recoverProposerAddress(header, ibftExtraData);

      final Collection<Address> storedValidators = validatorProvider.getValidators();

      if (!storedValidators.contains(proposer)) {
        LOG.trace("Proposer sealing block is not a member of the validators.");
        return false;
      }

      if (validateCommitSeals) {
        final List<Address> committers =
            IbftBlockHashing.recoverCommitterAddresses(header, ibftExtraData);
        if (!validateCommitters(committers, storedValidators)) {
          return false;
        }
      }

      final SortedSet<Address> sortedReportedValidators =
          new TreeSet<>(ibftExtraData.getValidators());

      if (!Iterables.elementsEqual(ibftExtraData.getValidators(), sortedReportedValidators)) {
        LOG.trace(
            "Validators are not sorted in ascending order. Expected {} but got {}.",
            sortedReportedValidators,
            ibftExtraData.getValidators());
        return false;
      }

      if (!Iterables.elementsEqual(ibftExtraData.getValidators(), storedValidators)) {
        LOG.trace(
            "Incorrect validators. Expected {} but got {}.",
            storedValidators,
            ibftExtraData.getValidators());
        return false;
      }

    } catch (final RLPException ex) {
      LOG.trace("ExtraData field was unable to be deserialised into an IBFT Struct.", ex);
      return false;
    } catch (final IllegalArgumentException ex) {
      LOG.trace("Failed to verify extra data", ex);
      return false;
    } catch (final RuntimeException ex) {
      LOG.trace("Failed to find validators at parent");
      return false;
    }

    return true;
  }

  private boolean validateCommitters(
      final Collection<Address> committers, final Collection<Address> storedValidators) {

    final int minimumSealsRequired =
        IbftHelpers.calculateRequiredValidatorQuorum(storedValidators.size());
    if (committers.size() < minimumSealsRequired) {
      LOG.trace(
          "Insufficient committers to seal block. (Required {}, received {})",
          minimumSealsRequired,
          committers.size());
      return false;
    }

    if (!storedValidators.containsAll(committers)) {
      LOG.trace("Not all committers are in the locally maintained validator list.");
      return false;
    }

    return true;
  }
}
