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

import org.hyperledger.besu.consensus.merge.MergeContext;
import org.hyperledger.besu.consensus.merge.MergeContextFactory;
import org.hyperledger.besu.consensus.merge.MergeProtocolSchedule;
import org.hyperledger.besu.consensus.merge.blockcreation.MergeCoordinator;
import org.hyperledger.besu.ethereum.BlockValidator;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

public class MergeBesuControllerBuilder extends BesuControllerBuilder {

  @Override
  protected MiningCoordinator createMiningCoordinator(
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext,
      final TransactionPool transactionPool,
      final MiningParameters miningParameters,
      final SyncState syncState,
      final EthProtocolManager ethProtocolManager) {

    // TODO: revisit this when totalDifficulty transition is defined.  Since ProtocolSchedule
    //  doesn't make sense here:
    BlockValidator blockValidator = protocolSchedule.getByBlockNumber(0).getBlockValidator();

    return new MergeCoordinator(
        protocolContext,
        protocolSchedule,
        transactionPool.getPendingTransactions(),
        miningParameters,
        blockValidator);
  }

  @Override
  protected ProtocolSchedule createProtocolSchedule() {
    return MergeProtocolSchedule.create(
        genesisConfig.getConfigOptions(genesisConfigOverrides),
        privacyParameters,
        isRevertReasonEnabled);
  }

  @Override
  protected MergeContext createConsensusContext(
      final Blockchain blockchain,
      final WorldStateArchive worldStateArchive,
      final ProtocolSchedule protocolSchedule) {
    return MergeContextFactory.get();
  }

  @Override
  protected PluginServiceFactory createAdditionalPluginServices(
      final Blockchain blockchain, final ProtocolContext protocolContext) {
    return new NoopPluginServiceFactory();
  }
}
