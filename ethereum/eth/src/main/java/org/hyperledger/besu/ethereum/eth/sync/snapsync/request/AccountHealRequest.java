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
package org.hyperledger.besu.ethereum.eth.sync.snapsync.request;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.SnapSyncState;
import org.hyperledger.besu.ethereum.eth.sync.worldstate.WorldDownloadState;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;

import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;

public class AccountHealRequest extends StorageTrieNodeDataRequest {

  AccountHealRequest(
      final Hash nodeHash, final Hash accountHash, final Hash rootHash, final Bytes location) {
    super(nodeHash, accountHash, rootHash, location);
  }

  @Override
  public boolean isDataPresent() {
    return !data.isEmpty();
  }

  public boolean isValidNodeHash() {
    return isDataPresent() && Hash.hash(data).equals(getNodeHash());
  }

  @Override
  public int persist(
      final WorldStateStorage worldStateStorage,
      final WorldStateStorage.Updater updater,
      final WorldDownloadState<SnapDataRequest> downloadState) {
    if (isValidNodeHash()) {
      return super.persist(worldStateStorage, updater, downloadState);
    }
    return 0;
  }

  @Override
  protected SnapDataRequest createChildNodeDataRequest(final Hash childHash, final Bytes location) {
    return new AccountHealRequest(childHash, getAccountHash(), getRootHash(), location);
  }

  @Override
  public Stream<SnapDataRequest> getChildRequests(
      final WorldStateStorage worldStateStorage, final SnapSyncState snapSyncState) {
    if (isValidNodeHash()) {
      return super.getChildRequests(worldStateStorage, snapSyncState);
    }
    return Stream.empty();
  }
}
