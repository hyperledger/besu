package tech.pegasys.pantheon.ethereum.eth.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import tech.pegasys.pantheon.ethereum.chain.GenesisConfig;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.eth.manager.ChainState;
import tech.pegasys.pantheon.ethereum.eth.manager.EthProtocolManager;
import tech.pegasys.pantheon.ethereum.eth.manager.EthProtocolManagerTestUtil;
import tech.pegasys.pantheon.ethereum.eth.manager.RespondingEthPeer;
import tech.pegasys.pantheon.ethereum.eth.manager.RespondingEthPeer.Responder;
import tech.pegasys.pantheon.ethereum.eth.manager.ethtaskutils.BlockchainSetupUtil;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.util.uint.UInt256;

import org.junit.Test;

public class ChainHeadTrackerTest {

  private final BlockchainSetupUtil<Void> blockchainSetupUtil = BlockchainSetupUtil.forTesting();
  private final MutableBlockchain blockchain = blockchainSetupUtil.getBlockchain();
  private final EthProtocolManager ethProtocolManager =
      EthProtocolManagerTestUtil.create(blockchain);
  private final RespondingEthPeer respondingPeer =
      RespondingEthPeer.create(
          ethProtocolManager,
          blockchain.getChainHeadHash(),
          blockchain.getChainHead().getTotalDifficulty(),
          0);
  private final ProtocolSchedule<Void> protocolSchedule =
      GenesisConfig.development().getProtocolSchedule();
  private final TrailingPeerLimiter trailingPeerLimiter = mock(TrailingPeerLimiter.class);
  private final ChainHeadTracker chainHeadTracker =
      new ChainHeadTracker(ethProtocolManager.ethContext(), protocolSchedule, trailingPeerLimiter);

  @Test
  public void shouldRequestHeaderChainHeadWhenNewPeerConnects() {
    final Responder responder =
        RespondingEthPeer.blockchainResponder(blockchainSetupUtil.getBlockchain());
    chainHeadTracker.onPeerConnected(respondingPeer.getEthPeer());

    assertThat(chainHeadState().getEstimatedHeight()).isZero();

    respondingPeer.respond(responder);

    assertThat(chainHeadState().getEstimatedHeight())
        .isEqualTo(blockchain.getChainHeadBlockNumber());
  }

  @Test
  public void shouldIgnoreHeadersIfChainHeadHasAlreadyBeenUpdatedWhileWaiting() {
    final Responder responder =
        RespondingEthPeer.blockchainResponder(blockchainSetupUtil.getBlockchain());
    chainHeadTracker.onPeerConnected(respondingPeer.getEthPeer());

    // Change the hash of the current known head
    respondingPeer.getEthPeer().chainState().statusReceived(Hash.EMPTY_TRIE_HASH, UInt256.ONE);

    respondingPeer.respond(responder);

    assertThat(chainHeadState().getEstimatedHeight()).isZero();
  }

  @Test
  public void shouldCheckTrialingPeerLimits() {
    final Responder responder =
        RespondingEthPeer.blockchainResponder(blockchainSetupUtil.getBlockchain());
    chainHeadTracker.onPeerConnected(respondingPeer.getEthPeer());

    assertThat(chainHeadState().getEstimatedHeight()).isZero();

    respondingPeer.respond(responder);

    assertThat(chainHeadState().getEstimatedHeight())
        .isEqualTo(blockchain.getChainHeadBlockNumber());
  }

  private ChainState chainHeadState() {
    return respondingPeer.getEthPeer().chainState();
  }
}
