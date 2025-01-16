/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.consensus.qbft.adaptor;

import org.hyperledger.besu.consensus.common.bft.BftBlockHeaderFunctions;
import org.hyperledger.besu.consensus.common.bft.BftExtraData;
import org.hyperledger.besu.consensus.common.bft.BftExtraDataCodec;
import org.hyperledger.besu.consensus.qbft.core.api.ExtraDataProvider;
import org.hyperledger.besu.consensus.qbft.core.api.QbftBlock;
import org.hyperledger.besu.consensus.qbft.core.api.QbftBlockCreator;
import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;

import java.util.Collection;

public class QbftBlockCreatorImpl implements QbftBlockCreator {

  private final org.hyperledger.besu.ethereum.blockcreation.BlockCreator besuBlockCreator;
  private final BftExtraDataCodec bftExtraDataCodec;

  public QbftBlockCreatorImpl(
      final org.hyperledger.besu.ethereum.blockcreation.BlockCreator besuBftBlockCreator,
      final BftExtraDataCodec bftExtraDataCodec) {
    this.besuBlockCreator = besuBftBlockCreator;
    this.bftExtraDataCodec = bftExtraDataCodec;
  }

  @Override
  public QbftBlock createBlock(final long headerTimeStampSeconds, final BlockHeader parentHeader) {
    var block = besuBlockCreator.createBlock(headerTimeStampSeconds, parentHeader);
    return new QbftBlockImpl(block.getBlock().getHeader());
  }

  @Override
  public QbftBlock createSealedBlock(
      final ExtraDataProvider bftExtraDataProvider,
      final QbftBlock block,
      final int roundNumber,
      final Collection<SECPSignature> commitSeals) {
    final BlockHeader initialBesuHeader = BlockUtil.toBesuBlock(block).getHeader();
    BftExtraData initialExtraData = bftExtraDataProvider.getExtraData(initialBesuHeader);

    final BftExtraData sealedExtraData =
        new BftExtraData(
            initialExtraData.getVanityData(),
            commitSeals,
            initialExtraData.getVote(),
            roundNumber,
            initialExtraData.getValidators());

    final BlockHeader sealedHeader =
        BlockHeaderBuilder.fromHeader(initialBesuHeader)
            .extraData(bftExtraDataCodec.encode(sealedExtraData))
            .blockHeaderFunctions(BftBlockHeaderFunctions.forOnchainBlock(bftExtraDataCodec))
            .buildBlockHeader();

    return new QbftBlockImpl(sealedHeader);
  }
}
