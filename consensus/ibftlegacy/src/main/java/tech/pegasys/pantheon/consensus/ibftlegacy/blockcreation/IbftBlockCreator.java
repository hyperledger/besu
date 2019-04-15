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
package tech.pegasys.pantheon.consensus.ibftlegacy.blockcreation;

import tech.pegasys.pantheon.consensus.ibft.IbftContext;
import tech.pegasys.pantheon.consensus.ibftlegacy.IbftBlockHashing;
import tech.pegasys.pantheon.consensus.ibftlegacy.IbftExtraData;
import tech.pegasys.pantheon.consensus.ibftlegacy.IbftHelpers;
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

import java.util.function.Function;

/**
 * Responsible for producing a Block which conforms to IBFT validation rules (other than missing
 * commit seals). Transactions and associated Hashes (stateroot, receipts etc.) are loaded into the
 * Block in the base class as part of the transaction selection process.
 */
public class IbftBlockCreator extends AbstractBlockCreator<IbftContext> {

  private final KeyPair nodeKeys;

  public IbftBlockCreator(
      final Address coinbase,
      final ExtraDataCalculator extraDataCalculator,
      final PendingTransactions pendingTransactions,
      final ProtocolContext<IbftContext> protocolContext,
      final ProtocolSchedule<IbftContext> protocolSchedule,
      final Function<Long, Long> gasLimitCalculator,
      final KeyPair nodeKeys,
      final Wei minTransactionGasPrice,
      final BlockHeader parentHeader) {
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
            .mixHash(IbftHelpers.EXPECTED_MIX_HASH)
            .nonce(0)
            .blockHashFunction(blockHashFunction);

    final IbftExtraData sealedExtraData = constructSignedExtraData(builder.buildBlockHeader());

    // Replace the extraData in the BlockHeaderBuilder, and return header.
    return builder.extraData(sealedExtraData.encode()).buildBlockHeader();
  }

  /**
   * Produces an IbftExtraData object with a populated proposerSeal. The signature in the block is
   * generated from the Hash of the header (minus proposer and committer seals) and the nodeKeys.
   *
   * @param headerToSign An almost fully populated header (proposer and committer seals are empty)
   * @return Extra data containing the same vanity data and validators as extraData, however
   *     proposerSeal will also be populated.
   */
  private IbftExtraData constructSignedExtraData(final BlockHeader headerToSign) {
    final IbftExtraData extraData = IbftExtraData.decode(headerToSign.getExtraData());
    final Hash hashToSign =
        IbftBlockHashing.calculateDataHashForProposerSeal(headerToSign, extraData);
    return new IbftExtraData(
        extraData.getVanityData(),
        extraData.getSeals(),
        SECP256K1.sign(hashToSign, nodeKeys),
        extraData.getValidators());
  }
}
