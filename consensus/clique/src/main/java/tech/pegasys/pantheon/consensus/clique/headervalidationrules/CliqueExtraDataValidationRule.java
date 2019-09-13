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
package tech.pegasys.pantheon.consensus.clique.headervalidationrules;

import tech.pegasys.pantheon.consensus.clique.CliqueContext;
import tech.pegasys.pantheon.consensus.clique.CliqueExtraData;
import tech.pegasys.pantheon.consensus.common.EpochManager;
import tech.pegasys.pantheon.consensus.common.VoteTally;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.mainnet.AttachedBlockHeaderValidationRule;
import tech.pegasys.pantheon.ethereum.rlp.RLPException;

import java.util.Collection;

import com.google.common.collect.Iterables;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CliqueExtraDataValidationRule
    implements AttachedBlockHeaderValidationRule<CliqueContext> {

  private static final Logger LOG = LogManager.getLogger();

  private final EpochManager epochManager;

  public CliqueExtraDataValidationRule(final EpochManager epochManager) {
    this.epochManager = epochManager;
  }

  /**
   * Responsible for determining the validity of the extra data field. Ensures:
   *
   * <ul>
   *   <li>Bytes in the extra data field can be decoded as per Clique specification
   *   <li>Proposer (derived from the proposerSeal) is a member of the validators
   *   <li>Validators are only validated on epoch blocks.
   * </ul>
   *
   * @param header the block header containing the extraData to be validated.
   * @return True if the extraData successfully produces an CliqueExtraData object, false otherwise
   */
  @Override
  public boolean validate(
      final BlockHeader header,
      final BlockHeader parent,
      final ProtocolContext<CliqueContext> protocolContext) {
    try {
      final VoteTally validatorProvider =
          protocolContext.getConsensusState().getVoteTallyCache().getVoteTallyAfterBlock(parent);

      final Collection<Address> storedValidators = validatorProvider.getValidators();
      return extraDataIsValid(storedValidators, header);

    } catch (final RLPException ex) {
      LOG.trace("ExtraData field was unable to be deserialised into an Clique Struct.", ex);
      return false;
    } catch (final IllegalArgumentException ex) {
      LOG.trace("Failed to verify extra data", ex);
      return false;
    }
  }

  private boolean extraDataIsValid(
      final Collection<Address> expectedValidators, final BlockHeader header) {

    final CliqueExtraData cliqueExtraData = CliqueExtraData.decode(header);
    final Address proposer = cliqueExtraData.getProposerAddress();

    if (!expectedValidators.contains(proposer)) {
      LOG.trace("Proposer sealing block is not a member of the signers.");
      return false;
    }

    if (epochManager.isEpochBlock(header.getNumber())) {
      if (!Iterables.elementsEqual(cliqueExtraData.getValidators(), expectedValidators)) {
        LOG.trace(
            "Incorrect signers. Expected {} but got {}.",
            expectedValidators,
            cliqueExtraData.getValidators());
        return false;
      }
    } else {
      if (!cliqueExtraData.getValidators().isEmpty()) {
        LOG.trace("Signer list on non-epoch blocks must be empty.");
        return false;
      }
    }

    return true;
  }

  @Override
  public boolean includeInLightValidation() {
    return false;
  }
}
