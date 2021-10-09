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
package org.hyperledger.besu.util;

import org.hyperledger.besu.controller.BesuController;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.privacy.PrivateStateRootResolver;
import org.hyperledger.besu.ethereum.privacy.storage.LegacyPrivateStateStorage;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;
import org.hyperledger.besu.ethereum.privacy.storage.migration.PrivateMigrationBlockProcessor;
import org.hyperledger.besu.ethereum.privacy.storage.migration.PrivateStorageMigration;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

public class PrivateStorageMigrationBuilder {

  private final BesuController besuController;
  private final PrivacyParameters privacyParameters;

  public PrivateStorageMigrationBuilder(
      final BesuController besuController, final PrivacyParameters privacyParameters) {
    this.besuController = besuController;
    this.privacyParameters = privacyParameters;
  }

  public PrivateStorageMigration build() {
    final Blockchain blockchain = besuController.getProtocolContext().getBlockchain();
    final Address privacyPrecompileAddress = privacyParameters.getPrivacyAddress();
    final ProtocolSchedule protocolSchedule = besuController.getProtocolSchedule();
    final WorldStateArchive publicWorldStateArchive =
        besuController.getProtocolContext().getWorldStateArchive();
    final PrivateStateStorage privateStateStorage = privacyParameters.getPrivateStateStorage();
    final LegacyPrivateStateStorage legacyPrivateStateStorage =
        privacyParameters.getPrivateStorageProvider().createLegacyPrivateStateStorage();
    final PrivateStateRootResolver privateStateRootResolver =
        privacyParameters.getPrivateStateRootResolver();

    return new PrivateStorageMigration(
        blockchain,
        privacyPrecompileAddress,
        protocolSchedule,
        publicWorldStateArchive,
        privateStateStorage,
        privateStateRootResolver,
        legacyPrivateStateStorage,
        PrivateMigrationBlockProcessor::new);
  }
}
