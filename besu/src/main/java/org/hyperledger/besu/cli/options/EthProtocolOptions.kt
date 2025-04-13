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
package org.hyperledger.besu.cli.options

import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration
import org.hyperledger.besu.util.number.PositiveNumber
import picocli.CommandLine
import java.util.*

/** The Eth protocol CLI options.  */
class EthProtocolOptions private constructor() : CLIOptions<EthProtocolConfiguration?> {
    @CommandLine.Option(
        hidden = true,
        names = [MAX_MESSAGE_SIZE_FLAG],
        paramLabel = "<INTEGER>",
        description = ["Maximum message size (in bytes) for Ethereum Wire Protocol messages. (default: \${DEFAULT-VALUE})"]
    )
    private var maxMessageSize: PositiveNumber =
        PositiveNumber.fromInt(EthProtocolConfiguration.DEFAULT_MAX_MESSAGE_SIZE)

    @CommandLine.Option(
        hidden = true,
        names = [MAX_GET_HEADERS_FLAG],
        paramLabel = "<INTEGER>",
        description = ["Maximum request limit for Ethereum Wire Protocol GET_BLOCK_HEADERS. (default: \${DEFAULT-VALUE})"]
    )
    private var maxGetBlockHeaders: PositiveNumber =
        PositiveNumber.fromInt(EthProtocolConfiguration.DEFAULT_MAX_GET_BLOCK_HEADERS)

    @CommandLine.Option(
        hidden = true,
        names = [MAX_GET_BODIES_FLAG],
        paramLabel = "<INTEGER>",
        description = ["Maximum request limit for Ethereum Wire Protocol GET_BLOCK_BODIES. (default: \${DEFAULT-VALUE})"]
    )
    private var maxGetBlockBodies: PositiveNumber =
        PositiveNumber.fromInt(EthProtocolConfiguration.DEFAULT_MAX_GET_BLOCK_BODIES)

    @CommandLine.Option(
        hidden = true,
        names = [MAX_GET_RECEIPTS_FLAG],
        paramLabel = "<INTEGER>",
        description = ["Maximum request limit for Ethereum Wire Protocol GET_RECEIPTS. (default: \${DEFAULT-VALUE})"]
    )
    private var maxGetReceipts: PositiveNumber =
        PositiveNumber.fromInt(EthProtocolConfiguration.DEFAULT_MAX_GET_RECEIPTS)

    @CommandLine.Option(
        hidden = true,
        names = [MAX_GET_NODE_DATA_FLAG],
        paramLabel = "<INTEGER>",
        description = ["Maximum request limit for Ethereum Wire Protocol GET_NODE_DATA. (default: \${DEFAULT-VALUE})"]
    )
    private var maxGetNodeData: PositiveNumber =
        PositiveNumber.fromInt(EthProtocolConfiguration.DEFAULT_MAX_GET_NODE_DATA)

    @CommandLine.Option(
        hidden = true,
        names = [MAX_GET_POOLED_TRANSACTIONS],
        paramLabel = "<INTEGER>",
        description = ["Maximum request limit for Ethereum Wire Protocol GET_POOLED_TRANSACTIONS. (default: \${DEFAULT-VALUE})"]
    )
    private var maxGetPooledTransactions: PositiveNumber =
        PositiveNumber.fromInt(EthProtocolConfiguration.DEFAULT_MAX_GET_POOLED_TRANSACTIONS)

    @CommandLine.Option(
        hidden = true,
        names = [MAX_CAPABILITY],
        paramLabel = "<INTEGER>",
        description = ["Max protocol version to support"]
    )
    private var maxEthCapability = EthProtocolConfiguration.DEFAULT_MAX_CAPABILITY

    @CommandLine.Option(
        hidden = true,
        names = [MIN_CAPABILITY],
        paramLabel = "<INTEGER>",
        description = ["Min protocol version to support"]
    )
    private var minEthCapability = EthProtocolConfiguration.DEFAULT_MIN_CAPABILITY

    override fun toDomainObject(): EthProtocolConfiguration {
        return EthProtocolConfiguration.builder()
            .maxMessageSize(maxMessageSize)
            .maxGetBlockHeaders(maxGetBlockHeaders)
            .maxGetBlockBodies(maxGetBlockBodies)
            .maxGetReceipts(maxGetReceipts)
            .maxGetNodeData(maxGetNodeData)
            .maxGetPooledTransactions(maxGetPooledTransactions)
            .maxEthCapability(maxEthCapability)
            .minEthCapability(minEthCapability)
            .build()
    }

    override fun getCLIOptions(): List<String> {
        return Arrays.asList(
            MAX_MESSAGE_SIZE_FLAG,
            OptionParser.format(maxMessageSize.value),
            MAX_GET_HEADERS_FLAG,
            OptionParser.format(maxGetBlockHeaders.value),
            MAX_GET_BODIES_FLAG,
            OptionParser.format(maxGetBlockBodies.value),
            MAX_GET_RECEIPTS_FLAG,
            OptionParser.format(maxGetReceipts.value),
            MAX_GET_NODE_DATA_FLAG,
            OptionParser.format(maxGetNodeData.value),
            MAX_GET_POOLED_TRANSACTIONS,
            OptionParser.format(maxGetPooledTransactions.value)
        )
    }

    companion object {
        private const val MAX_MESSAGE_SIZE_FLAG = "--Xeth-max-message-size"
        private const val MAX_GET_HEADERS_FLAG = "--Xewp-max-get-headers"
        private const val MAX_GET_BODIES_FLAG = "--Xewp-max-get-bodies"
        private const val MAX_GET_RECEIPTS_FLAG = "--Xewp-max-get-receipts"
        private const val MAX_GET_NODE_DATA_FLAG = "--Xewp-max-get-node-data"
        private const val MAX_GET_POOLED_TRANSACTIONS = "--Xewp-max-get-pooled-transactions"

        private const val MAX_CAPABILITY = "--Xeth-capability-max"
        private const val MIN_CAPABILITY = "--Xeth-capability-min"

        /**
         * Create eth protocol options.
         *
         * @return the eth protocol options
         */
        @JvmStatic
        fun create(): EthProtocolOptions {
            return EthProtocolOptions()
        }

        /**
         * From config eth protocol options.
         *
         * @param config the config
         * @return the eth protocol options
         */
        @JvmStatic
        fun fromConfig(config: EthProtocolConfiguration): EthProtocolOptions {
            val options = create()
            options.maxMessageSize = PositiveNumber.fromInt(config.maxMessageSize)
            options.maxGetBlockHeaders = PositiveNumber.fromInt(config.maxGetBlockHeaders)
            options.maxGetBlockBodies = PositiveNumber.fromInt(config.maxGetBlockBodies)
            options.maxGetReceipts = PositiveNumber.fromInt(config.maxGetReceipts)
            options.maxGetNodeData = PositiveNumber.fromInt(config.maxGetNodeData)
            options.maxGetPooledTransactions = PositiveNumber.fromInt(config.maxGetPooledTransactions)
            options.maxEthCapability = config.maxEthCapability
            options.minEthCapability = config.minEthCapability
            return options
        }
    }
}
