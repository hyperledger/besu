/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.ethereum.privacy;

import org.hyperledger.besu.ethereum.core.Log;
import org.hyperledger.besu.ethereum.core.LogSeries;
import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.List;
import java.util.Optional;

public interface PrivateTransactionStorage {

  Optional<List<Log>> getEvents(Bytes32 transactionHash);

  Optional<BytesValue> getOutput(Bytes32 transactionHash);

  boolean isPrivateStateAvailable(Bytes32 transactionHash);

  Updater updater();

  interface Updater {

    Updater putTransactionLogs(Bytes32 transactionHash, LogSeries logs);

    Updater putTransactionResult(Bytes32 transactionHash, BytesValue events);

    void commit();

    void rollback();
  }
}
