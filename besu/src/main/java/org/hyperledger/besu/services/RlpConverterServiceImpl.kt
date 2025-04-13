/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.services

import org.apache.tuweni.bytes.Bytes
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions
import org.hyperledger.besu.ethereum.core.encoding.receipt.TransactionReceiptDecoder
import org.hyperledger.besu.ethereum.core.encoding.receipt.TransactionReceiptEncoder
import org.hyperledger.besu.ethereum.core.encoding.receipt.TransactionReceiptEncodingConfiguration
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule
import org.hyperledger.besu.ethereum.mainnet.ScheduleBasedBlockHeaderFunctions
import org.hyperledger.besu.ethereum.rlp.RLP
import org.hyperledger.besu.ethereum.rlp.RLPOutput
import org.hyperledger.besu.plugin.data.BlockBody
import org.hyperledger.besu.plugin.data.BlockHeader
import org.hyperledger.besu.plugin.data.TransactionReceipt
import org.hyperledger.besu.plugin.services.rlp.RlpConverterService

/** RLP Serialization/Deserialization service.  */
class RlpConverterServiceImpl(protocolSchedule: ProtocolSchedule?) : RlpConverterService {
    private val blockHeaderFunctions: BlockHeaderFunctions = ScheduleBasedBlockHeaderFunctions.create(protocolSchedule)

    override fun buildHeaderFromRlp(rlp: Bytes): BlockHeader? {
        return org.hyperledger.besu.ethereum.core.BlockHeader.readFrom(
            RLP.input(rlp), blockHeaderFunctions
        )
    }

    override fun buildBodyFromRlp(rlp: Bytes): BlockBody {
        return org.hyperledger.besu.ethereum.core.BlockBody.readWrappedBodyFrom(
            RLP.input(rlp), blockHeaderFunctions
        )
    }

    override fun buildReceiptFromRlp(rlp: Bytes): TransactionReceipt {
        return TransactionReceiptDecoder.readFrom(RLP.input(rlp))
    }

    override fun buildRlpFromHeader(blockHeader: BlockHeader): Bytes {
        return RLP.encode { out: RLPOutput? ->
            org.hyperledger.besu.ethereum.core.BlockHeader.convertPluginBlockHeader(
                blockHeader, blockHeaderFunctions
            )
                .writeTo(out)
        }
    }

    override fun buildRlpFromBody(blockBody: BlockBody): Bytes {
        return RLP.encode { rlpOutput: RLPOutput? ->
            (blockBody as org.hyperledger.besu.ethereum.core.BlockBody)
                .writeWrappedBodyTo(rlpOutput)
        }
    }

    override fun buildRlpFromReceipt(receipt: TransactionReceipt): Bytes {
        return RLP.encode { rlpOutput: RLPOutput? ->
            TransactionReceiptEncoder.writeTo(
                receipt as org.hyperledger.besu.ethereum.core.TransactionReceipt,
                rlpOutput,
                TransactionReceiptEncodingConfiguration.NETWORK
            )
        }
    }
}
