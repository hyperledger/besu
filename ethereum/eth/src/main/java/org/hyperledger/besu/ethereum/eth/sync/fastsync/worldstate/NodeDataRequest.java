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
package org.hyperledger.besu.ethereum.eth.sync.fastsync.worldstate;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.logging.log4j.LogManager.getLogger;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.eth.sync.worldstate.WorldStateDownloaderException;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.services.tasks.TasksPriorityProvider;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

public abstract class NodeDataRequest implements TasksPriorityProvider {
  private static final Logger LOG = getLogger();
  public static final int MAX_CHILDREN = 16;

  private final RequestType requestType;
  private final Hash hash;
  private Bytes data;
  private boolean requiresPersisting = true;
  private final Optional<Bytes> location;
  private Optional<NodeDataRequest> possibleParent = Optional.empty();
  private final AtomicInteger pendingChildren = new AtomicInteger(0);
  private int depth = 0;
  private long priority;

  protected NodeDataRequest(
      final RequestType requestType, final Hash hash, final Optional<Bytes> location) {
    this.requestType = requestType;
    this.hash = hash;
    this.location = location;
  }

  protected NodeDataRequest(final RequestType requestType, final Hash hash) {
    this.requestType = requestType;
    this.hash = hash;
    this.location = Optional.empty();
  }

  public static AccountTrieNodeDataRequest createAccountDataRequest(
      final Hash hash, final Optional<Bytes> location) {
    return new AccountTrieNodeDataRequest(hash, location);
  }

  public static StorageTrieNodeDataRequest createStorageDataRequest(
      final Hash hash, final Optional<Hash> accountHash, final Optional<Bytes> location) {
    return new StorageTrieNodeDataRequest(hash, accountHash, location);
  }

  public static CodeNodeDataRequest createCodeRequest(
      final Hash hash, final Optional<Hash> accountHash) {
    return new CodeNodeDataRequest(hash, accountHash);
  }

  public static Bytes serialize(final NodeDataRequest request) {
    return RLP.encode(request::writeTo);
  }

  public static NodeDataRequest deserialize(final Bytes encoded) {
    final RLPInput in = RLP.input(encoded);
    in.enterList();
    final RequestType requestType = RequestType.fromValue(in.readByte());
    final Hash hash = Hash.wrap(in.readBytes32());

    final Optional<Hash> accountHash;
    final Optional<Bytes> location;

    try {
      final NodeDataRequest deserialized;
      switch (requestType) {
        case ACCOUNT_TRIE_NODE:
          location = Optional.of((!in.isEndOfCurrentList()) ? in.readBytes() : Bytes.EMPTY);
          deserialized = createAccountDataRequest(hash, location);
          break;
        case STORAGE_TRIE_NODE:
          accountHash =
              Optional.ofNullable((!in.isEndOfCurrentList()) ? Hash.wrap(in.readBytes32()) : null);
          location = Optional.ofNullable((!in.isEndOfCurrentList()) ? in.readBytes() : Bytes.EMPTY);
          deserialized = createStorageDataRequest(hash, accountHash, location);
          break;
        case CODE:
          accountHash =
              Optional.ofNullable((!in.isEndOfCurrentList()) ? Hash.wrap(in.readBytes32()) : null);
          deserialized = createCodeRequest(hash, accountHash);
          break;
        default:
          throw new IllegalArgumentException(
              "Unable to deserialize provided data into a valid "
                  + NodeDataRequest.class.getSimpleName());
      }

      return deserialized;
    } finally {
      in.leaveList();
    }
  }

  public RequestType getRequestType() {
    return requestType;
  }

  public Hash getHash() {
    return hash;
  }

  public Bytes getData() {
    return data;
  }

  public NodeDataRequest setData(final Bytes data) {
    this.data = data;
    return this;
  }

  public Optional<Bytes> getLocation() {
    return location;
  }

  public NodeDataRequest setRequiresPersisting(final boolean requiresPersisting) {
    this.requiresPersisting = requiresPersisting;
    return this;
  }

  public final void persist(final WorldStateStorage.Updater updater) {
    if (pendingChildren.get() > 0) {
      return; // we do nothing. Our last child will eventually persist us.
    }
    if (requiresPersisting) {
      checkNotNull(getData(), "Must set data before node can be persisted.");
      doPersist(updater);
    }
    possibleParent.ifPresentOrElse(
        parent -> parent.saveParent(updater), () -> LOG.warn("Missing a parent for {}", this.hash));
  }

  private void saveParent(final WorldStateStorage.Updater updater) {
    if (pendingChildren.decrementAndGet() == 0) {
      persist(updater);
    }
  }

  private int incrementChildren() {
    return pendingChildren.incrementAndGet();
  }

  protected abstract void writeTo(final RLPOutput out);

  protected abstract void doPersist(final WorldStateStorage.Updater updater);

  public abstract Stream<NodeDataRequest> getChildRequests(WorldStateStorage worldStateStorage);

  public abstract Optional<Bytes> getExistingData(final WorldStateStorage worldStateStorage);

  protected void registerParent(final NodeDataRequest parent) {
    if (this.possibleParent.isPresent()) {
      throw new WorldStateDownloaderException("Cannot set parent twice");
    }
    this.possibleParent = Optional.of(parent);
    this.depth = parent.depth + 1;
    this.priority = parent.priority * MAX_CHILDREN + parent.incrementChildren();
  }

  @Override
  public long getPriority() {
    return priority;
  }

  @Override
  public int getDepth() {
    return depth;
  }
}
