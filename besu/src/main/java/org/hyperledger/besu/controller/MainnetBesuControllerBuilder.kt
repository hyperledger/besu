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
package org.hyperledger.besu.controller

import org.hyperledger.besu.ethereum.ConsensusContext
import org.hyperledger.besu.ethereum.ProtocolContext
import org.hyperledger.besu.ethereum.blockcreation.DefaultBlockScheduler
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator
import org.hyperledger.besu.ethereum.blockcreation.PoWMinerExecutor
import org.hyperledger.besu.ethereum.blockcreation.PoWMiningCoordinator
import org.hyperledger.besu.ethereum.chain.Blockchain
import org.hyperledger.besu.ethereum.core.MiningConfiguration
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool
import org.hyperledger.besu.ethereum.mainnet.EpochCalculator
import org.hyperledger.besu.ethereum.mainnet.EpochCalculator.DefaultEpochCalculator
import org.hyperledger.besu.ethereum.mainnet.EpochCalculator.Ecip1099EpochCalculator
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderValidator
import org.hyperledger.besu.ethereum.mainnet.MainnetProtocolSchedule
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive
import java.util.*

/** The Mainnet besu controller builder.  */
class MainnetBesuControllerBuilder
/** Default constructor.  */
    : BesuControllerBuilder() {
    private var epochCalculator: EpochCalculator = DefaultEpochCalculator()

    override fun createMiningCoordinator(
        protocolSchedule: ProtocolSchedule?,
        protocolContext: ProtocolContext?,
        transactionPool: TransactionPool?,
        miningConfiguration: MiningConfiguration?,
        syncState: SyncState?,
        ethProtocolManager: EthProtocolManager?
    ): MiningCoordinator {
        val executor =
            PoWMinerExecutor(
                protocolContext,
                protocolSchedule,
                transactionPool,
                miningConfiguration,
                DefaultBlockScheduler(
                    MainnetBlockHeaderValidator.MINIMUM_SECONDS_SINCE_PARENT.toLong(),
                    MainnetBlockHeaderValidator.TIMESTAMP_TOLERANCE_S.toLong(),
                    clock
                ),
                epochCalculator,
                ethProtocolManager!!.ethContext().scheduler
            )

        val miningCoordinator =
            PoWMiningCoordinator(
                protocolContext!!.blockchain,
                executor,
                syncState,
                miningConfiguration!!.unstable.remoteSealersLimit,
                miningConfiguration.unstable.remoteSealersTimeToLive
            )
        miningCoordinator.addMinedBlockObserver(ethProtocolManager)
        miningCoordinator.setStratumMiningEnabled(miningConfiguration.isStratumMiningEnabled)
        if (miningConfiguration.isMiningEnabled) {
            miningCoordinator.enable()
        }

        return miningCoordinator
    }

    override fun createConsensusContext(
        blockchain: Blockchain?,
        worldStateArchive: WorldStateArchive?,
        protocolSchedule: ProtocolSchedule?
    ): ConsensusContext? {
        return null
    }

    override fun createAdditionalPluginServices(
        blockchain: Blockchain?, protocolContext: ProtocolContext?
    ): PluginServiceFactory {
        return NoopPluginServiceFactory()
    }

    override fun createProtocolSchedule(): ProtocolSchedule {
        return MainnetProtocolSchedule.fromConfig(
            genesisConfigOptions,
            Optional.of(privacyParameters!!),
            Optional.of(isRevertReasonEnabled),
            Optional.of(evmConfiguration!!),
            super.miningConfiguration,
            badBlockManager,
            isParallelTxProcessingEnabled,
            metricsSystem
        )
    }

    override fun prepForBuild() {
        genesisConfigOptions!!
            .getThanosBlockNumber()
            .ifPresent { activationBlock: Long -> epochCalculator = Ecip1099EpochCalculator() }
    }
}
