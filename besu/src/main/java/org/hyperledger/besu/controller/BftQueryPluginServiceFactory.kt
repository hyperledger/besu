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

import org.hyperledger.besu.consensus.common.bft.BftBlockInterface
import org.hyperledger.besu.consensus.common.bft.BftExtraDataCodec
import org.hyperledger.besu.consensus.common.bft.queries.BftQueryServiceImpl
import org.hyperledger.besu.consensus.common.validator.ValidatorProvider
import org.hyperledger.besu.cryptoservices.NodeKey
import org.hyperledger.besu.ethereum.chain.Blockchain
import org.hyperledger.besu.plugin.services.metrics.PoAMetricsService
import org.hyperledger.besu.plugin.services.query.BftQueryService
import org.hyperledger.besu.plugin.services.query.PoaQueryService
import org.hyperledger.besu.services.BesuPluginContextImpl

/** Bft query plugin service factory which is a concrete implementation of PluginServiceFactory.  */
class BftQueryPluginServiceFactory
/**
 * Instantiates a new Bft query plugin service factory.
 *
 * @param blockchain the blockchain
 * @param bftExtraDataCodec the bft extra data codec
 * @param validatorProvider the validator provider
 * @param nodeKey the node key
 * @param consensusMechanismName the consensus mechanism name
 */(
    private val blockchain: Blockchain,
    private val bftExtraDataCodec: BftExtraDataCodec,
    private val validatorProvider: ValidatorProvider,
    private val nodeKey: NodeKey,
    private val consensusMechanismName: String
) : PluginServiceFactory {
    override fun appendPluginServices(besuContext: BesuPluginContextImpl?) {
        val blockInterface = BftBlockInterface(bftExtraDataCodec)

        val service =
            BftQueryServiceImpl(
                blockInterface, blockchain, validatorProvider, nodeKey, consensusMechanismName
            )
        besuContext?.addService(BftQueryService::class.java, service)
        besuContext?.addService(PoaQueryService::class.java, service)
        besuContext?.addService(PoAMetricsService::class.java, service)
    }
}
