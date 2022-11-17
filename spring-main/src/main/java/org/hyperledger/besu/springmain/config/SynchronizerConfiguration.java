/*
 *
 *  * Copyright Hyperledger Besu Contributors.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  * the License. You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 *  * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *  * specific language governing permissions and limitations under the License.
 *  *
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.hyperledger.besu.springmain.config;

import java.time.Clock;
import java.util.Optional;
import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.consensus.merge.MergeContext;
import org.hyperledger.besu.consensus.merge.UnverifiedForkchoiceSupplier;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Synchronizer;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager;
import org.hyperledger.besu.ethereum.eth.sync.DefaultSynchronizer;
import org.hyperledger.besu.ethereum.eth.sync.PivotBlockSelector;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.PivotSelectorFromFinalizedBlock;
import org.hyperledger.besu.ethereum.eth.sync.fastsync.PivotSelectorFromPeers;
import org.hyperledger.besu.ethereum.eth.sync.fullsync.SyncTerminationCondition;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.worldstate.Pruner;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.plugin.services.BesuConfiguration;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.springframework.context.annotation.Bean;

public class SynchronizerConfiguration {

    @Bean
    public Synchronizer synchronizer(
            final ProtocolSchedule protocolSchedule,
            final WorldStateStorage worldStateStorage,
            final ProtocolContext protocolContext,
            final Optional<Pruner> maybePruner,
            final EthContext ethContext,
            final SyncState syncState,
            final EthProtocolManager ethProtocolManager,
            final PivotBlockSelector pivotBlockSelector,
            org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration syncConfig,
            BesuConfiguration besuConfig,
            StorageProvider storageProvider,
            Clock clock,
            MetricsSystem metricsSystem,
            SyncTerminationCondition fullSyncTerminationCondition) {

        final DefaultSynchronizer toUse =
                new DefaultSynchronizer(
                        syncConfig,
                        protocolSchedule,
                        protocolContext,
                        worldStateStorage,
                        ethProtocolManager.getBlockBroadcaster(),
                        maybePruner,
                        ethContext,
                        syncState,
                        besuConfig.getDataPath(),
                        storageProvider,
                        clock,
                        metricsSystem,
                        fullSyncTerminationCondition,
                        pivotBlockSelector);

        return toUse;
    }

    @Bean
    public SyncTerminationCondition fullSyncTerminationCondition(GenesisConfigOptions genesisConfigOptions, MutableBlockchain blockchain) {
        return genesisConfigOptions
                .getTerminalTotalDifficulty()
                .map(difficulty -> SyncTerminationCondition.difficulty(difficulty, blockchain))
                .orElse(SyncTerminationCondition.never());

    }

    @Bean
    public PivotBlockSelector pivotBlockSelector(
            final ProtocolSchedule protocolSchedule,
            final ProtocolContext protocolContext,
            final EthContext ethContext,
            final SyncState syncState,
            final MetricsSystem metricsSystem,
            GenesisConfigOptions genesisConfigOptions,
            org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration syncConfig) {

        if (genesisConfigOptions.getTerminalTotalDifficulty().isPresent()) {

            final MergeContext mergeContext = protocolContext.getConsensusContext(MergeContext.class);
            final UnverifiedForkchoiceSupplier unverifiedForkchoiceSupplier =
                    new UnverifiedForkchoiceSupplier();
            final long subscriptionId =
                    mergeContext.addNewUnverifiedForkchoiceListener(unverifiedForkchoiceSupplier);

            final Runnable unsubscribeForkchoiceListener =
                    () -> {
                        mergeContext.removeNewUnverifiedForkchoiceListener(subscriptionId);
                    };

            return new PivotSelectorFromFinalizedBlock(
                    protocolContext,
                    protocolSchedule,
                    ethContext,
                    metricsSystem,
                    genesisConfigOptions,
                    unverifiedForkchoiceSupplier,
                    unsubscribeForkchoiceListener);
        } else {
            return new PivotSelectorFromPeers(ethContext, syncConfig, syncState, metricsSystem);
        }
    }

}
