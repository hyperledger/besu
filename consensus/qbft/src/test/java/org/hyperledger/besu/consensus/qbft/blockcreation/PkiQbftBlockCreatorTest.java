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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hyperledger.besu.consensus.common.bft.BftExtraDataFixture.createExtraData;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.common.bft.BftBlockHeaderFunctions;
import org.hyperledger.besu.consensus.common.bft.BftExtraData;
import org.hyperledger.besu.consensus.common.bft.BftProtocolSchedule;
import org.hyperledger.besu.consensus.qbft.QbftExtraDataCodec;
import org.hyperledger.besu.consensus.qbft.pki.PkiQbftBlockHeaderFunctions;
import org.hyperledger.besu.consensus.qbft.pki.PkiQbftExtraData;
import org.hyperledger.besu.consensus.qbft.pki.PkiQbftExtraDataCodec;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.blockcreation.BlockCreator;
import org.hyperledger.besu.ethereum.blockcreation.BlockCreator.BlockCreationResult;
import org.hyperledger.besu.ethereum.blockcreation.txselection.TransactionSelectionResults;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.WithdrawalsValidator;
import org.hyperledger.besu.pki.cms.CmsCreator;

import java.util.Collections;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PkiQbftBlockCreatorTest {

  private final PkiQbftExtraDataCodec extraDataCodec = new PkiQbftExtraDataCodec();

  private BlockCreator blockCreator;
  private CmsCreator cmsCreator;
  private PkiQbftBlockCreator pkiQbftBlockCreator;
  private BlockHeaderTestFixture blockHeaderBuilder;
  private BlockHeader blockHeader;
  private BftProtocolSchedule protocolSchedule;
  private ProtocolSpec protocolSpec;

  @BeforeEach
  public void before() {
    blockCreator = mock(BlockCreator.class);
    cmsCreator = mock(CmsCreator.class);
    blockHeader = mock(BlockHeader.class);
    protocolSchedule = mock(BftProtocolSchedule.class);
    protocolSpec = mock(ProtocolSpec.class);

    pkiQbftBlockCreator =
        new PkiQbftBlockCreator(
            blockCreator, cmsCreator, extraDataCodec, blockHeader, protocolSchedule);

    blockHeaderBuilder = new BlockHeaderTestFixture();

    when(protocolSchedule.getByBlockNumberOrTimestamp(anyLong(), anyLong()))
        .thenReturn(protocolSpec);
  }

  @Test
  public void createProposalBehaviourWithNonPkiCodecFails() {
    assertThatThrownBy(
            () ->
                new PkiQbftBlockCreator(
                    blockCreator,
                    cmsCreator,
                    new QbftExtraDataCodec(),
                    blockHeader,
                    protocolSchedule))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("PkiQbftBlockCreator must use PkiQbftExtraDataCodec");
  }

  @Test
  public void cmsInProposedBlockHasValueCreatedByCmsCreator() {
    createBlockBeingProposed();
    when(protocolSpec.getWithdrawalsValidator())
        .thenReturn(new WithdrawalsValidator.AllowedWithdrawals());

    final Bytes cms = Bytes.random(32);
    when(cmsCreator.create(any(Bytes.class))).thenReturn(cms);

    final Block proposedBlock = pkiQbftBlockCreator.createBlock(1L).getBlock();

    final PkiQbftExtraData proposedBlockExtraData =
        (PkiQbftExtraData) extraDataCodec.decodeRaw(proposedBlock.getHeader().getExtraData());
    assertThat(proposedBlockExtraData).isInstanceOf(PkiQbftExtraData.class);
    assertThat(proposedBlockExtraData.getCms()).isEqualTo(cms);
  }

  @Test
  public void cmsIsCreatedWithCorrectHashingFunction() {
    when(protocolSpec.getWithdrawalsValidator())
        .thenReturn(new WithdrawalsValidator.ProhibitedWithdrawals());
    final Block block = createBlockBeingProposed();
    final Hash expectedHashForCmsCreation =
        PkiQbftBlockHeaderFunctions.forCmsSignature(extraDataCodec).hash(block.getHeader());

    when(cmsCreator.create(any(Bytes.class))).thenReturn(Bytes.random(32));

    pkiQbftBlockCreator.createBlock(1L);

    verify(cmsCreator).create(eq(expectedHashForCmsCreation));
  }

  @Test
  public void proposedBlockHashUsesCommittedSealHeaderFunction() {
    createBlockBeingProposed();
    when(cmsCreator.create(any(Bytes.class))).thenReturn(Bytes.random(32));

    final Block blockWithCms = pkiQbftBlockCreator.createBlock(1L).getBlock();

    final Hash expectedBlockHash =
        BftBlockHeaderFunctions.forCommittedSeal(extraDataCodec).hash(blockWithCms.getHeader());

    assertThat(blockWithCms.getHash()).isEqualTo(expectedBlockHash);
  }

  private Block createBlockBeingProposed() {
    final BftExtraData originalExtraData =
        createExtraData(blockHeaderBuilder.buildHeader(), extraDataCodec);
    final BlockHeader blockHeaderWithExtraData =
        blockHeaderBuilder.extraData(extraDataCodec.encode(originalExtraData)).buildHeader();
    final Block block =
        new Block(
            blockHeaderWithExtraData,
            new BlockBody(Collections.emptyList(), Collections.emptyList()));
    when(blockCreator.createBlock(eq(1L)))
        .thenReturn(new BlockCreationResult(block, new TransactionSelectionResults()));
    when(blockCreator.createEmptyWithdrawalsBlock(anyLong()))
        .thenReturn(new BlockCreationResult(block, new TransactionSelectionResults()));

    return block;
  }
}
