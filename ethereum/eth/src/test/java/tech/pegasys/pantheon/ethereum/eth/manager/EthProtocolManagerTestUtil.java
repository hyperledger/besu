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

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.pantheon.ethereum.core.InMemoryStorageProvider.createInMemoryBlockchain;
import static tech.pegasys.pantheon.ethereum.core.InMemoryStorageProvider.createInMemoryWorldStateArchive;

import tech.pegasys.pantheon.config.GenesisConfigFile;
import tech.pegasys.pantheon.ethereum.chain.Blockchain;
import tech.pegasys.pantheon.ethereum.chain.ChainHead;
import tech.pegasys.pantheon.ethereum.chain.GenesisState;
import tech.pegasys.pantheon.ethereum.eth.EthProtocol;
import tech.pegasys.pantheon.ethereum.eth.EthProtocolConfiguration;
import tech.pegasys.pantheon.ethereum.eth.manager.DeterministicEthScheduler.TimeoutPolicy;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.wire.DefaultMessage;
import tech.pegasys.pantheon.ethereum.p2p.rlpx.wire.MessageData;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateArchive;
import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.pantheon.testutil.TestClock;
import tech.pegasys.pantheon.util.uint.UInt256;

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
