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

import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Log;
import org.hyperledger.besu.ethereum.core.LogSeries;
import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.List;
import java.util.Optional;

public interface PrivateStateStorage {

  @Deprecated
  Optional<Hash> getLatestStateRoot(BytesValue privacyId);

  Optional<List<Log>> getTransactionLogs(Bytes32 transactionHash);

  Optional<BytesValue> getTransactionOutput(Bytes32 transactionHash);

  Optional<PrivateTransactionMetadata> getTransactionMetadata(
      Bytes32 blockHash, Bytes32 transactionHash);

  boolean isPrivateStateAvailable(Bytes32 transactionHash);

  boolean isWorldStateAvailable(Bytes32 rootHash);

  Updater updater();

  interface Updater {

    @Deprecated
    Updater putLatestStateRoot(BytesValue privacyId, Hash privateStateHash);

    Updater putTransactionLogs(Bytes32 transactionHash, LogSeries logs);

    Updater putTransactionResult(Bytes32 transactionHash, BytesValue events);

    Updater putTransactionMetadata(
        Bytes32 blockHash, Bytes32 transactionHash, PrivateTransactionMetadata metadata);

    void commit();

    void rollback();
  }
}
