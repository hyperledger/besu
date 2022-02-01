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
package org.hyperledger.besu.ethereum.eth.sync.snapsync;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.sync.worldstate.WorldDownloadState;
import org.hyperledger.besu.ethereum.proof.WorldStateProofProvider;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.services.tasks.TasksPriorityProvider;

import java.util.Optional;
import java.util.stream.Stream;

import kotlin.collections.ArrayDeque;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.immutables.value.Value;

public abstract class SnapDataRequest implements TasksPriorityProvider {

  private final RequestType requestType;

  private final Hash originalRootHash;

  private Optional<Bytes> data;

  protected SnapDataRequest(final RequestType requestType, final Hash originalRootHash) {
    this.requestType = requestType;
    this.originalRootHash = originalRootHash;
    this.data = Optional.empty();
  }

  public static AccountRangeDataRequest createAccountRangeDataRequest(
      final Hash rootHash,
      final Bytes32 startKeyHash,
      final Bytes32 endKeyHash,
      final int depth,
      final long priority) {
    return new AccountRangeDataRequest(rootHash, startKeyHash, endKeyHash, depth, priority);
  }

  public static AccountDataRequest createAccountDataRequest(
      final Hash rootHash,
      final Hash accountHash,
      final Bytes32 startStorageRange,
      final Bytes32 endStorageRange,
      final int depth,
      final long priority) {
    return new AccountDataRequest(
        rootHash, accountHash, startStorageRange, endStorageRange, depth, priority);
  }

  public StorageRangeDataRequest createStorageRangeDataRequest(
      final ArrayDeque<Bytes32> accountsHashes,
      final ArrayDeque<Bytes32> storageRoots,
      final Bytes32 startKeyHash,
      final Bytes32 endKeyHash,
      final int depth,
      final long priority) {
    return new StorageRangeDataRequest(
        getOriginalRootHash(),
        accountsHashes,
        storageRoots,
        startKeyHash,
        endKeyHash,
        depth,
        priority);
  }

  public GetBytecodeRequest createBytecodeRequest(
      final ArrayDeque<Bytes32> accountHashes,
      final ArrayDeque<Bytes32> codeHashes,
      final int depth,
      final long priority) {

    return new GetBytecodeRequest(
        getOriginalRootHash(), accountHashes, codeHashes, depth, priority);
  }

  public RequestType getRequestType() {
    return requestType;
  }

  public Hash getOriginalRootHash() {
    return originalRootHash;
  }

  public Optional<Bytes> getData() {
    return data;
  }

  public SnapDataRequest setData(final Bytes data) {
    this.setData(Optional.ofNullable(data));
    return this;
  }

  public SnapDataRequest setData(final Optional<Bytes> data) {
    this.data = data;
    return this;
  }

  public int persist(
      final WorldStateStorage worldStateStorage, final WorldStateStorage.Updater updater) {
    return doPersist(worldStateStorage, updater);
  }

  protected abstract int doPersist(
      final WorldStateStorage worldStateStorage, final WorldStateStorage.Updater updater);

  protected abstract boolean isTaskCompleted(
      WorldDownloadState<SnapDataRequest> downloadState,
      SnapSyncState fastSyncState,
      EthPeers ethPeers,
      WorldStateProofProvider worldStateProofProvider);

  public abstract Stream<SnapDataRequest> getChildRequests(
      final WorldStateStorage worldStateStorage);

  public abstract void clear();

  @Value.Immutable
  public abstract static class ExistingData {
    @Value.Default
    public boolean isFoundInCache() {
      return false;
    }

    @Value.Parameter
    public abstract Optional<Bytes> data();
  }
}
