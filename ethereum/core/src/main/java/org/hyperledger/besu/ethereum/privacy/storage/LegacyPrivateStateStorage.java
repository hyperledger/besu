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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.log.Log;

import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/** This interface contains the methods used to access the private state until version 1.3 */
@Deprecated
public interface LegacyPrivateStateStorage {

  Optional<Hash> getLatestStateRoot(Bytes privacyId);

  Optional<List<Log>> getTransactionLogs(Bytes32 transactionHash);

  Optional<Bytes> getTransactionOutput(Bytes32 transactionHash);

  Optional<Bytes> getStatus(Bytes32 transactionHash);

  Optional<Bytes> getRevertReason(Bytes32 transactionHash);

  Optional<PrivateTransactionMetadata> getTransactionMetadata(
      Bytes32 blockHash, Bytes32 transactionHash);

  boolean isPrivateStateAvailable(Bytes32 transactionHash);

  boolean isWorldStateAvailable(Bytes32 rootHash);

  Updater updater();

  interface Updater {

    Updater putLatestStateRoot(Bytes privacyId, Hash privateStateHash);

    Updater putTransactionLogs(Bytes32 transactionHash, List<Log> logs);

    Updater putTransactionResult(Bytes32 transactionHash, Bytes events);

    Updater putTransactionStatus(Bytes32 transactionHash, Bytes status);

    Updater putTransactionRevertReason(Bytes32 txHash, Bytes bytesValue);

    Updater putTransactionMetadata(
        Bytes32 blockHash, Bytes32 transactionHash, PrivateTransactionMetadata metadata);

    void commit();

    void rollback();
  }
}
