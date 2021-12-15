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
import org.hyperledger.besu.ethereum.proof.WorldStateProofProvider;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;

import java.util.Optional;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public abstract class SnapDataRequest {

  private final RequestType requestType;

  private Optional<Bytes> data;

  protected SnapDataRequest(final RequestType requestType) {
    this.requestType = requestType;
    this.data = Optional.empty();
  }

  public static AccountRangeDataRequest createAccountRangeDataRequest(
      final Hash rootHash, final Bytes32 startKeyHash, final Bytes32 endKeyHash) {
    return new AccountRangeDataRequest(rootHash, startKeyHash, endKeyHash);
  }

  public static TrieNodeDataRequest createAccountTrieNodeRequest(
      final Hash rootHash, final Bytes location) {
    return new AccountTrieNodeDataRequest(Optional.empty(), rootHash, location);
  }

  public static SnapDataRequest createStorageTrieNodeRequest(
      final Optional<TrieNodeDataRequest> parent,
      final Hash accountHash,
      final Hash nodeHash,
      final Bytes location) {
    return new StorageTrieNodeDataRequest(parent, accountHash, nodeHash, location);
  }

  public static Bytes serialize(final SnapDataRequest request) {
    return RLP.encode(
        out -> {
          out.startList();
          out.writeByte(request.requestType.getValue());
          out.writeBytes(request.getData().orElseThrow());
          out.endList();
        });
  }

  public static SnapDataRequest deserialize(final Bytes encoded) {
    final RLPInput in = RLP.input(encoded);
    in.enterList();
    final RequestType requestType = RequestType.fromValue(in.readByte());
    try {
      final SnapDataRequest deserialized;
      switch (requestType) {
        case ACCOUNT_RANGE:
          deserialized = new AccountRangeDataRequest(in.readBytes());
          break;
        case STORAGE_RANGE:
          deserialized = new StorageRangeDataRequest(in.readBytes());
          break;
        default:
          throw new IllegalArgumentException(
              "Unable to deserialize provided data into a valid "
                  + SnapDataRequest.class.getSimpleName());
      }

      return deserialized;
    } finally {
      in.leaveList();
    }
  }

  public RequestType getRequestType() {
    return requestType;
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

  public void persist(final WorldStateStorage worldStateStorage) {
    final WorldStateStorage.Updater updater = worldStateStorage.updater();
    doPersist(worldStateStorage, updater);
    updater.commit();
  }

  protected abstract void doPersist(
      final WorldStateStorage worldStateStorage, WorldStateStorage.Updater updater);

  protected abstract boolean isValidResponse(
      SnapSyncState fastSyncState,
      EthPeers ethPeers,
      WorldStateProofProvider worldStateProofProvider);

  public abstract Stream<SnapDataRequest> getChildRequests(
      final WorldStateStorage worldStateStorage);

  public Optional<Bytes> getExistingData(final WorldStateStorage worldStateStorage) {
    return Optional.empty();
  }

  public abstract void clear();
}
