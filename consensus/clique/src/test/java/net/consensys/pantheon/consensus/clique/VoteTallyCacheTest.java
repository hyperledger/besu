package net.consensys.pantheon.consensus.clique;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import net.consensys.pantheon.consensus.common.EpochManager;
import net.consensys.pantheon.crypto.SECP256K1.Signature;
import net.consensys.pantheon.ethereum.core.AddressHelpers;
import net.consensys.pantheon.ethereum.core.Block;
import net.consensys.pantheon.ethereum.core.BlockBody;
import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.core.BlockHeaderTestFixture;
import net.consensys.pantheon.ethereum.core.Hash;
import net.consensys.pantheon.ethereum.db.DefaultMutableBlockchain;
import net.consensys.pantheon.ethereum.mainnet.MainnetBlockHashFunction;
import net.consensys.pantheon.services.kvstore.InMemoryKeyValueStorage;
import net.consensys.pantheon.services.kvstore.KeyValueStorage;
import net.consensys.pantheon.util.bytes.BytesValue;

import java.math.BigInteger;
import java.util.Arrays;

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

  DefaultMutableBlockchain blockChain;
  private Block genesisBlock;
  private Block block_1;
  private Block block_2;

  @Before
  public void constructThreeBlockChain() {
    headerBuilder.extraData(
        new CliqueExtraData(
                BytesValue.wrap(new byte[32]),
                Signature.create(BigInteger.TEN, BigInteger.TEN, (byte) 1),
                Lists.emptyList())
            .encode());

    genesisBlock = createEmptyBlock(0, Hash.ZERO);
    final KeyValueStorage keyValueStorage = new InMemoryKeyValueStorage();

    blockChain =
        new DefaultMutableBlockchain(
            genesisBlock, keyValueStorage, MainnetBlockHashFunction::createHash);

    block_1 = createEmptyBlock(1, genesisBlock.getHeader().getHash());
    block_2 = createEmptyBlock(1, block_1.getHeader().getHash());

    blockChain.appendBlock(block_1, Lists.emptyList());
    blockChain.appendBlock(block_2, Lists.emptyList());
  }

  @Test
  public void parentBlockVoteTallysAreCachedWhenChildVoteTallyRequested() {
    final CliqueVoteTallyUpdater tallyUpdater = mock(CliqueVoteTallyUpdater.class);
    final VoteTallyCache cache =
        new VoteTallyCache(blockChain, tallyUpdater, new EpochManager(30_000));

    // The votetallyUpdater should be invoked for the requested block, and all parents including
    // the epoch (genesis) block.
    final ArgumentCaptor<BlockHeader> varArgs = ArgumentCaptor.forClass(BlockHeader.class);
    cache.getVoteTallyAtBlock(block_2.getHeader());
    verify(tallyUpdater, times(3)).updateForBlock(varArgs.capture(), any());
    assertThat(varArgs.getAllValues())
        .isEqualTo(
            Arrays.asList(genesisBlock.getHeader(), block_1.getHeader(), block_2.getHeader()));

    reset(tallyUpdater);

    // Requesting the vote tally to the parent block should not invoke the voteTallyUpdater as the
    // vote tally was cached from previous operation.
    cache.getVoteTallyAtBlock(block_1.getHeader());
    verifyZeroInteractions(tallyUpdater);

    cache.getVoteTallyAtBlock(block_2.getHeader());
    verifyZeroInteractions(tallyUpdater);
  }

  @Test
  public void exceptionThrownIfNoParentBlockExists() {
    final CliqueVoteTallyUpdater tallyUpdater = mock(CliqueVoteTallyUpdater.class);
    final VoteTallyCache cache =
        new VoteTallyCache(blockChain, tallyUpdater, new EpochManager(30_000));

    final Block orphanBlock = createEmptyBlock(4, Hash.ZERO);

    assertThatExceptionOfType(UncheckedExecutionException.class)
        .isThrownBy(() -> cache.getVoteTallyAtBlock(orphanBlock.getHeader()))
        .withMessageContaining(
            "Supplied block was on a orphaned chain, unable to generate " + "VoteTally.");
  }

  @Test
  public void walkBackStopsWhenACachedVoteTallyIsFound() {
    final CliqueVoteTallyUpdater tallyUpdater = mock(CliqueVoteTallyUpdater.class);
    final VoteTallyCache cache =
        new VoteTallyCache(blockChain, tallyUpdater, new EpochManager(30_000));

    // Load the Cache up to block_2
    cache.getVoteTallyAtBlock(block_2.getHeader());

    reset(tallyUpdater);

    // Append new blocks to the chain, and ensure the walkback only goes as far as block_2.
    final Block block_3 = createEmptyBlock(4, block_2.getHeader().getHash());
    // Load the Cache up to block_2
    cache.getVoteTallyAtBlock(block_3.getHeader());

    // The votetallyUpdater should be invoked for the requested block, and all parents including
    // the epoch (genesis) block.
    final ArgumentCaptor<BlockHeader> varArgs = ArgumentCaptor.forClass(BlockHeader.class);
    verify(tallyUpdater, times(1)).updateForBlock(varArgs.capture(), any());
    assertThat(varArgs.getAllValues()).isEqualTo(Arrays.asList(block_3.getHeader()));
  }
}
