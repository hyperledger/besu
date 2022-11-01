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
package org.hyperledger.besu.consensus.clique.blockcreation;

import static com.google.common.base.Preconditions.checkState;

import org.hyperledger.besu.consensus.clique.CliqueBlockHashing;
import org.hyperledger.besu.consensus.clique.CliqueBlockInterface;
import org.hyperledger.besu.consensus.clique.CliqueContext;
import org.hyperledger.besu.consensus.clique.CliqueExtraData;
import org.hyperledger.besu.consensus.common.EpochManager;
import org.hyperledger.besu.consensus.common.validator.ValidatorVote;
import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.blockcreation.AbstractBlockCreator;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.core.SealableBlockHeader;
import org.hyperledger.besu.ethereum.core.Util;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPendingTransactionsSorter;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ScheduleBasedBlockHeaderFunctions;

import java.util.Optional;
import java.util.function.Supplier;

public class CliqueBlockCreator extends AbstractBlockCreator {

  private final NodeKey nodeKey;
  private final EpochManager epochManager;

  public CliqueBlockCreator(
      final Address coinbase,
      final Supplier<Optional<Long>> targetGasLimitSupplier,
      final ExtraDataCalculator extraDataCalculator,
      final AbstractPendingTransactionsSorter pendingTransactions,
      final ProtocolContext protocolContext,
      final ProtocolSchedule protocolSchedule,
      final NodeKey nodeKey,
      final Wei minTransactionGasPrice,
      final Double minBlockOccupancyRatio,
      final BlockHeader parentHeader,
      final EpochManager epochManager) {
    super(
        coinbase,
        __ -> Util.publicKeyToAddress(nodeKey.getPublicKey()),
        targetGasLimitSupplier,
        extraDataCalculator,
        pendingTransactions,
        protocolContext,
        protocolSchedule,
        minTransactionGasPrice,
        minBlockOccupancyRatio,
        parentHeader);
    this.nodeKey = nodeKey;
    this.epochManager = epochManager;
  }

  /**
   * Responsible for signing (hash of) the block (including MixHash and Nonce), and then injecting
   * the seal into the extraData. This is called after a suitable set of transactions have been
   * identified, and all resulting hashes have been inserted into the passed-in SealableBlockHeader.
   *
   * @param sealableBlockHeader A block header containing StateRoots, TransactionHashes etc.
   * @return The blockhead which is to be added to the block being proposed.
   */
  @Override
  protected BlockHeader createFinalBlockHeader(final SealableBlockHeader sealableBlockHeader) {
    final BlockHeaderFunctions blockHeaderFunctions =
        ScheduleBasedBlockHeaderFunctions.create(protocolSchedule);

    final BlockHeaderBuilder builder =
        BlockHeaderBuilder.create()
            .populateFrom(sealableBlockHeader)
            .mixHash(Hash.ZERO)
            .blockHeaderFunctions(blockHeaderFunctions);

    final Optional<ValidatorVote> vote = determineCliqueVote(sealableBlockHeader);
    final BlockHeaderBuilder builderIncludingProposedVotes =
        CliqueBlockInterface.createHeaderBuilderWithVoteHeaders(builder, vote);
    final CliqueExtraData sealedExtraData =
        constructSignedExtraData(builderIncludingProposedVotes.buildBlockHeader());

    // Replace the extraData in the BlockHeaderBuilder, and return header.
    return builderIncludingProposedVotes.extraData(sealedExtraData.encode()).buildBlockHeader();
  }

  private Optional<ValidatorVote> determineCliqueVote(
      final SealableBlockHeader sealableBlockHeader) {
    if (epochManager.isEpochBlock(sealableBlockHeader.getNumber())) {
      return Optional.empty();
    } else {
      final CliqueContext cliqueContext = protocolContext.getConsensusContext(CliqueContext.class);
      checkState(
          cliqueContext.getValidatorProvider().getVoteProviderAtHead().isPresent(),
          "Clique requires a vote provider");
      return cliqueContext
          .getValidatorProvider()
          .getVoteProviderAtHead()
          .get()
          .getVoteAfterBlock(parentHeader, Util.publicKeyToAddress(nodeKey.getPublicKey()));
    }
  }

  /**
   * Produces a CliqueExtraData object with a populated proposerSeal. The signature in the block is
   * generated from the Hash of the header (minus proposer and committer seals) and the nodeKeys.
   *
   * @param headerToSign An almost fully populated header (proposer and committer seals are empty)
   * @return Extra data containing the same vanity data and validators as extraData, however
   *     proposerSeal will also be populated.
   */
  private CliqueExtraData constructSignedExtraData(final BlockHeader headerToSign) {
    final CliqueExtraData extraData = CliqueExtraData.decode(headerToSign);
    final Hash hashToSign =
        CliqueBlockHashing.calculateDataHashForProposerSeal(headerToSign, extraData);
    return new CliqueExtraData(
        extraData.getVanityData(),
        nodeKey.sign(hashToSign),
        extraData.getValidators(),
        headerToSign);
  }
}
