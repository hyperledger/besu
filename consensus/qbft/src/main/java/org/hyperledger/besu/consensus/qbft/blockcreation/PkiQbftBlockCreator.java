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
package org.hyperledger.besu.consensus.qbft.blockcreation;

import static com.google.common.base.Preconditions.checkArgument;

import org.hyperledger.besu.consensus.common.bft.BftBlockHeaderFunctions;
import org.hyperledger.besu.consensus.common.bft.BftExtraData;
import org.hyperledger.besu.consensus.common.bft.BftExtraDataCodec;
import org.hyperledger.besu.consensus.common.bft.BftProtocolSchedule;
import org.hyperledger.besu.consensus.qbft.pki.PkiBlockCreationConfiguration;
import org.hyperledger.besu.consensus.qbft.pki.PkiQbftBlockHeaderFunctions;
import org.hyperledger.besu.consensus.qbft.pki.PkiQbftExtraData;
import org.hyperledger.besu.consensus.qbft.pki.PkiQbftExtraDataCodec;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.blockcreation.BlockCreator;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.WithdrawalsValidator;
import org.hyperledger.besu.pki.cms.CmsCreator;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The Pki qbft block creator. */
public class PkiQbftBlockCreator implements BlockCreator {

  private static final Logger LOG = LoggerFactory.getLogger(PkiQbftBlockCreator.class);

  private final BlockCreator blockCreator;
  private final PkiQbftExtraDataCodec pkiQbftExtraDataCodec;
  private final CmsCreator cmsCreator;
  private final BlockHeader parentHeader;
  private final ProtocolSchedule protocolSchedule;

  /**
   * Instantiates a new Pki qbft block creator.
   *
   * @param blockCreator the block creator
   * @param pkiBlockCreationConfiguration the pki block creation configuration
   * @param pkiQbftExtraDataCodec the pki qbft extra data codec
   * @param parentHeader the block header of the parent block
   * @param protocolSchedule the protocol schedule (the type of block can vary based on the current
   *     protocol spec)
   */
  public PkiQbftBlockCreator(
      final BlockCreator blockCreator,
      final PkiBlockCreationConfiguration pkiBlockCreationConfiguration,
      final BftExtraDataCodec pkiQbftExtraDataCodec,
      final BlockHeader parentHeader,
      final ProtocolSchedule protocolSchedule) {
    this(
        blockCreator,
        new CmsCreator(
            pkiBlockCreationConfiguration.getKeyStore(),
            pkiBlockCreationConfiguration.getCertificateAlias()),
        pkiQbftExtraDataCodec,
        parentHeader,
        protocolSchedule);
  }

  /**
   * Instantiates a new Pki qbft block creator.
   *
   * @param blockCreator the block creator
   * @param cmsCreator the cms creator
   * @param bftExtraDataCodec the bft extra data codec
   * @param parentHeader the block header of the parent block
   * @param protocolSchedule the protocol schedule (the type of block can vary based on the current
   *     protocol spec)
   */
  @VisibleForTesting
  public PkiQbftBlockCreator(
      final BlockCreator blockCreator,
      final CmsCreator cmsCreator,
      final BftExtraDataCodec bftExtraDataCodec,
      final BlockHeader parentHeader,
      final ProtocolSchedule protocolSchedule) {
    this.blockCreator = blockCreator;
    this.cmsCreator = cmsCreator;
    this.protocolSchedule = protocolSchedule;
    this.parentHeader = parentHeader;

    checkArgument(
        bftExtraDataCodec instanceof PkiQbftExtraDataCodec,
        "PkiQbftBlockCreator must use PkiQbftExtraDataCodec");
    this.pkiQbftExtraDataCodec = (PkiQbftExtraDataCodec) bftExtraDataCodec;
  }

  @Override
  public BlockCreationResult createBlock(final long timestamp) {
    ProtocolSpec protocolSpec =
        ((BftProtocolSchedule) protocolSchedule)
            .getByBlockNumberOrTimestamp(parentHeader.getNumber() + 1, timestamp);

    final BlockCreationResult blockCreationResult;
    if (protocolSpec.getWithdrawalsValidator() instanceof WithdrawalsValidator.AllowedWithdrawals) {
      blockCreationResult = blockCreator.createEmptyWithdrawalsBlock(timestamp);
    } else {
      blockCreationResult = blockCreator.createBlock(timestamp);
    }
    return replaceCmsInBlock(blockCreationResult);
  }

  @Override
  public BlockCreationResult createBlock(
      final List<Transaction> transactions, final List<BlockHeader> ommers, final long timestamp) {
    final BlockCreationResult blockCreationResult =
        blockCreator.createBlock(transactions, ommers, timestamp);
    return replaceCmsInBlock(blockCreationResult);
  }

  @Override
  public BlockCreationResult createBlock(
      final Optional<List<Transaction>> maybeTransactions,
      final Optional<List<BlockHeader>> maybeOmmers,
      final long timestamp) {
    return createBlock(
        maybeTransactions.orElse(Collections.emptyList()),
        maybeOmmers.orElse(Collections.emptyList()),
        timestamp);
  }

  @Override
  public BlockCreationResult createEmptyWithdrawalsBlock(final long timestamp) {
    final BlockCreationResult blockCreationResult =
        blockCreator.createEmptyWithdrawalsBlock(timestamp);
    return replaceCmsInBlock(blockCreationResult);
  }

  private BlockCreationResult replaceCmsInBlock(final BlockCreationResult blockCreationResult) {
    final Block block = blockCreationResult.getBlock();
    final BlockHeader blockHeader = block.getHeader();
    final Hash hashWithoutCms =
        PkiQbftBlockHeaderFunctions.forCmsSignature(pkiQbftExtraDataCodec).hash(block.getHeader());

    final Bytes cms = cmsCreator.create(hashWithoutCms);

    final BftExtraData previousExtraData = pkiQbftExtraDataCodec.decode(blockHeader);
    final BftExtraData substituteExtraData = new PkiQbftExtraData(previousExtraData, cms);
    final Bytes substituteExtraDataBytes = pkiQbftExtraDataCodec.encode(substituteExtraData);

    final BlockHeaderBuilder headerBuilder = BlockHeaderBuilder.fromHeader(blockHeader);
    headerBuilder
        .extraData(substituteExtraDataBytes)
        .blockHeaderFunctions(BftBlockHeaderFunctions.forCommittedSeal(pkiQbftExtraDataCodec));
    final BlockHeader newHeader = headerBuilder.buildBlockHeader();

    LOG.debug("Created CMS with signed hash {} for block {}", hashWithoutCms, newHeader.getHash());

    return new BlockCreationResult(
        new Block(newHeader, block.getBody()),
        blockCreationResult.getTransactionSelectionResults());
  }
}
