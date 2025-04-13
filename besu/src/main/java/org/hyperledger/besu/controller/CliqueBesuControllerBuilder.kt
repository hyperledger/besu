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

import org.hyperledger.besu.config.CliqueConfigOptions
import org.hyperledger.besu.consensus.clique.*
import org.hyperledger.besu.consensus.clique.blockcreation.CliqueBlockScheduler
import org.hyperledger.besu.consensus.clique.blockcreation.CliqueMinerExecutor
import org.hyperledger.besu.consensus.clique.blockcreation.CliqueMiningCoordinator
import org.hyperledger.besu.consensus.clique.jsonrpc.CliqueJsonRpcMethods
import org.hyperledger.besu.consensus.common.BlockInterface
import org.hyperledger.besu.consensus.common.EpochManager
import org.hyperledger.besu.consensus.common.ForksSchedule
import org.hyperledger.besu.consensus.common.validator.blockbased.BlockValidatorProvider
import org.hyperledger.besu.datatypes.Address
import org.hyperledger.besu.ethereum.ProtocolContext
import org.hyperledger.besu.ethereum.api.jsonrpc.methods.JsonRpcMethods
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator
import org.hyperledger.besu.ethereum.chain.BlockAddedEvent
import org.hyperledger.besu.ethereum.chain.Blockchain
import org.hyperledger.besu.ethereum.core.MiningConfiguration
import org.hyperledger.besu.ethereum.core.Util
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/** The Clique consensus controller builder.  */
class CliqueBesuControllerBuilder
/** Default constructor.  */
    : BesuControllerBuilder() {
    private var localAddress: Address? = null
    private var epochManager: EpochManager? = null
    private val blockInterface: BlockInterface = CliqueBlockInterface()
    private var forksSchedule: ForksSchedule<CliqueConfigOptions>? = null

    override fun prepForBuild() {
        localAddress = Util.publicKeyToAddress(nodeKey!!.publicKey)
        val cliqueConfig = genesisConfigOptions!!.cliqueConfigOptions
        val blocksPerEpoch = cliqueConfig.epochLength

        epochManager = EpochManager(blocksPerEpoch)
        forksSchedule = CliqueForksSchedulesFactory.create(genesisConfigOptions)
    }

    override fun createAdditionalJsonRpcMethodFactory(
        protocolContext: ProtocolContext?,
        protocolSchedule: ProtocolSchedule?,
        miningConfiguration: MiningConfiguration?
    ): JsonRpcMethods {
        return CliqueJsonRpcMethods(protocolContext, protocolSchedule, miningConfiguration)
    }

    override fun createMiningCoordinator(
        protocolSchedule: ProtocolSchedule?,
        protocolContext: ProtocolContext?,
        transactionPool: TransactionPool?,
        miningConfiguration: MiningConfiguration?,
        syncState: SyncState?,
        ethProtocolManager: EthProtocolManager?
    ): MiningCoordinator {
        val miningExecutor =
            CliqueMinerExecutor(
                protocolContext,
                protocolSchedule,
                transactionPool,
                nodeKey,
                miningConfiguration,
                CliqueBlockScheduler(
                    clock,
                    protocolContext!!.getConsensusContext(CliqueContext::class.java).validatorProvider,
                    localAddress,
                    forksSchedule
                ),
                epochManager,
                forksSchedule,
                ethProtocolManager!!.ethContext().scheduler
            )
        val miningCoordinator =
            CliqueMiningCoordinator(
                protocolContext.blockchain,
                miningExecutor,
                syncState,
                CliqueMiningTracker(localAddress, protocolContext)
            )

        // Update the next block period in seconds according to the transition schedule
        protocolContext
            .blockchain
            .observeBlockAdded { o: BlockAddedEvent ->
                miningConfiguration!!.setBlockPeriodSeconds(
                    forksSchedule!!
                        .getFork(o.block.header.number + 1)
                        .value
                        .blockPeriodSeconds
                )
            }

        miningCoordinator.addMinedBlockObserver(ethProtocolManager)

        // Clique mining is implicitly enabled.
        miningCoordinator.enable()
        return miningCoordinator
    }

    override fun createProtocolSchedule(): ProtocolSchedule {
        return CliqueProtocolSchedule.create(
            genesisConfigOptions,
            forksSchedule,
            nodeKey,
            privacyParameters,
            isRevertReasonEnabled,
            evmConfiguration,
            miningConfiguration,
            badBlockManager,
            isParallelTxProcessingEnabled,
            metricsSystem
        )
    }

    override fun validateContext(context: ProtocolContext?) {
        val genesisBlockHeader = context!!.blockchain.genesisBlock.header

        if (blockInterface.validatorsInBlock(genesisBlockHeader).isEmpty()) {
            LOG.warn("Genesis block contains no signers - chain will not progress.")
        }
    }

    override fun createAdditionalPluginServices(
        blockchain: Blockchain?, protocolContext: ProtocolContext?
    ): PluginServiceFactory {
        return CliqueQueryPluginServiceFactory(blockchain, nodeKey)
    }

    override fun createConsensusContext(
        blockchain: Blockchain?,
        worldStateArchive: WorldStateArchive?,
        protocolSchedule: ProtocolSchedule?
    ): CliqueContext {
        val cliqueContext =
            CliqueContext(
                BlockValidatorProvider.nonForkingValidatorProvider(
                    blockchain, epochManager, blockInterface
                ),
                epochManager,
                blockInterface
            )
        CliqueHelpers.setCliqueContext(cliqueContext)
        CliqueHelpers.installCliqueBlockChoiceRule(blockchain, cliqueContext)
        return cliqueContext
    }

    override fun getMiningParameterOverrides(fromCli: MiningConfiguration): MiningConfiguration {
        // Clique mines by default, reflect that with in the mining parameters:
        return fromCli.setMiningEnabled(true)
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(CliqueBesuControllerBuilder::class.java)
    }
}
