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
package org.hyperledger.besu.consensus.clique;

import org.hyperledger.besu.consensus.clique.blockcreation.CliqueProposerSelector;
import org.hyperledger.besu.consensus.common.ValidatorProvider;
import org.hyperledger.besu.consensus.common.VoteTally;
import org.hyperledger.besu.consensus.common.VoteTallyCache;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;

import java.util.Collection;
import java.util.Comparator;

public class CliqueHelpers {

  public static Address getProposerOfBlock(final BlockHeader header) {
    final CliqueExtraData extraData = CliqueExtraData.decode(header);
    return extraData.getProposerAddress();
  }

  public static Address getProposerForBlockAfter(
      final BlockHeader parent, final VoteTallyCache voteTallyCache) {
    final CliqueProposerSelector proposerSelector = new CliqueProposerSelector(voteTallyCache);
    return proposerSelector.selectProposerForNextBlock(parent);
  }

  public static boolean isSigner(
      final Address candidate, final ProtocolContext protocolContext, final BlockHeader parent) {
    final VoteTally validatorProvider =
        protocolContext
            .getConsensusState(CliqueContext.class)
            .getVoteTallyCache()
            .getVoteTallyAfterBlock(parent);
    return validatorProvider.getValidators().contains(candidate);
  }

  public static boolean addressIsAllowedToProduceNextBlock(
      final Address candidate, final ProtocolContext protocolContext, final BlockHeader parent) {
    final VoteTally validatorProvider =
        protocolContext
            .getConsensusState(CliqueContext.class)
            .getVoteTallyCache()
            .getVoteTallyAfterBlock(parent);

    if (!isSigner(candidate, protocolContext, parent)) {
      return false;
    }

    final int minimumUnsignedPastBlocks = minimumBlocksSincePreviousSigning(validatorProvider);

    final Blockchain blockchain = protocolContext.getBlockchain();
    int unsignedBlockCount = 0;
    BlockHeader localParent = parent;

    while (unsignedBlockCount < minimumUnsignedPastBlocks) {

      if (localParent.getNumber() == 0) {
        return true;
      }

      final Address parentSigner = CliqueHelpers.getProposerOfBlock(localParent);
      if (parentSigner.equals(candidate)) {
        return false;
      }
      unsignedBlockCount++;

      localParent =
          blockchain
              .getBlockHeader(localParent.getParentHash())
              .orElseThrow(() -> new IllegalStateException("The block was on a orphaned chain."));
    }

    return true;
  }

  private static int minimumBlocksSincePreviousSigning(final ValidatorProvider validatorProvider) {
    final int validatorCount = validatorProvider.getValidators().size();
    // The number of contiguous blocks in which a signer may only sign 1 (as taken from clique spec)
    final int signerLimit = (validatorCount / 2) + 1;
    return signerLimit - 1;
  }

  public static void installCliqueBlockChoiceRule(
      final Blockchain blockchain, final CliqueContext cliqueContext) {
    blockchain.setBlockChoiceRule(
        blockchain
            // most total difficulty
            .getBlockChoiceRule()
            // shortest chain
            .thenComparing(Comparator.comparing(ProcessableBlockHeader::getNumber).reversed())
            // proposer with least recent in-turn block
            .thenComparing(
                Comparator.comparing(
                    (BlockHeader header) -> distanceFromInTurn(header, cliqueContext)))
            // last resort: blockhash as uint256.
            .thenComparing(Comparator.comparing(BlockHeader::getHash).reversed()));
  }

  static int distanceFromInTurn(final BlockHeader header, final CliqueContext context) {
    final Address proposer = CliqueHelpers.getProposerOfBlock(header);
    final Collection<Address> validators =
        context.getVoteTallyCache().getVoteTallyAfterBlock(header).getValidators();

    // validators is an ordered collection, but as it is a tree set the only way to get an "index"
    // is to walk the tree.
    int index = 0;
    for (final Address validator : validators) {
      if (validator.equals(proposer)) {
        break;
      }
      index++;
    }

    return (int) (header.getNumber() - index) % validators.size();
  }
}
