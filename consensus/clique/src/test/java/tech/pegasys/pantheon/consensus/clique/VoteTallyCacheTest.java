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
package tech.pegasys.pantheon.consensus.clique;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.pantheon.consensus.common.VoteType.DROP;
import static tech.pegasys.pantheon.ethereum.core.InMemoryStorageProvider.createInMemoryBlockchain;

import tech.pegasys.pantheon.consensus.common.EpochManager;
import tech.pegasys.pantheon.consensus.common.ValidatorVote;
import tech.pegasys.pantheon.consensus.common.VoteTally;
import tech.pegasys.pantheon.consensus.common.VoteTallyUpdater;
import tech.pegasys.pantheon.crypto.SECP256K1.Signature;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.AddressHelpers;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockBody;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockHeaderTestFixture;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.google.common.util.concurrent.UncheckedExecutionException;
import org.assertj.core.util.Lists;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class VoteTallyCacheTest {

  BlockHeaderTestFixture headerBuilder = new BlockHeaderTestFixture();

  private Block createEmptyBlock(final long blockNumber, final Hash parentHash) {
    headerBuilder.number(blockNumber).parentHash(parentHash).coinbase(AddressHelpers.ofValue(0));
    return new Block(
        headerBuilder.buildHeader(), new BlockBody(Lists.emptyList(), Lists.emptyList()));
  }

  MutableBlockchain blockChain;
  private Block genesisBlock;
  private Block block_1;
  private Block block_2;

  private final List<Address> validators = Lists.newArrayList();

  @Before
  public void constructThreeBlockChain() {
    for (int i = 0; i < 3; i++) {
      validators.add(AddressHelpers.ofValue(i));
    }
    headerBuilder.extraData(
        new CliqueExtraData(
                BytesValue.wrap(new byte[32]),
                Signature.create(BigInteger.TEN, BigInteger.TEN, (byte) 1),
                validators)
            .encode());

    genesisBlock = createEmptyBlock(0, Hash.ZERO);

    blockChain = createInMemoryBlockchain(genesisBlock);

    block_1 = createEmptyBlock(1, genesisBlock.getHeader().getHash());
    block_2 = createEmptyBlock(1, block_1.getHeader().getHash());

    blockChain.appendBlock(block_1, Lists.emptyList());
    blockChain.appendBlock(block_2, Lists.emptyList());
  }

  @Test
  public void parentBlockVoteTallysAreCachedWhenChildVoteTallyRequested() {
    final VoteTallyUpdater tallyUpdater = mock(VoteTallyUpdater.class);
    final VoteTallyCache cache =
        new VoteTallyCache(blockChain, tallyUpdater, new EpochManager(30_000));

    // The votetallyUpdater should be invoked for the requested block, and all parents including
    // the epoch (genesis) block.
    final ArgumentCaptor<BlockHeader> varArgs = ArgumentCaptor.forClass(BlockHeader.class);
    cache.getVoteTallyAfterBlock(block_2.getHeader());
    verify(tallyUpdater, times(3)).updateForBlock(varArgs.capture(), any());
    assertThat(varArgs.getAllValues())
        .isEqualTo(
            Arrays.asList(genesisBlock.getHeader(), block_1.getHeader(), block_2.getHeader()));

    reset(tallyUpdater);

    // Requesting the vote tally to the parent block should not invoke the voteTallyUpdater as the
    // vote tally was cached from previous operation.
    cache.getVoteTallyAfterBlock(block_1.getHeader());
    verifyZeroInteractions(tallyUpdater);

    cache.getVoteTallyAfterBlock(block_2.getHeader());
    verifyZeroInteractions(tallyUpdater);
  }

  @Test
  public void exceptionThrownIfNoParentBlockExists() {
    final VoteTallyUpdater tallyUpdater = mock(VoteTallyUpdater.class);
    final VoteTallyCache cache =
        new VoteTallyCache(blockChain, tallyUpdater, new EpochManager(30_000));

    final Block orphanBlock = createEmptyBlock(4, Hash.ZERO);

    assertThatExceptionOfType(UncheckedExecutionException.class)
        .isThrownBy(() -> cache.getVoteTallyAfterBlock(orphanBlock.getHeader()))
        .withMessageContaining(
            "Supplied block was on a orphaned chain, unable to generate " + "VoteTally.");
  }

  @Test
  public void walkBackStopsWhenACachedVoteTallyIsFound() {
    final VoteTallyUpdater tallyUpdater = mock(VoteTallyUpdater.class);
    final VoteTallyCache cache =
        new VoteTallyCache(blockChain, tallyUpdater, new EpochManager(30_000));

    // Load the Cache up to block_2
    cache.getVoteTallyAfterBlock(block_2.getHeader());

    reset(tallyUpdater);

    // Append new blocks to the chain, and ensure the walkback only goes as far as block_2.
    final Block block_3 = createEmptyBlock(4, block_2.getHeader().getHash());
    // Load the Cache up to block_2
    cache.getVoteTallyAfterBlock(block_3.getHeader());

    // The votetallyUpdater should be invoked for the requested block, and all parents including
    // the epoch (genesis) block.
    final ArgumentCaptor<BlockHeader> varArgs = ArgumentCaptor.forClass(BlockHeader.class);
    verify(tallyUpdater, times(1)).updateForBlock(varArgs.capture(), any());
    assertThat(varArgs.getAllValues()).isEqualTo(Arrays.asList(block_3.getHeader()));
  }

  // A bug was identified in VoteTallyCache whereby a vote cast in the next block *could* be applied
  // to the parent block (depending on cache creation ordering). This test ensure the problem is
  // resolved.
  @Test
  public void integrationTestingVotesBeingApplied() {
    final EpochManager epochManager = new EpochManager(30_000);
    final CliqueBlockInterface blockInterface = mock(CliqueBlockInterface.class);
    final VoteTallyUpdater tallyUpdater = new VoteTallyUpdater(epochManager, blockInterface);

    when(blockInterface.extractVoteFromHeader(block_1.getHeader()))
        .thenReturn(Optional.of(new ValidatorVote(DROP, validators.get(0), validators.get(2))));

    when(blockInterface.extractVoteFromHeader(block_2.getHeader()))
        .thenReturn(Optional.of(new ValidatorVote(DROP, validators.get(1), validators.get(2))));

    final VoteTallyCache cache = new VoteTallyCache(blockChain, tallyUpdater, epochManager);

    VoteTally voteTally = cache.getVoteTallyAfterBlock(block_1.getHeader());
    assertThat(voteTally.getValidators()).containsAll(validators);

    voteTally = cache.getVoteTallyAfterBlock(block_2.getHeader());

    assertThat(voteTally.getValidators()).containsExactly(validators.get(0), validators.get(1));

    voteTally = cache.getVoteTallyAfterBlock(block_1.getHeader());
    assertThat(voteTally.getValidators()).containsAll(validators);
  }
}
