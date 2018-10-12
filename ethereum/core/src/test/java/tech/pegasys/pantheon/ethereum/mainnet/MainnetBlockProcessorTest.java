package net.consensys.pantheon.ethereum.mainnet;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import net.consensys.pantheon.ethereum.chain.Blockchain;
import net.consensys.pantheon.ethereum.chain.GenesisConfig;
import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.core.Hash;
import net.consensys.pantheon.ethereum.core.MutableWorldState;
import net.consensys.pantheon.ethereum.core.Wei;
import net.consensys.pantheon.ethereum.mainnet.MainnetBlockProcessor.TransactionReceiptFactory;
import net.consensys.pantheon.ethereum.vm.TestBlockchain;
import net.consensys.pantheon.ethereum.vm.WorldStateMock;

import org.junit.Test;

public class MainnetBlockProcessorTest {

  private final TransactionProcessor transactionProcessor = mock(TransactionProcessor.class);
  private final TransactionReceiptFactory transactionReceiptFactory =
      mock(TransactionReceiptFactory.class);
  private final MainnetBlockProcessor blockProcessor =
      new MainnetBlockProcessor(
          transactionProcessor, transactionReceiptFactory, Wei.ZERO, BlockHeader::getCoinbase);

  @Test
  public void noAccountCreatedWhenBlockRewardIsZero() {
    final Blockchain blockchain = new TestBlockchain();

    final MutableWorldState worldState = WorldStateMock.create(emptyMap());
    final Hash initialHash = worldState.rootHash();

    final BlockHeader emptyBlockHeader = GenesisConfig.mainnet().getBlock().getHeader();
    blockProcessor.processBlock(blockchain, worldState, emptyBlockHeader, emptyList(), emptyList());

    // An empty block with 0 reward should not change the world state
    assertThat(worldState.rootHash()).isEqualTo(initialHash);
  }
}
