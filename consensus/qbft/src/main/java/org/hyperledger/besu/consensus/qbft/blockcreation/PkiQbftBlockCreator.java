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
import org.hyperledger.besu.pki.cms.CmsCreator;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PkiQbftBlockCreator implements BlockCreator {
  private static final Logger LOG = LoggerFactory.getLogger(PkiQbftBlockCreator.class);

  private final BlockCreator blockCreator;
  private final PkiQbftExtraDataCodec pkiQbftExtraDataCodec;
  private final CmsCreator cmsCreator;

  public PkiQbftBlockCreator(
      final BlockCreator blockCreator,
      final PkiBlockCreationConfiguration pkiBlockCreationConfiguration,
      final BftExtraDataCodec pkiQbftExtraDataCodec) {
    this(
        blockCreator,
        new CmsCreator(
            pkiBlockCreationConfiguration.getKeyStore(),
            pkiBlockCreationConfiguration.getCertificateAlias()),
        pkiQbftExtraDataCodec);
  }

  @VisibleForTesting
  public PkiQbftBlockCreator(
      final BlockCreator blockCreator,
      final CmsCreator cmsCreator,
      final BftExtraDataCodec bftExtraDataCodec) {
    this.blockCreator = blockCreator;
    this.cmsCreator = cmsCreator;

    checkArgument(
        bftExtraDataCodec instanceof PkiQbftExtraDataCodec,
        "PkiQbftBlockCreator must use PkiQbftExtraDataCodec");
    this.pkiQbftExtraDataCodec = (PkiQbftExtraDataCodec) bftExtraDataCodec;
  }

  @Override
  public Block createBlock(final long timestamp) {
    final Block block = blockCreator.createBlock(timestamp);
    return replaceCmsInBlock(block);
  }

  @Override
  public Block createBlock(
      final List<Transaction> transactions, final List<BlockHeader> ommers, final long timestamp) {
    final Block block = blockCreator.createBlock(transactions, ommers, timestamp);
    return replaceCmsInBlock(block);
  }

  @Override
  public Block createBlock(
      final Optional<List<Transaction>> maybeTransactions,
      final Optional<List<BlockHeader>> maybeOmmers,
      final long timestamp) {
    return createBlock(
        maybeTransactions.orElse(Collections.emptyList()),
        maybeOmmers.orElse(Collections.emptyList()),
        timestamp);
  }

  private Block replaceCmsInBlock(final Block block) {
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

    return new Block(newHeader, block.getBody());
  }
}
