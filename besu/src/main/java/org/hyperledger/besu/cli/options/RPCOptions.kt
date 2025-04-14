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

import org.hyperledger.besu.ethereum.api.handlers.TimeoutOptions
import picocli.CommandLine

/** The Rpc Cli options.  */
class RPCOptions
/** Default Constructor.  */
internal constructor() {
    /**
     * Gets http timeout sec.
     *
     * @return the http timeout sec
     */
    @JvmField
    @CommandLine.Option(
        hidden = true,
        names = ["--Xhttp-timeout-seconds"],
        description = ["HTTP timeout in seconds (default: \${DEFAULT-VALUE})"],
        arity = "1"
    )
    var httpTimeoutSec: Long = TimeoutOptions.defaultOptions().timeoutSeconds

    /**
     * Gets WebSocket timeout sec.
     *
     * @return the WebSocket timeout sec
     */
    @JvmField
    @CommandLine.Option(
        hidden = true,
        names = ["--Xws-timeout-seconds"],
        description = ["Web socket timeout in seconds (default: \${DEFAULT-VALUE})"],
        arity = "1"
    )
    var wsTimeoutSec: Long = TimeoutOptions.defaultOptions().timeoutSeconds

    companion object {
        /**
         * Create rpc options.
         *
         * @return the rpc options
         */
        @JvmStatic
        fun create(): RPCOptions {
            return RPCOptions()
        }
    }
}
