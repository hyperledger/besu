/*
 * Copyright contributors to Hyperledger Besu
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

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.SnapSyncState;
import org.hyperledger.besu.ethereum.eth.sync.worldstate.WorldDownloadState;
import org.hyperledger.besu.ethereum.trie.CompactEncoding;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;

import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

public class AccountHealRequest extends StorageTrieNodeDataRequest {

  AccountHealRequest(
      final Hash nodeHash, final Hash accountHash, final Hash rootHash, final Bytes location) {
    super(nodeHash, accountHash, rootHash, location);
    System.out.println("Create AccountHealRequest heal2"+accountHash+" "+location);
  }

  @Override
  protected int doPersist(final WorldStateStorage worldStateStorage,final WorldStateStorage.Updater updater,final WorldDownloadState<SnapDataRequest> downloadState, final SnapSyncState snapSyncState) {
    System.out.println("Persist heal2 "+getAccountHash()+" "+getLocation());
    return super.doPersist(worldStateStorage, updater, downloadState, snapSyncState);
  }

  @Override
  protected SnapDataRequest createChildNodeDataRequest(final Hash childHash, final Bytes location) {
    return new AccountHealRequest(childHash, getAccountHash(), getRootHash(), location);
  }

  @Override
  public int persist(
          final WorldStateStorage worldStateStorage,
          final WorldStateStorage.Updater updater,
          final WorldDownloadState<SnapDataRequest> downloadState,
          final SnapSyncState snapSyncState) {

    if (!isValid() || pendingChildren.get() > 0) {
      // we do nothing. Our last child will eventually persist us.
      return 0;
    }

    int saved = 0;
    if (requiresPersisting) {
      checkNotNull(data, "Must set data before node can be persisted.");
      saved = doPersist(worldStateStorage, updater, downloadState, snapSyncState);
    }
    if (possibleParent.isPresent()) {
      return possibleParent
              .get()
              .saveParent(worldStateStorage, updater, downloadState, snapSyncState)
              + saved;
    }
    return saved;
  }

  @Override
  public List<Bytes> getTrieNodePath() {
    System.out.println("getTrieNodePath heal2"+accountHash+" "+getLocation());
    return List.of(accountHash, CompactEncoding.encode(getLocation()));
  }
}
