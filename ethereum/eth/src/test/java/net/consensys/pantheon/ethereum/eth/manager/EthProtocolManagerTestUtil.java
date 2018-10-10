package net.consensys.pantheon.ethereum.eth.manager;

import net.consensys.pantheon.ethereum.chain.Blockchain;
import net.consensys.pantheon.ethereum.chain.ChainHead;
import net.consensys.pantheon.ethereum.chain.GenesisConfig;
import net.consensys.pantheon.ethereum.db.DefaultMutableBlockchain;
import net.consensys.pantheon.ethereum.eth.EthProtocol;
import net.consensys.pantheon.ethereum.eth.manager.DeterministicEthScheduler.TimeoutPolicy;
import net.consensys.pantheon.ethereum.mainnet.MainnetProtocolSchedule;
import net.consensys.pantheon.ethereum.mainnet.ScheduleBasedBlockHashFunction;
import net.consensys.pantheon.ethereum.p2p.api.MessageData;
import net.consensys.pantheon.ethereum.p2p.wire.DefaultMessage;
import net.consensys.pantheon.services.kvstore.InMemoryKeyValueStorage;
import net.consensys.pantheon.util.uint.UInt256;

public class EthProtocolManagerTestUtil {

  public static EthProtocolManager create(
      final Blockchain blockchain, final TimeoutPolicy timeoutPolicy) {
    final int networkId = 1;
    final EthScheduler ethScheduler = new DeterministicEthScheduler(timeoutPolicy);
    return new EthProtocolManager(
        blockchain, networkId, false, EthProtocolManager.DEFAULT_REQUEST_LIMIT, ethScheduler);
  }

  public static EthProtocolManager create(final Blockchain blockchain) {
    return create(blockchain, () -> false);
  }

  public static EthProtocolManager create() {
    final Blockchain blockchain =
        new DefaultMutableBlockchain(
            GenesisConfig.mainnet().getBlock(),
            new InMemoryKeyValueStorage(),
            ScheduleBasedBlockHashFunction.create(MainnetProtocolSchedule.create()));
    return create(blockchain);
  }

  public static void broadcastMessage(
      final EthProtocolManager ethProtocolManager,
      final RespondingEthPeer peer,
      final MessageData message) {
    ethProtocolManager.processMessage(
        EthProtocol.ETH63, new DefaultMessage(peer.getPeerConnection(), message));
  }

  public static RespondingEthPeer createPeer(
      final EthProtocolManager ethProtocolManager, final UInt256 td) {
    return RespondingEthPeer.create(ethProtocolManager, td);
  }

  public static RespondingEthPeer createPeer(
      final EthProtocolManager ethProtocolManager, final UInt256 td, final long estimatedHeight) {
    return RespondingEthPeer.create(ethProtocolManager, td, estimatedHeight);
  }

  public static RespondingEthPeer createPeer(final EthProtocolManager ethProtocolManager) {
    return RespondingEthPeer.create(ethProtocolManager, UInt256.of(1000L));
  }

  public static RespondingEthPeer createPeer(
      final EthProtocolManager ethProtocolManager, final long estimatedHeight) {
    return RespondingEthPeer.create(ethProtocolManager, UInt256.of(1000L), estimatedHeight);
  }

  public static RespondingEthPeer createPeer(
      final EthProtocolManager ethProtocolManager, final Blockchain blockchain) {
    final ChainHead head = blockchain.getChainHead();
    return RespondingEthPeer.create(
        ethProtocolManager,
        head.getHash(),
        head.getTotalDifficulty(),
        blockchain.getChainHeadBlockNumber());
  }
}
