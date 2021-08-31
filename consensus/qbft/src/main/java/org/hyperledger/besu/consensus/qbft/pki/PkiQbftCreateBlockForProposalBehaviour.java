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

package org.hyperledger.besu.consensus.qbft.pki;

import static com.google.common.base.Preconditions.checkArgument;

import org.hyperledger.besu.consensus.common.bft.BftBlockHeaderFunctions;
import org.hyperledger.besu.consensus.common.bft.BftExtraData;
import org.hyperledger.besu.consensus.common.bft.BftExtraDataCodec;
import org.hyperledger.besu.consensus.qbft.statemachine.CreateBlockForProposalBehaviour;
import org.hyperledger.besu.ethereum.blockcreation.BlockCreator;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.pki.cms.CmsCreator;

import com.google.common.annotations.VisibleForTesting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

public class PkiQbftCreateBlockForProposalBehaviour implements CreateBlockForProposalBehaviour {

  private static final Logger LOG = LogManager.getLogger();

  private final BlockCreator blockCreator;
  private final CmsCreator cmsCreator;
  private final PkiQbftExtraDataCodec bftExtraDataCodec;

  public PkiQbftCreateBlockForProposalBehaviour(
      final BlockCreator blockCreator,
      final PkiBlockCreationConfiguration pkiBlockCreationConfiguration,
      final BftExtraDataCodec bftExtraDataCodec) {
    this(
        blockCreator,
        new CmsCreator(
            pkiBlockCreationConfiguration.getKeyStore(),
            pkiBlockCreationConfiguration.getCertificateAlias()),
        bftExtraDataCodec);
  }

  @VisibleForTesting
  PkiQbftCreateBlockForProposalBehaviour(
      final BlockCreator blockCreator,
      final CmsCreator cmsCreator,
      final BftExtraDataCodec bftExtraDataCodec) {
    this.blockCreator = blockCreator;
    this.cmsCreator = cmsCreator;

    checkArgument(
        bftExtraDataCodec instanceof PkiQbftExtraDataCodec,
        "PkiQbftCreateBlockForProposalBehaviour must use PkiQbftExtraDataCodec");
    this.bftExtraDataCodec = (PkiQbftExtraDataCodec) bftExtraDataCodec;
  }

  @Override
  public Block create(final long headerTimeStampSeconds) {
    final Block block = blockCreator.createBlock(headerTimeStampSeconds);
    return replaceCmsInBlock(block);
  }

  private Block replaceCmsInBlock(final Block block) {
    final BlockHeader blockHeader = block.getHeader();
    final Hash hashWithoutCms =
        PkiQbftBlockHeaderFunctions.forCmsSignature(bftExtraDataCodec).hash(block.getHeader());

    final Bytes cms = cmsCreator.create(hashWithoutCms);

    final BftExtraData previousExtraData = bftExtraDataCodec.decode(blockHeader);
    final BftExtraData substituteExtraData = new PkiQbftExtraData(previousExtraData, cms);
    final Bytes substituteExtraDataBytes = bftExtraDataCodec.encode(substituteExtraData);

    final BlockHeaderBuilder headerBuilder = BlockHeaderBuilder.fromHeader(blockHeader);
    headerBuilder
        .extraData(substituteExtraDataBytes)
        .blockHeaderFunctions(BftBlockHeaderFunctions.forCommittedSeal(bftExtraDataCodec));
    final BlockHeader newHeader = headerBuilder.buildBlockHeader();

    LOG.debug("Created CMS with signed hash {} for block {}", hashWithoutCms, newHeader.getHash());

    return new Block(newHeader, block.getBody());
  }
}
