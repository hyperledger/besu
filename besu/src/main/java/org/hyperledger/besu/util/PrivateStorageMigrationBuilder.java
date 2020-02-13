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
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;
import org.hyperledger.besu.ethereum.privacy.storage.migration.PrivateStorageMigration;
import org.hyperledger.besu.ethereum.privacy.storage.migration.PrivateStorageMigrationV2;

public class PrivateStorageMigrationBuilder {

  private final BesuController<?> besuController;
  private final PrivacyParameters privacyParameters;

  public PrivateStorageMigrationBuilder(
      final BesuController<?> besuController, final PrivacyParameters privacyParameters) {
    this.besuController = besuController;
    this.privacyParameters = privacyParameters;
  }

  public PrivateStorageMigration build() {
    final PrivateStateStorage privateStateStorage = privacyParameters.getPrivateStateStorage();
    final Blockchain blockchain = besuController.getProtocolContext().getBlockchain();
    //    final WorldStateArchive worldStateArchive =
    //        besuController.getProtocolContext().getWorldStateArchive();
    //    final ProtocolSchedule<?> protocolSchedule = besuController.getProtocolSchedule();

    //    final WorldStateArchive inMemoryPrivateWorldStateArchive =
    //        createInMemoryPrivateWorldStateArchive();
    //    final PrivateStateRootResolver privateStateRootResolver =
    //        new PrivateStateRootResolver(privateStateStorage);

    //    final PrivateStorageMigrationTransactionProcessor migrationTransactionProcessor =
    //        new PrivateStorageMigrationTransactionProcessor(
    //            blockchain,
    //            protocolSchedule,
    //            worldStateArchive,
    //            inMemoryPrivateWorldStateArchive,
    //            privateStateRootResolver);
    //    return new PrivateStorageMigrationV1(
    //        privateStateStorage,
    //        blockchain,
    //        privacyParameters.getEnclave(),
    //        Bytes.fromBase64String(privacyParameters.getEnclavePublicKey()),
    //        Address.privacyPrecompiled(privacyParameters.getPrivacyAddress()),
    //        migrationTransactionProcessor);

    return new PrivateStorageMigrationV2(
        privateStateStorage,
        blockchain,
        Address.privacyPrecompiled(privacyParameters.getPrivacyAddress()),
        besuController.getProtocolSchedule(),
        besuController.getProtocolContext().getWorldStateArchive());
  }

  //  private WorldStateArchive createInMemoryPrivateWorldStateArchive() {
  //    final WorldStateStorage privateWorldStateStorage =
  //        new WorldStateKeyValueStorage(new InMemoryKeyValueStorage());
  //    final WorldStatePreimageStorage privatePreimageStorage =
  //        new WorldStatePreimageKeyValueStorage(new InMemoryKeyValueStorage());
  //    return new WorldStateArchive(privateWorldStateStorage, privatePreimageStorage);
  //  }
}
