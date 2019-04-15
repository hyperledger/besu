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
package tech.pegasys.pantheon.consensus.clique.blockcreation;

import tech.pegasys.pantheon.consensus.clique.CliqueBlockHashing;
import tech.pegasys.pantheon.consensus.clique.CliqueBlockInterface;
import tech.pegasys.pantheon.consensus.clique.CliqueContext;
import tech.pegasys.pantheon.consensus.clique.CliqueExtraData;
import tech.pegasys.pantheon.consensus.common.EpochManager;
import tech.pegasys.pantheon.consensus.common.ValidatorVote;
import tech.pegasys.pantheon.consensus.common.VoteTally;
import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.blockcreation.AbstractBlockCreator;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.BlockHashFunction;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockHeaderBuilder;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.SealableBlockHeader;
import tech.pegasys.pantheon.ethereum.core.Util;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.eth.transactions.PendingTransactions;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ScheduleBasedBlockHashFunction;

import java.util.Optional;
import java.util.function.Function;

public class CliqueBlockCreator extends AbstractBlockCreator<CliqueContext> {

  private final KeyPair nodeKeys;
  private final EpochManager epochManager;

  public CliqueBlockCreator(
      final Address coinbase,
      final ExtraDataCalculator extraDataCalculator,
      final PendingTransactions pendingTransactions,
      final ProtocolContext<CliqueContext> protocolContext,
      final ProtocolSchedule<CliqueContext> protocolSchedule,
      final Function<Long, Long> gasLimitCalculator,
      final KeyPair nodeKeys,
      final Wei minTransactionGasPrice,
      final BlockHeader parentHeader,
      final EpochManager epochManager) {
    super(
        coinbase,
        extraDataCalculator,
        pendingTransactions,
        protocolContext,
        protocolSchedule,
        gasLimitCalculator,
        minTransactionGasPrice,
        Util.publicKeyToAddress(nodeKeys.getPublicKey()),
        parentHeader);
    this.nodeKeys = nodeKeys;
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
    final BlockHashFunction blockHashFunction =
        ScheduleBasedBlockHashFunction.create(protocolSchedule);

    final BlockHeaderBuilder builder =
        BlockHeaderBuilder.create()
            .populateFrom(sealableBlockHeader)
            .mixHash(Hash.ZERO)
            .blockHashFunction(blockHashFunction);

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
      final CliqueContext cliqueContext = protocolContext.getConsensusState();
      final VoteTally voteTally =
          cliqueContext.getVoteTallyCache().getVoteTallyAfterBlock(parentHeader);
      return cliqueContext
          .getVoteProposer()
          .getVote(Util.publicKeyToAddress(nodeKeys.getPublicKey()), voteTally);
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
    final CliqueExtraData extraData = CliqueExtraData.decode(headerToSign.getExtraData());
    final Hash hashToSign =
        CliqueBlockHashing.calculateDataHashForProposerSeal(headerToSign, extraData);
    return new CliqueExtraData(
        extraData.getVanityData(), SECP256K1.sign(hashToSign, nodeKeys), extraData.getValidators());
  }
}
