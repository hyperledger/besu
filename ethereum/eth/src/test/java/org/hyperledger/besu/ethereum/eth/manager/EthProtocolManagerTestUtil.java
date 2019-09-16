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
package org.hyperledger.besu.ethereum.eth.manager;

import static com.google.common.base.Preconditions.checkArgument;
import static org.hyperledger.besu.ethereum.core.InMemoryStorageProvider.createInMemoryBlockchain;
import static org.hyperledger.besu.ethereum.core.InMemoryStorageProvider.createInMemoryWorldStateArchive;

import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.ChainHead;
import org.hyperledger.besu.ethereum.chain.GenesisState;
import org.hyperledger.besu.ethereum.eth.EthProtocol;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.manager.DeterministicEthScheduler.TimeoutPolicy;
import org.hyperledger.besu.ethereum.mainnet.MainnetProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.DefaultMessage;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.testutil.TestClock;
import org.hyperledger.besu.util.uint.UInt256;

import java.math.BigInteger;
import java.util.OptionalLong;

public class EthProtocolManagerTestUtil {

  public static EthProtocolManager create(
      final Blockchain blockchain,
      final WorldStateArchive worldStateArchive,
      final TimeoutPolicy timeoutPolicy) {
    return create(blockchain, worldStateArchive, new DeterministicEthScheduler(timeoutPolicy));
  }

  public static EthProtocolManager create(
      final Blockchain blockchain,
      final WorldStateArchive worldStateArchive,
      final EthScheduler ethScheduler) {
    final BigInteger networkId = BigInteger.ONE;
    return new EthProtocolManager(
        blockchain,
        worldStateArchive,
        networkId,
        false,
        ethScheduler,
        EthProtocolConfiguration.defaultConfig(),
        TestClock.fixed(),
        new NoOpMetricsSystem());
  }

  public static EthProtocolManager create(
      final Blockchain blockchain, final WorldStateArchive worldStateArchive) {
    return create(blockchain, worldStateArchive, TimeoutPolicy.NEVER);
  }

  public static EthProtocolManager create(final EthScheduler ethScheduler) {
    final ProtocolSchedule<Void> protocolSchedule = MainnetProtocolSchedule.create();
    final GenesisConfigFile config = GenesisConfigFile.mainnet();
    final GenesisState genesisState = GenesisState.fromConfig(config, protocolSchedule);
    final Blockchain blockchain = createInMemoryBlockchain(genesisState.getBlock());
    final WorldStateArchive worldStateArchive = createInMemoryWorldStateArchive();
    return create(blockchain, worldStateArchive, ethScheduler);
  }

  public static EthProtocolManager create() {
    return create(TimeoutPolicy.NEVER);
  }

  public static EthProtocolManager create(final TimeoutPolicy timeoutPolicy) {
    return create(new DeterministicEthScheduler(timeoutPolicy));
  }

  // Utility to prevent scheduler from automatically running submitted tasks
  public static void disableEthSchedulerAutoRun(final EthProtocolManager ethProtocolManager) {
    final EthScheduler scheduler = ethProtocolManager.ethContext().getScheduler();
    checkArgument(
        scheduler instanceof DeterministicEthScheduler,
        "EthProtocolManager must be set up with "
            + DeterministicEthScheduler.class.getSimpleName()
            + " in order to disable auto run.");
    ((DeterministicEthScheduler) scheduler).disableAutoRun();
  }

  // Manually runs any pending tasks submitted to the EthScheduler
  // Works with {@code disableEthSchedulerAutoRun} - tasks will only be pending if
  // autoRun has been disabled.
  public static void runPendingFutures(final EthProtocolManager ethProtocolManager) {
    final EthScheduler scheduler = ethProtocolManager.ethContext().getScheduler();
    checkArgument(
        scheduler instanceof DeterministicEthScheduler,
        "EthProtocolManager must be set up with "
            + DeterministicEthScheduler.class.getSimpleName()
            + " in order to manually run pending futures.");
    ((DeterministicEthScheduler) scheduler).runPendingFutures();
  }

  /**
   * Gets the number of pending tasks submitted to the EthScheduler.
   *
   * <p>Works with {@code disableEthSchedulerAutoRun} - tasks will only be pending if autoRun has
   * been disabled.
   */
  public static long getPendingFuturesCount(final EthProtocolManager ethProtocolManager) {
    final EthScheduler scheduler = ethProtocolManager.ethContext().getScheduler();
    checkArgument(
        scheduler instanceof DeterministicEthScheduler,
        "EthProtocolManager must be set up with "
            + DeterministicEthScheduler.class.getSimpleName()
            + " in order to manually run pending futures.");
    return ((DeterministicEthScheduler) scheduler).getPendingFuturesCount();
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

  public static RespondingEthPeer createPeer(
      final EthProtocolManager ethProtocolManager,
      final UInt256 td,
      final OptionalLong estimatedHeight) {
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
