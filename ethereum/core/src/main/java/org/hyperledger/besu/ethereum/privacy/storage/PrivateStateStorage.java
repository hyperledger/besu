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
package org.hyperledger.besu.ethereum.privacy.storage;

import org.hyperledger.besu.ethereum.privacy.PrivateTransactionReceipt;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public interface PrivateStateStorage {

  Optional<PrivateTransactionReceipt> getTransactionReceipt(Bytes32 blockHash, Bytes32 txHash);

  Optional<PrivateBlockMetadata> getPrivateBlockMetadata(Bytes32 blockHash, Bytes32 privacyGroupId);

  Optional<PrivacyGroupHeadBlockMap> getPrivacyGroupHeadBlockMap(Bytes32 blockHash);

  int getSchemaVersion();

  Optional<Bytes32> getAddDataKey(Bytes32 privacyGroupId);

  boolean isEmpty();

  Updater updater();

  interface Updater {

    Updater putTransactionReceipt(
        Bytes32 blockHash, Bytes32 transactionHash, PrivateTransactionReceipt receipt);

    Updater putPrivateBlockMetadata(
        Bytes32 blockHash, Bytes32 privacyGroupId, PrivateBlockMetadata metadata);

    Updater putPrivacyGroupHeadBlockMap(Bytes32 blockHash, PrivacyGroupHeadBlockMap map);

    Updater putDatabaseVersion(int version);

    Updater putAddDataKey(Bytes32 privacyGroupId, Bytes32 addDataKey);

    void commit();

    void rollback();

    void remove(final Bytes key, final Bytes keySuffix);
  }
}
