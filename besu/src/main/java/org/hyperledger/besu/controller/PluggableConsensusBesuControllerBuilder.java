/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.controller;

import org.hyperledger.besu.ethereum.ConsensusContext;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.methods.JsonRpcMethods;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;
import org.hyperledger.besu.ethereum.blockcreation.NoopMiningCoordinator;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.eth.EthProtocol;
import org.hyperledger.besu.ethereum.eth.SnapProtocol;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.manager.snap.SnapProtocolManager;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.p2p.config.SubProtocolConfiguration;
import org.hyperledger.besu.ethereum.p2p.network.ProtocolManager;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.SubProtocol;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.plugin.data.PluginProtocolManager;
import org.hyperledger.besu.plugin.data.PluginSubProtocol;
import org.hyperledger.besu.plugin.data.PluginConsensusContext;
import org.hyperledger.besu.plugin.data.PluginProtocolSchedule;
import org.hyperledger.besu.plugin.services.mining.ConsensusComponentFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.Optional;

/** The Plugin Besu controller builder. */
public class PluggableConsensusBesuControllerBuilder extends BesuControllerBuilder {

  private static final Logger LOG = LoggerFactory.getLogger(PluggableConsensusBesuControllerBuilder.class);
  private static final BigInteger DEFAULT_CHAIN_ID = BigInteger.ONE;
  private   ConsensusComponentFactory consensusComponentFactory;

  /** Default Constructor. */
  public PluggableConsensusBesuControllerBuilder() {}

  @Override
  public void prepForBuild() {
    consensusComponentFactory.prepForBuild();
  }

  @Override
  public JsonRpcMethods createAdditionalJsonRpcMethodFactory(
      final ProtocolContext protocolContext,
      final ProtocolSchedule protocolSchedule,
      final MiningConfiguration miningConfiguration) {

    return null;
  }

  @Override
  public SubProtocolConfiguration createSubProtocolConfiguration(
      final EthProtocolManager ethProtocolManager,
      final Optional<SnapProtocolManager> maybeSnapProtocolManager) {
    PluginProtocolManager pluginProtocolManager = consensusComponentFactory.createProtocolManager();
    ProtocolManager protocolManager = ProtocolManagerAdapter.create(pluginProtocolManager);
    PluginSubProtocol pluginSubProtocol =
        consensusComponentFactory.createSubProtocol();
    SubProtocol subProtocol =
        SubProtocolAdapter.create(pluginSubProtocol);

    final SubProtocolConfiguration subProtocolConfiguration =
            new SubProtocolConfiguration()
                    .withSubProtocol(EthProtocol.get(), ethProtocolManager)
                    .withSubProtocol(subProtocol, protocolManager);
    maybeSnapProtocolManager.ifPresent(
            snapProtocolManager ->
                    subProtocolConfiguration.withSubProtocol(SnapProtocol.get(), snapProtocolManager));
    return subProtocolConfiguration;
  }

  @Override
  public MiningCoordinator createMiningCoordinator(
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final TransactionPool transactionPool,
      final MiningConfiguration miningConfiguration,
      final SyncState syncState,
      final EthProtocolManager ethProtocolManager) {
    return new NoopMiningCoordinator(miningConfiguration);
  }

  @Override
  public PluginServiceFactory createAdditionalPluginServices(
      final Blockchain blockchain, final ProtocolContext protocolContext) {
    return null;
  }

  @Override
  public ProtocolSchedule createProtocolSchedule() {
    PluginProtocolSchedule pluginProtocolSchedule = consensusComponentFactory.createProtocolSchedule();
    return ProtocolScheduleAdapter.create(pluginProtocolSchedule);

  }

  @Override
  public void validateContext(final ProtocolContext context) {}

  @Override
  public ConsensusContext createConsensusContext(
      final Blockchain blockchain,
      final WorldStateArchive worldStateArchive,
      final ProtocolSchedule protocolSchedule) {
     PluginConsensusContext pluginConsensusContext = consensusComponentFactory.createConsensusContext();
    return ConsensusContextAdapter.create(pluginConsensusContext);
  }
}
