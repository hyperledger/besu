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
package org.hyperledger.besu.ethereum.storage.keyvalue;

import org.hyperledger.besu.ethereum.chain.BlockchainStorage;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ScheduleBasedBlockHeaderFunctions;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;

import java.util.function.Function;

public class GoQuorumKeyValueStorageProvider extends KeyValueStorageProvider {

  public GoQuorumKeyValueStorageProvider(
      final Function<SegmentIdentifier, KeyValueStorage> storageCreator,
      final KeyValueStorage worldStatePreimageStorage,
      final boolean segmentIsolationSupported) {
    super(storageCreator, worldStatePreimageStorage, segmentIsolationSupported);
  }

  public GoQuorumKeyValueStorageProvider(
      final Function<SegmentIdentifier, KeyValueStorage> storageCreator,
      final KeyValueStorage worldStatePreimageStorage,
      final KeyValueStorage privateWorldStatePreimageStorage,
      final boolean segmentIsolationSupported) {
    super(
        storageCreator,
        worldStatePreimageStorage,
        privateWorldStatePreimageStorage,
        segmentIsolationSupported);
  }

  @Override
  public BlockchainStorage createBlockchainStorage(final ProtocolSchedule protocolSchedule) {
    return new GoQuorumKeyValueStoragePrefixedKeyBlockchainStorage(
        getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.BLOCKCHAIN),
        ScheduleBasedBlockHeaderFunctions.create(protocolSchedule));
  }
}
