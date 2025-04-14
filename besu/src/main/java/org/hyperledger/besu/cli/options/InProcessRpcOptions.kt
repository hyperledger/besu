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
package org.hyperledger.besu.cli.options

import org.hyperledger.besu.cli.util.CommandLineUtils
import org.hyperledger.besu.ethereum.api.jsonrpc.ImmutableInProcessRpcConfiguration
import org.hyperledger.besu.ethereum.api.jsonrpc.InProcessRpcConfiguration
import picocli.CommandLine

/** The in process RPC options.  */
class InProcessRpcOptions
/** Default constructor.  */
internal constructor() : CLIOptions<InProcessRpcConfiguration?> {
    @CommandLine.Option(
        names = ["--Xin-process-rpc-enabled"],
        hidden = true,
        description = ["Set to enalbe in-process RPC method call service (default: \${DEFAULT-VALUE})"]
    )
    private var enabled = InProcessRpcConfiguration.DEFAULT_IN_PROCESS_RPC_ENABLED

    @CommandLine.Option(
        names = ["--Xin-process-rpc-api", "--Xin-process-rpc-apis"],
        hidden = true,
        paramLabel = "<api name>",
        split = " {0,1}, {0,1}",
        arity = "1..*",
        description = ["Comma separated list of APIs to enable on  in-process RPC method call service (default: \${DEFAULT-VALUE})"]
    )
    private var inProcessRpcApis: Set<String> = InProcessRpcConfiguration.DEFAULT_IN_PROCESS_RPC_APIS

    override fun toDomainObject(): InProcessRpcConfiguration {
        return ImmutableInProcessRpcConfiguration.builder()
            .isEnabled(enabled)
            .inProcessRpcApis(inProcessRpcApis)
            .build()
    }

    override fun getCLIOptions(): List<String> {
        return CommandLineUtils.getCLIOptions(this, InProcessRpcOptions())
    }

    companion object {
        /**
         * Create ipc options.
         *
         * @return the ipc options
         */
        @JvmStatic
        fun create(): InProcessRpcOptions {
            return InProcessRpcOptions()
        }
    }
}
