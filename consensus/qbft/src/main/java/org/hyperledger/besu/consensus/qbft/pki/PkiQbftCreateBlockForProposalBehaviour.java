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

import org.hyperledger.besu.consensus.common.bft.BftBlockHeaderFunctions;
import org.hyperledger.besu.consensus.common.bft.BftExtraData;
import org.hyperledger.besu.consensus.common.bft.BftExtraDataCodec;
import org.hyperledger.besu.consensus.qbft.statemachine.QbftRound.CreateBlockForProposalBehaviour;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.blockcreation.BlockCreator;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.pki.cms.CmsCreator;
import org.hyperledger.besu.pki.keystore.KeyStoreWrapper;

import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

public class PkiQbftCreateBlockForProposalBehaviour implements CreateBlockForProposalBehaviour {

  private static final Logger LOG = LogManager.getLogger();

  private final BlockCreator blockCreator;
  private final ProtocolContext protocolContext;
  private final BftExtraDataCodec bftExtraDataCodec;

  public PkiQbftCreateBlockForProposalBehaviour(
      final BlockCreator blockCreator,
      final ProtocolContext protocolContext,
      final BftExtraDataCodec bftExtraDataCodec) {
    this.blockCreator = blockCreator;
    this.protocolContext = protocolContext;
    this.bftExtraDataCodec = bftExtraDataCodec;
  }

  @Override
  public Block create(final long headerTimeStampSeconds) {
    final Block block = blockCreator.createBlock(headerTimeStampSeconds);

    /*
     If KeyStoreWrapper exists, we are operating in "PKI" mode. We need to add the "stamp" to the
     proposed block header
    */
    final PkiQbftContext pkiQbftContext = protocolContext.getConsensusState(PkiQbftContext.class);
    final PkiBlockCreationConfiguration pkiBlockCreationConfig =
        pkiQbftContext.getPkiBlockCreationConfiguration();
    return replaceCmsInBlock(block, pkiBlockCreationConfig);
  }

  private Block replaceCmsInBlock(
      final Block block, final PkiBlockCreationConfiguration pkiBlockCreationConfig) {
    final KeyStoreWrapper keyStore = pkiBlockCreationConfig.getKeyStore();
    final String certificateAlias = pkiBlockCreationConfig.getCertificateAlias();
    final CmsCreator cmsCreator = new CmsCreator(keyStore, certificateAlias);

    final BlockHeaderFunctions blockHeaderFunctions =
        BftBlockHeaderFunctions.forOnChainBlock(bftExtraDataCodec);
    final Hash hash = blockHeaderFunctions.hash(block.getHeader());

    LOG.info(">>> Creating CMS for block {}", hash);
    final Bytes cms = cmsCreator.create(hash);

    final BftExtraData prevExtraData = bftExtraDataCodec.decode(block.getHeader());
    final BftExtraData substituteExtraData =
        new PkiQbftExtraData(
            prevExtraData.getVanityData(),
            prevExtraData.getSeals(),
            prevExtraData.getVote(),
            prevExtraData.getRound(),
            prevExtraData.getValidators(),
            Optional.of(cms));

    final BlockHeaderBuilder headerBuilder = BlockHeaderBuilder.fromHeader(block.getHeader());
    headerBuilder
        .extraData(bftExtraDataCodec.encode(substituteExtraData))
        .blockHeaderFunctions(blockHeaderFunctions);
    final BlockHeader newHeader = headerBuilder.buildBlockHeader();

    return new Block(newHeader, block.getBody());
  }
}
