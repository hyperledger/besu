package net.consensys.pantheon.ethereum.eth.transactions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import net.consensys.pantheon.ethereum.core.Transaction;
import net.consensys.pantheon.ethereum.eth.manager.EthPeer;
import net.consensys.pantheon.ethereum.testutil.BlockDataGenerator;

import com.google.common.collect.ImmutableSet;
import org.junit.Test;

public class PeerTransactionTrackerTest {

  private final EthPeer ethPeer1 = mock(EthPeer.class);
  private final EthPeer ethPeer2 = mock(EthPeer.class);
  private final BlockDataGenerator generator = new BlockDataGenerator();
  private final PeerTransactionTracker tracker = new PeerTransactionTracker();
  private final Transaction transaction1 = generator.transaction();
  private final Transaction transaction2 = generator.transaction();
  private final Transaction transaction3 = generator.transaction();

  @Test
  public void shouldTrackTransactionsToSendToPeer() {
    tracker.addToPeerSendQueue(ethPeer1, transaction1);
    tracker.addToPeerSendQueue(ethPeer1, transaction2);
    tracker.addToPeerSendQueue(ethPeer2, transaction3);

    assertThat(tracker.getEthPeersWithUnsentTransactions()).containsOnly(ethPeer1, ethPeer2);
    assertThat(tracker.claimTransactionsToSendToPeer(ethPeer1))
        .containsOnly(transaction1, transaction2);
    assertThat(tracker.claimTransactionsToSendToPeer(ethPeer2)).containsOnly(transaction3);
  }

  @Test
  public void shouldExcludeAlreadySeenTransactionsFromTransactionsToSend() {
    tracker.markTransactionsAsSeen(ethPeer1, ImmutableSet.of(transaction2));

    tracker.addToPeerSendQueue(ethPeer1, transaction1);
    tracker.addToPeerSendQueue(ethPeer1, transaction2);
    tracker.addToPeerSendQueue(ethPeer2, transaction3);

    assertThat(tracker.getEthPeersWithUnsentTransactions()).containsOnly(ethPeer1, ethPeer2);
    assertThat(tracker.claimTransactionsToSendToPeer(ethPeer1)).containsOnly(transaction1);
    assertThat(tracker.claimTransactionsToSendToPeer(ethPeer2)).containsOnly(transaction3);
  }

  @Test
  public void shouldExcludeAlreadySeenTransactionsAsACollectionFromTransactionsToSend() {
    tracker.markTransactionsAsSeen(ethPeer1, ImmutableSet.of(transaction1, transaction2));

    tracker.addToPeerSendQueue(ethPeer1, transaction1);
    tracker.addToPeerSendQueue(ethPeer1, transaction2);
    tracker.addToPeerSendQueue(ethPeer2, transaction3);

    assertThat(tracker.getEthPeersWithUnsentTransactions()).containsOnly(ethPeer2);
    assertThat(tracker.claimTransactionsToSendToPeer(ethPeer1)).isEmpty();
    assertThat(tracker.claimTransactionsToSendToPeer(ethPeer2)).containsOnly(transaction3);
  }

  @Test
  public void shouldClearDataWhenPeerDisconnects() {
    tracker.markTransactionsAsSeen(ethPeer1, ImmutableSet.of(transaction1));

    tracker.addToPeerSendQueue(ethPeer1, transaction2);
    tracker.addToPeerSendQueue(ethPeer2, transaction3);

    tracker.onDisconnect(ethPeer1);

    assertThat(tracker.getEthPeersWithUnsentTransactions()).containsOnly(ethPeer2);

    // Should have cleared data that ethPeer1 has already seen transaction1
    tracker.addToPeerSendQueue(ethPeer1, transaction1);

    assertThat(tracker.getEthPeersWithUnsentTransactions()).containsOnly(ethPeer1, ethPeer2);
    assertThat(tracker.claimTransactionsToSendToPeer(ethPeer1)).containsOnly(transaction1);
    assertThat(tracker.claimTransactionsToSendToPeer(ethPeer2)).containsOnly(transaction3);
  }
}
