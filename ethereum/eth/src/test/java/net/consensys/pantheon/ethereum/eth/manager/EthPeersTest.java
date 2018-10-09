package net.consensys.pantheon.ethereum.eth.manager;

import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.pantheon.ethereum.testutil.BlockDataGenerator;
import net.consensys.pantheon.util.uint.UInt256;

import org.junit.Before;
import org.junit.Test;

public class EthPeersTest {

  private EthProtocolManager ethProtocolManager;
  private BlockDataGenerator gen;

  @Before
  public void setup() {
    gen = new BlockDataGenerator();
    ethProtocolManager = EthProtocolManagerTestUtil.create();
  }

  @Test
  public void comparesPeersWithHeightAndTd() {
    // Set peerA with better height, lower td
    final EthPeer peerA =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, UInt256.of(50), 0).getEthPeer();
    final EthPeer peerB =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, UInt256.of(100), 0).getEthPeer();
    peerA.chainState().update(gen.hash(), 20);
    peerB.chainState().update(gen.hash(), 10);

    // Sanity check
    assertThat(peerA.chainState().getEstimatedHeight()).isEqualTo(20);
    assertThat(peerB.chainState().getEstimatedHeight()).isEqualTo(10);

    assertThat(EthPeers.CHAIN_HEIGHT.compare(peerA, peerB)).isGreaterThan(0);
    assertThat(EthPeers.TOTAL_DIFFICULTY.compare(peerA, peerB)).isLessThan(0);

    assertThat(EthPeers.BEST_CHAIN.compare(peerA, peerB)).isGreaterThan(0);
    assertThat(EthPeers.BEST_CHAIN.compare(peerB, peerA)).isLessThan(0);
    assertThat(EthPeers.BEST_CHAIN.compare(peerA, peerA)).isEqualTo(0);
    assertThat(EthPeers.BEST_CHAIN.compare(peerB, peerB)).isEqualTo(0);
  }

  @Test
  public void comparesPeersWithTdAndNoHeight() {
    final EthPeer peerA =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, UInt256.of(100), 0).getEthPeer();
    final EthPeer peerB =
        EthProtocolManagerTestUtil.createPeer(ethProtocolManager, UInt256.of(50), 0).getEthPeer();

    // Sanity check
    assertThat(peerA.chainState().getEstimatedHeight()).isEqualTo(0);
    assertThat(peerB.chainState().getEstimatedHeight()).isEqualTo(0);

    assertThat(EthPeers.CHAIN_HEIGHT.compare(peerA, peerB)).isEqualTo(0);
    assertThat(EthPeers.TOTAL_DIFFICULTY.compare(peerA, peerB)).isGreaterThan(0);

    assertThat(EthPeers.BEST_CHAIN.compare(peerA, peerB)).isGreaterThan(0);
    assertThat(EthPeers.BEST_CHAIN.compare(peerB, peerA)).isLessThan(0);
    assertThat(EthPeers.BEST_CHAIN.compare(peerA, peerA)).isEqualTo(0);
    assertThat(EthPeers.BEST_CHAIN.compare(peerB, peerB)).isEqualTo(0);
  }
}
