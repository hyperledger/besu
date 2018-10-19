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
package tech.pegasys.pantheon.consensus.ibft;

import static java.util.Collections.emptyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.consensus.common.VoteProposer;
import tech.pegasys.pantheon.consensus.common.VoteTally;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockBody;
import tech.pegasys.pantheon.ethereum.core.BlockHeaderTestFixture;
import tech.pegasys.pantheon.ethereum.core.BlockImporter;
import tech.pegasys.pantheon.ethereum.db.WorldStateArchive;
import tech.pegasys.pantheon.ethereum.mainnet.HeaderValidationMode;

import org.junit.Test;

public class IbftBlockImporterTest {

  private final VoteTallyUpdater voteTallyUpdater = mock(VoteTallyUpdater.class);
  private final VoteTally voteTally = mock(VoteTally.class);
  private final VoteProposer voteProposer = mock(VoteProposer.class);

  @SuppressWarnings("unchecked")
  private final BlockImporter<IbftContext> delegate = mock(BlockImporter.class);

  private final MutableBlockchain blockchain = mock(MutableBlockchain.class);
  private final WorldStateArchive worldStateArchive = mock(WorldStateArchive.class);
  private final ProtocolContext<IbftContext> context =
      new ProtocolContext<>(
          blockchain, worldStateArchive, new IbftContext(voteTally, voteProposer));

  private final IbftBlockImporter importer = new IbftBlockImporter(delegate, voteTallyUpdater);

  @Test
  public void voteTallyNotUpdatedWhenBlockImportFails() {
    final BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();
    final Block block =
        new Block(headerBuilder.buildHeader(), new BlockBody(emptyList(), emptyList()));

    when(delegate.importBlock(context, block, HeaderValidationMode.FULL, HeaderValidationMode.FULL))
        .thenReturn(false);

    importer.importBlock(context, block, HeaderValidationMode.FULL);

    verifyZeroInteractions(voteTallyUpdater);
  }

  @Test
  public void voteTallyNotUpdatedWhenFastBlockImportFails() {
    final BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();
    final Block block =
        new Block(headerBuilder.buildHeader(), new BlockBody(emptyList(), emptyList()));

    when(delegate.fastImportBlock(context, block, emptyList(), HeaderValidationMode.LIGHT))
        .thenReturn(false);

    importer.fastImportBlock(context, block, emptyList(), HeaderValidationMode.LIGHT);

    verifyZeroInteractions(voteTallyUpdater);
  }

  @Test
  public void voteTallyUpdatedWhenBlockImportSucceeds() {
    final Block block =
        new Block(
            new BlockHeaderTestFixture().buildHeader(), new BlockBody(emptyList(), emptyList()));

    when(delegate.importBlock(context, block, HeaderValidationMode.FULL, HeaderValidationMode.FULL))
        .thenReturn(true);

    importer.importBlock(context, block, HeaderValidationMode.FULL);

    verify(voteTallyUpdater).updateForBlock(block.getHeader(), voteTally);
  }

  @Test
  public void voteTallyUpdatedWhenFastBlockImportSucceeds() {
    final Block block =
        new Block(
            new BlockHeaderTestFixture().buildHeader(), new BlockBody(emptyList(), emptyList()));

    when(delegate.fastImportBlock(context, block, emptyList(), HeaderValidationMode.LIGHT))
        .thenReturn(true);

    importer.fastImportBlock(context, block, emptyList(), HeaderValidationMode.LIGHT);

    verify(voteTallyUpdater).updateForBlock(block.getHeader(), voteTally);
  }
}
