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
package tech.pegasys.pantheon.ethereum.eth.manager;

import static tech.pegasys.pantheon.ethereum.core.InMemoryTestFixture.createInMemoryBlockchain;

import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.chain.ChainHead;
import tech.pegasys.pantheon.ethereum.chain.GenesisConfig;
import tech.pegasys.pantheon.ethereum.eth.EthProtocol;
import tech.pegasys.pantheon.ethereum.eth.manager.DeterministicEthScheduler.TimeoutPolicy;
import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;
import tech.pegasys.pantheon.ethereum.p2p.wire.DefaultMessage;
import tech.pegasys.pantheon.util.uint.UInt256;

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
    final Blockchain blockchain = createInMemoryBlockchain(GenesisConfig.mainnet().getBlock());
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
