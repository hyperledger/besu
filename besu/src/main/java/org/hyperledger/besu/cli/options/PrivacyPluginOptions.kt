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

import picocli.CommandLine

/** The Privacy plugin Cli options.  */
@Deprecated("")
class PrivacyPluginOptions
/** Default Constructor.  */
internal constructor() {
    /**
     * Is privacy plugin enabled boolean.
     *
     * @return the boolean
     */
    @JvmField
    @CommandLine.Option(
        names = ["--Xprivacy-plugin-enabled"],
        description = ["Deprecated. Tessera-based privacy is deprecated. See CHANGELOG for alternative options. Enables the use of a plugin to implement your own privacy strategy (default: \${DEFAULT-VALUE})"],
        hidden = true
    )
    var isPrivacyPluginEnabled: Boolean = false

    companion object {
        /**
         * Create privacy plugin options.
         *
         * @return the privacy plugin options
         */
        @JvmStatic
        fun create(): PrivacyPluginOptions {
            return PrivacyPluginOptions()
        }
    }
}
