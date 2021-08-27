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
import org.hyperledger.besu.consensus.qbft.statemachine.CreateBlockForProposalBehaviour;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.blockcreation.BlockCreator;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
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
  private final PkiQbftExtraDataCodec bftExtraDataCodec;

  public PkiQbftCreateBlockForProposalBehaviour(
      final BlockCreator blockCreator,
      final ProtocolContext protocolContext,
      final BftExtraDataCodec bftExtraDataCodec) {
    this.blockCreator = blockCreator;
    this.protocolContext = protocolContext;
    assert (bftExtraDataCodec instanceof PkiQbftExtraDataCodec);
    this.bftExtraDataCodec = (PkiQbftExtraDataCodec) bftExtraDataCodec;
  }

  @Override
  public Block create(final long headerTimeStampSeconds) {
    final Block block = blockCreator.createBlock(headerTimeStampSeconds);
    final QbftContext qbftContext = protocolContext.getConsensusState(QbftContext.class);
    final Optional<PkiBlockCreationConfiguration> pkiBlockCreationConfig =
        qbftContext.getPkiBlockCreationConfiguration();

    if (pkiBlockCreationConfig.isEmpty()) {
      throw new IllegalStateException(
          "Empty PkiBlockCreationConfiguration when create proposal in PKI-mode");
    }

    return replaceCmsInBlock(block, pkiBlockCreationConfig.get());
  }

  private Block replaceCmsInBlock(
      final Block block, final PkiBlockCreationConfiguration pkiBlockCreationConfig) {
    final KeyStoreWrapper keyStore = pkiBlockCreationConfig.getKeyStore();
    final String certificateAlias = pkiBlockCreationConfig.getCertificateAlias();
    final CmsCreator cmsCreator = new CmsCreator(keyStore, certificateAlias);

    final Hash hashWithoutCms =
        PkiQbftBlockHeaderFunctions.forCmsSignature(bftExtraDataCodec).hash(block.getHeader());

    final Bytes cms = cmsCreator.create(hashWithoutCms);

    final BftExtraData previousExtraData = bftExtraDataCodec.decode(block.getHeader());
    final BftExtraData substituteExtraData = new PkiQbftExtraData(previousExtraData, cms);
    final Bytes substituteExtraDataBytes = bftExtraDataCodec.encode(substituteExtraData);

    final BlockHeaderBuilder headerBuilder = BlockHeaderBuilder.fromHeader(block.getHeader());
    headerBuilder
        .extraData(substituteExtraDataBytes)
        .blockHeaderFunctions(BftBlockHeaderFunctions.forCommittedSeal(bftExtraDataCodec));
    final BlockHeader newHeader = headerBuilder.buildBlockHeader();

    LOG.debug("Created CMS with signed hash {} for block {}", hashWithoutCms, block.getHash());

    return new Block(newHeader, block.getBody());
  }
}
