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
package org.hyperledger.besu.services

import org.hyperledger.besu.plugin.services.PrivacyPluginService
import org.hyperledger.besu.plugin.services.privacy.PrivacyGroupAuthProvider
import org.hyperledger.besu.plugin.services.privacy.PrivacyGroupGenesisProvider
import org.hyperledger.besu.plugin.services.privacy.PrivacyPluginPayloadProvider
import org.hyperledger.besu.plugin.services.privacy.PrivateMarkerTransactionFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

/** The Privacy plugin service implementation.  */
@Deprecated("")
class PrivacyPluginServiceImpl
/** Default Constructor.  */
    : PrivacyPluginService {
    private var privacyPluginPayloadProvider: PrivacyPluginPayloadProvider? = null
    private var privateMarkerTransactionFactory: PrivateMarkerTransactionFactory? = null

    private var privacyGroupAuthProvider =
        PrivacyGroupAuthProvider { privacyGroupId: String?, privacyUserId: String?, blockNumber: Optional<Long?>? -> true }
    private var privacyGroupGenesisProvider: PrivacyGroupGenesisProvider? = null

    override fun setPayloadProvider(privacyPluginPayloadProvider: PrivacyPluginPayloadProvider) {
        this.privacyPluginPayloadProvider = privacyPluginPayloadProvider
    }

    override fun getPayloadProvider(): PrivacyPluginPayloadProvider {
        if (privacyPluginPayloadProvider == null) {
            LOG.error(
                "You must register a PrivacyPluginService and register a PrivacyPluginPayloadProvider by calling setPayloadProvider when enabling privacy plugin!"
            )
        }
        return privacyPluginPayloadProvider!!
    }

    override fun setPrivacyGroupAuthProvider(privacyGroupAuthProvider: PrivacyGroupAuthProvider) {
        this.privacyGroupAuthProvider = privacyGroupAuthProvider
    }

    override fun getPrivacyGroupAuthProvider(): PrivacyGroupAuthProvider {
        return privacyGroupAuthProvider
    }

    override fun getPrivateMarkerTransactionFactory(): PrivateMarkerTransactionFactory {
        return privateMarkerTransactionFactory!!
    }

    override fun setPrivateMarkerTransactionFactory(
        privateMarkerTransactionFactory: PrivateMarkerTransactionFactory
    ) {
        this.privateMarkerTransactionFactory = privateMarkerTransactionFactory
    }

    override fun setPrivacyGroupGenesisProvider(
        privacyGroupGenesisProvider: PrivacyGroupGenesisProvider
    ) {
        this.privacyGroupGenesisProvider = privacyGroupGenesisProvider
    }

    override fun getPrivacyGroupGenesisProvider(): PrivacyGroupGenesisProvider {
        return privacyGroupGenesisProvider!!
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(PrivacyPluginServiceImpl::class.java)
    }
}
