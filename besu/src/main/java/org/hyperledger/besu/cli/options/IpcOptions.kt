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

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcApis
import picocli.CommandLine
import java.nio.file.Path

/** The Ipc CLI options.  */
class IpcOptions
/** Default constructor.  */
internal constructor() {
    /**
     * Whether IPC options are enabled.
     *
     * @return true for enabled, false otherwise.
     */
    @CommandLine.Option(
        names = ["--Xrpc-ipc-enabled"],
        hidden = true,
        description = ["Set to start the JSON-RPC IPC service (default: \${DEFAULT-VALUE})"]
    )
    val isEnabled: Boolean = false

    /**
     * Gets ipc path.
     *
     * @return the ipc path
     */
    @JvmField
    @CommandLine.Option(
        names = ["--Xrpc-ipc-path"], hidden = true, description = [("IPC socket/pipe file (default: a file named \""
                + DEFAULT_IPC_FILE
                + "\" in the Besu data directory)")]
    )
    val ipcPath: Path? = null

    /**
     * Gets rpc ipc apis.
     *
     * @return the rpc ipc apis
     */
    @JvmField
    @CommandLine.Option(
        names = ["--Xrpc-ipc-api", "--Xrpc-ipc-apis"],
        hidden = true,
        paramLabel = "<api name>",
        split = " {0,1}, {0,1}",
        arity = "1..*",
        description = ["Comma separated list of APIs to enable on JSON-RPC IPC service (default: \${DEFAULT-VALUE})"]
    )
    val rpcIpcApis: List<String> = RpcApis.DEFAULT_RPC_APIS

    companion object {
        private const val DEFAULT_IPC_FILE = "besu.ipc"

        /**
         * Create ipc options.
         *
         * @return the ipc options
         */
        @JvmStatic
        fun create(): IpcOptions {
            return IpcOptions()
        }

        /**
         * Gets default path.
         *
         * @param dataDir the data dir
         * @return the default path
         */
        @JvmStatic
        fun getDefaultPath(dataDir: Path): Path {
            return dataDir.resolve(DEFAULT_IPC_FILE)
        }
    }
}
