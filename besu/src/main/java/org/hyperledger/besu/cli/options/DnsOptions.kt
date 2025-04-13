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

import org.hyperledger.besu.ethereum.p2p.peers.EnodeDnsConfiguration
import org.hyperledger.besu.ethereum.p2p.peers.ImmutableEnodeDnsConfiguration
import picocli.CommandLine
import java.util.*

/** The Dns CLI options.  */
class DnsOptions
/** Instantiates a new Dns options.  */
internal constructor() : CLIOptions<EnodeDnsConfiguration?> {
    private val DNS_ENABLED = "--Xdns-enabled"
    private val DNS_UPDATE_ENABLED = "--Xdns-update-enabled"

    /**
     * Gets dns enabled.
     *
     * @return the dns enabled
     */
    @CommandLine.Option(hidden = true, names = ["--Xdns-enabled"], description = ["Enabled DNS support"], arity = "1")
    var dnsEnabled: Boolean = java.lang.Boolean.FALSE
        private set

    /**
     * Gets dns update enabled.
     *
     * @return the dns update enabled
     */
    @CommandLine.Option(
        hidden = true,
        names = ["--Xdns-update-enabled"],
        description = ["Allow to detect an IP update automatically"],
        arity = "1"
    )
    var dnsUpdateEnabled: Boolean = java.lang.Boolean.FALSE
        private set

    override fun toDomainObject(): EnodeDnsConfiguration {
        return ImmutableEnodeDnsConfiguration.builder()
            .updateEnabled(dnsUpdateEnabled)
            .dnsEnabled(dnsEnabled)
            .build()
    }

    override fun getCLIOptions(): List<String> {
        return Arrays.asList(
            DNS_ENABLED, dnsEnabled.toString(), DNS_UPDATE_ENABLED, dnsUpdateEnabled.toString()
        )
    }

    companion object {
        /**
         * Create dns options.
         *
         * @return the dns options
         */
        @JvmStatic
        fun create(): DnsOptions {
            return DnsOptions()
        }

        /**
         * From config dns options.
         *
         * @param enodeDnsConfiguration the enode dns configuration
         * @return the dns options
         */
        fun fromConfig(enodeDnsConfiguration: EnodeDnsConfiguration): DnsOptions {
            val cliOptions = DnsOptions()
            cliOptions.dnsEnabled = enodeDnsConfiguration.dnsEnabled()
            cliOptions.dnsUpdateEnabled = enodeDnsConfiguration.updateEnabled()
            return cliOptions
        }
    }
}
