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
package org.hyperledger.besu.util

import org.hyperledger.besu.controller.BesuController
import org.hyperledger.besu.ethereum.chain.Blockchain
import org.hyperledger.besu.ethereum.core.PrivacyParameters
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec
import org.hyperledger.besu.ethereum.privacy.storage.migration.PrivateMigrationBlockProcessor
import org.hyperledger.besu.ethereum.privacy.storage.migration.PrivateStorageMigration

/** The Private storage migration builder.  */
class PrivateStorageMigrationBuilder
/**
 * Instantiates a new Private storage migration builder.
 *
 * @param besuController the besu controller
 * @param privacyParameters the privacy parameters
 */(private val besuController: BesuController, private val privacyParameters: PrivacyParameters) {
    /**
     * Build private storage migration.
     *
     * @return the private storage migration
     */
    fun build(): PrivateStorageMigration {
        val blockchain: Blockchain = besuController.protocolContext.blockchain
        val privacyPrecompileAddress = privacyParameters.privacyAddress
        val protocolSchedule = besuController.protocolSchedule
        val publicWorldStateArchive =
            besuController.protocolContext.worldStateArchive
        val privateStateStorage = privacyParameters.privateStateStorage
        val legacyPrivateStateStorage =
            privacyParameters.privateStorageProvider.createLegacyPrivateStateStorage()
        val privateStateRootResolver =
            privacyParameters.privateStateRootResolver

        return PrivateStorageMigration(
            blockchain,
            privacyPrecompileAddress,
            protocolSchedule,
            publicWorldStateArchive,
            privateStateStorage,
            privateStateRootResolver,
            legacyPrivateStateStorage
        ) { protocolSpec: ProtocolSpec? -> PrivateMigrationBlockProcessor(protocolSpec) }
    }
}
