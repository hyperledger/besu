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
package org.hyperledger.besu.consensus.ibftlegacy.headervalidationrules;

import org.hyperledger.besu.consensus.common.bft.BftHelpers;
import org.hyperledger.besu.consensus.ibft.IbftLegacyContext;
import org.hyperledger.besu.consensus.ibftlegacy.IbftBlockHashing;
import org.hyperledger.besu.consensus.ibftlegacy.IbftExtraData;
import org.hyperledger.besu.consensus.ibftlegacy.IbftHelpers;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.AttachedBlockHeaderValidationRule;
import org.hyperledger.besu.ethereum.rlp.RLPException;

import java.util.Collection;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;

import com.google.common.collect.Iterables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ensures the byte content of the extraData field can be deserialised into an appropriate
 * structure, and that the structure created contains data matching expectations from preceding
 * blocks.
 */
public class IbftExtraDataValidationRule implements AttachedBlockHeaderValidationRule {

  private static final Logger LOG = LoggerFactory.getLogger(IbftExtraDataValidationRule.class);

  private final boolean validateCommitSeals;
  private final long ceil2nBy3Block;

  public IbftExtraDataValidationRule(final boolean validateCommitSeals, final long ceil2nBy3Block) {
    this.validateCommitSeals = validateCommitSeals;
    this.ceil2nBy3Block = ceil2nBy3Block;
  }

  @Override
  public boolean validate(
      final BlockHeader header, final BlockHeader parent, final ProtocolContext context) {
    try {
      final Collection<Address> storedValidators =
          context
              .getConsensusContext(IbftLegacyContext.class)
              .getValidatorProvider()
              .getValidatorsAfterBlock(parent);
      final IbftExtraData ibftExtraData = IbftExtraData.decode(header);

      final Address proposer = IbftBlockHashing.recoverProposerAddress(header, ibftExtraData);

      if (!storedValidators.contains(proposer)) {
        LOG.info("Invalid block header: Proposer sealing block is not a member of the validators.");
        return false;
      }

      if (validateCommitSeals) {
        final List<Address> committers =
            IbftBlockHashing.recoverCommitterAddresses(header, ibftExtraData);

        final int minimumSealsRequired =
            header.getNumber() < ceil2nBy3Block
                ? IbftHelpers.calculateRequiredValidatorQuorum(storedValidators.size())
                : BftHelpers.calculateRequiredValidatorQuorum(storedValidators.size());

        if (!validateCommitters(committers, storedValidators, minimumSealsRequired)) {
          return false;
        }
      }

      final NavigableSet<Address> sortedReportedValidators =
          new TreeSet<>(ibftExtraData.getValidators());

      if (!Iterables.elementsEqual(ibftExtraData.getValidators(), sortedReportedValidators)) {
        LOG.info(
            "Invalid block header: Validators are not sorted in ascending order. Expected {} but got {}.",
            sortedReportedValidators,
            ibftExtraData.getValidators());
        return false;
      }

      if (!Iterables.elementsEqual(ibftExtraData.getValidators(), storedValidators)) {
        LOG.info(
            "Invalid block header: Incorrect validators. Expected {} but got {}.",
            storedValidators,
            ibftExtraData.getValidators());
        return false;
      }

    } catch (final RLPException ex) {
      LOG.info(
          "Invalid block header: ExtraData field was unable to be deserialised into an IBFT Struct.",
          ex);
      return false;
    } catch (final IllegalArgumentException ex) {
      LOG.info("Invalid block header: Failed to verify extra data", ex);
      return false;
    } catch (final RuntimeException ex) {
      LOG.info("Invalid block header: Failed to find validators at parent");
      return false;
    }

    return true;
  }

  private boolean validateCommitters(
      final Collection<Address> committers,
      final Collection<Address> storedValidators,
      final int minimumSealsRequired) {
    if (committers.size() < minimumSealsRequired) {
      LOG.info(
          "Invalid block header: Insufficient committers to seal block. (Required {}, received {})",
          minimumSealsRequired,
          committers.size());
      return false;
    }

    if (!storedValidators.containsAll(committers)) {
      LOG.info(
          "Invalid block header: Not all committers are in the locally maintained validator list.");
      return false;
    }

    return true;
  }
}
