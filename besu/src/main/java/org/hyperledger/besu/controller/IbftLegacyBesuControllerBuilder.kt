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

import org.hyperledger.besu.consensus.common.EpochManager
import org.hyperledger.besu.consensus.common.bft.BftBlockInterface
import org.hyperledger.besu.consensus.common.bft.BftContext
import org.hyperledger.besu.consensus.common.validator.ValidatorProvider
import org.hyperledger.besu.consensus.common.validator.blockbased.BlockValidatorProvider
import org.hyperledger.besu.consensus.ibftlegacy.IbftExtraDataCodec
import org.hyperledger.besu.consensus.ibftlegacy.IbftLegacyBlockInterface
import org.hyperledger.besu.consensus.ibftlegacy.IbftProtocolSchedule
import org.hyperledger.besu.ethereum.ProtocolContext
import org.hyperledger.besu.ethereum.blockcreation.MiningCoordinator
import org.hyperledger.besu.ethereum.blockcreation.NoopMiningCoordinator
import org.hyperledger.besu.ethereum.chain.Blockchain
import org.hyperledger.besu.ethereum.core.MiningConfiguration
import org.hyperledger.besu.ethereum.eth.manager.EthProtocolManager
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/** The Ibft legacy besu controller builder.  */
class IbftLegacyBesuControllerBuilder : BesuControllerBuilder() {
    private val blockInterface: BftBlockInterface

    /** Default constructor  */
    init {
        LOG.warn(
            "IBFT1 is deprecated. This consensus configuration should be used only while migrating to another consensus mechanism."
        )
        this.blockInterface = IbftLegacyBlockInterface(IbftExtraDataCodec())
    }

    override fun createMiningCoordinator(
        protocolSchedule: ProtocolSchedule?,
        protocolContext: ProtocolContext?,
        transactionPool: TransactionPool?,
        miningConfiguration: MiningConfiguration?,
        syncState: SyncState?,
        ethProtocolManager: EthProtocolManager?
    ): MiningCoordinator {
        return NoopMiningCoordinator(miningConfiguration)
    }

    override fun createProtocolSchedule(): ProtocolSchedule {
        return IbftProtocolSchedule.create(
            genesisConfigOptions, privacyParameters, isRevertReasonEnabled, evmConfiguration
        )
    }

    override fun createConsensusContext(
        blockchain: Blockchain?,
        worldStateArchive: WorldStateArchive?,
        protocolSchedule: ProtocolSchedule?
    ): BftContext {
        val ibftConfig = genesisConfigOptions!!.ibftLegacyConfigOptions
        val epochManager = EpochManager(ibftConfig.epochLength)
        val validatorProvider: ValidatorProvider =
            BlockValidatorProvider.nonForkingValidatorProvider(
                blockchain, epochManager, blockInterface
            )

        return BftContext(validatorProvider, epochManager, blockInterface)
    }

    override fun createAdditionalPluginServices(
        blockchain: Blockchain?, protocolContext: ProtocolContext?
    ): PluginServiceFactory {
        return NoopPluginServiceFactory()
    }

    override fun validateContext(context: ProtocolContext?) {
        val genesisBlockHeader = context!!.blockchain.genesisBlock.header

        if (blockInterface.validatorsInBlock(genesisBlockHeader).isEmpty()) {
            LOG.warn("Genesis block contains no signers - chain will not progress.")
        }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(IbftLegacyBesuControllerBuilder::class.java)
    }
}
