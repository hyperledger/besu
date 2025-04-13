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

import com.google.common.collect.Lists
import org.hyperledger.besu.plugin.services.PermissioningService
import org.hyperledger.besu.plugin.services.permissioning.NodeConnectionPermissioningProvider
import org.hyperledger.besu.plugin.services.permissioning.NodeMessagePermissioningProvider
import org.hyperledger.besu.plugin.services.permissioning.TransactionPermissioningProvider
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.inject.Inject

/** The Permissioning service implementation.  */
class PermissioningServiceImpl
/** Default Constructor.  */
@Inject constructor() : PermissioningService {
    private val connectionPermissioningProviders: MutableList<NodeConnectionPermissioningProvider> =
        Lists.newArrayList()
    private val transactionPermissioningProviders: MutableList<TransactionPermissioningProvider> = ArrayList()

    override fun registerNodePermissioningProvider(
        provider: NodeConnectionPermissioningProvider
    ) {
        connectionPermissioningProviders.add(provider)
    }

    override fun registerTransactionPermissioningProvider(
        provider: TransactionPermissioningProvider
    ) {
        transactionPermissioningProviders.add(provider)
        LOG.info("Registered new transaction permissioning provider.")
    }

    /**
     * Gets connection permissioning providers.
     *
     * @return the connection permissioning providers
     */
    fun getConnectionPermissioningProviders(): List<NodeConnectionPermissioningProvider> {
        return connectionPermissioningProviders
    }

    private val messagePermissioningProviders: MutableList<NodeMessagePermissioningProvider> = Lists.newArrayList()

    override fun registerNodeMessagePermissioningProvider(
        provider: NodeMessagePermissioningProvider
    ) {
        messagePermissioningProviders.add(provider)
    }

    /**
     * Gets message permissioning providers.
     *
     * @return the message permissioning providers
     */
    fun getMessagePermissioningProviders(): List<NodeMessagePermissioningProvider> {
        return messagePermissioningProviders
    }

    /**
     * Gets transaction permissioning providers.
     *
     * @return the transaction permissioning providers
     */
    fun getTransactionPermissioningProviders(): List<TransactionPermissioningProvider> {
        return transactionPermissioningProviders
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(PermissioningServiceImpl::class.java)
    }
}
