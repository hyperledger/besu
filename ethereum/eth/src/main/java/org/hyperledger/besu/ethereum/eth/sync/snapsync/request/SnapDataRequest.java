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

import static org.hyperledger.besu.ethereum.eth.sync.fastsync.worldstate.NodeDataRequest.MAX_CHILDREN;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.RequestType;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.SnapSyncState;
import org.hyperledger.besu.ethereum.eth.sync.snapsync.SnapWorldDownloadState;
import org.hyperledger.besu.ethereum.eth.sync.worldstate.WorldStateDownloaderException;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.services.tasks.TasksPriorityProvider;

import java.util.HashSet;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public abstract class SnapDataRequest implements TasksPriorityProvider {

  public static final int MAX_CHILD = 16;

  protected Optional<TrieNodeDataRequest> possibleParent = Optional.empty();
  protected int depth;
  protected long priority;
  protected final AtomicInteger pendingChildren = new AtomicInteger(0);

  private final RequestType requestType;
  private Hash rootHash;

  protected SnapDataRequest(final RequestType requestType, final Hash originalRootHash) {
    this.requestType = requestType;
    this.rootHash = originalRootHash;
  }

  public static AccountRangeDataRequest createAccountRangeDataRequest(
      final Hash rootHash, final Bytes32 startKeyHash, final Bytes32 endKeyHash) {
    return new AccountRangeDataRequest(rootHash, startKeyHash, endKeyHash);
  }

  public static AccountRangeDataRequest createAccountDataRequest(
      final Hash rootHash,
      final Hash accountHash,
      final Bytes32 startStorageRange,
      final Bytes32 endStorageRange) {
    return new AccountRangeDataRequest(rootHash, accountHash, startStorageRange, endStorageRange);
  }

  public static StorageRangeDataRequest createStorageRangeDataRequest(
      final Hash rootHash,
      final Bytes32 accountHash,
      final Bytes32 storageRoot,
      final Bytes32 startKeyHash,
      final Bytes32 endKeyHash) {
    return new StorageRangeDataRequest(
        rootHash, accountHash, storageRoot, startKeyHash, endKeyHash);
  }

  public static AccountTrieNodeDataRequest createAccountTrieNodeDataRequest(
      final Hash hash, final Bytes location, final HashSet<Bytes> inconsistentAccounts) {
    return new AccountTrieNodeDataRequest(hash, hash, location, inconsistentAccounts);
  }

  public static AccountTrieNodeDataRequest createAccountTrieNodeDataRequest(
      final Hash hash,
      final Hash rootHash,
      final Bytes location,
      final HashSet<Bytes> inconsistentAccounts) {
    return new AccountTrieNodeDataRequest(hash, rootHash, location, inconsistentAccounts);
  }

  public static StorageTrieNodeDataRequest createStorageTrieNodeDataRequest(
      final Hash hash, final Hash accountHash, final Hash rootHash, final Bytes location) {
    return new StorageTrieNodeDataRequest(hash, accountHash, rootHash, location);
  }

  public static BytecodeRequest createBytecodeRequest(
      final Bytes32 accountHash, final Hash rootHash, final Bytes32 codeHash) {
    return new BytecodeRequest(rootHash, accountHash, codeHash);
  }

  public int persist(
      final WorldStateStorage worldStateStorage,
      final WorldStateStorage.Updater updater,
      final SnapWorldDownloadState downloadState,
      final SnapSyncState snapSyncState) {
    return doPersist(worldStateStorage, updater, downloadState, snapSyncState);
  }

  protected abstract int doPersist(
      final WorldStateStorage worldStateStorage,
      final WorldStateStorage.Updater updater,
      final SnapWorldDownloadState downloadState,
      final SnapSyncState snapSyncState);

  public abstract boolean isResponseReceived();

  public boolean isExpired(final SnapSyncState snapSyncState) {
    return false;
  }

  public abstract Stream<SnapDataRequest> getChildRequests(
      final SnapWorldDownloadState downloadState,
      final WorldStateStorage worldStateStorage,
      final SnapSyncState snapSyncState);

  protected void registerParent(final TrieNodeDataRequest parent) {
    if (this.possibleParent.isPresent()) {
      throw new WorldStateDownloaderException("Cannot set parent twice");
    }
    this.possibleParent = Optional.of(parent);
    this.depth = parent.depth + 1;
    this.priority = parent.priority * MAX_CHILDREN + parent.incrementChildren();
  }

  protected int incrementChildren() {
    return pendingChildren.incrementAndGet();
  }

  protected int saveParent(
      final WorldStateStorage worldStateStorage,
      final WorldStateStorage.Updater updater,
      final SnapWorldDownloadState downloadState,
      final SnapSyncState snapSyncState) {
    if (pendingChildren.decrementAndGet() == 0) {
      return persist(worldStateStorage, updater, downloadState, snapSyncState);
    }
    return 0;
  }

  public void clear() {}

  public RequestType getRequestType() {
    return requestType;
  }

  public Hash getRootHash() {
    return rootHash;
  }

  public void setRootHash(final Hash rootHash) {
    this.rootHash = rootHash;
  }

  @Override
  public long getPriority() {
    return 0;
  }

  @Override
  public int getDepth() {
    return 0;
  }
}
