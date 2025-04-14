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
package org.hyperledger.besu.plugin.services

import org.hyperledger.besu.plugin.services.trielogs.TrieLogEvent.TrieLogObserver
import org.hyperledger.besu.plugin.services.trielogs.TrieLogFactory
import org.hyperledger.besu.plugin.services.trielogs.TrieLogProvider
import java.util.*

/**
 * A service interface for registering observers for trie log events.
 *
 *
 * Implementations must be thread-safe.
 */
interface TrieLogService : BesuService {
    /**
     * Provides list of observers to configure for trie log events.
     *
     * @return the list of observers to configure
     */
    val observers: List<TrieLogObserver?>?

    /**
     * Provide a TrieLogFactory implementation to use for serializing and deserializing TrieLogs.
     *
     * @return the TrieLogFactory implementation
     */
    val trieLogFactory: Optional<TrieLogFactory?>?

    /**
     * Configure a TrieLogProvider implementation to use for retrieving stored TrieLogs.
     *
     * @param provider the TrieLogProvider implementation
     */
    fun configureTrieLogProvider(provider: TrieLogProvider?)

    /**
     * Retrieve the configured TrieLogProvider implementation.
     *
     * @return the TrieLogProvider implementation
     */
    val trieLogProvider: TrieLogProvider?
}
