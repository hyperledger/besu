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
package tech.pegasys.pantheon.ethereum.eth.sync.worldstate;

import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.rlp.RLP;
import tech.pegasys.pantheon.ethereum.rlp.RLPInput;
import tech.pegasys.pantheon.ethereum.rlp.RLPOutput;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateStorage;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public abstract class NodeDataRequest {

  private final RequestType requestType;
  private final Hash hash;
  private BytesValue data;
  private final AtomicInteger failedRequestCount = new AtomicInteger(0);

  protected NodeDataRequest(final RequestType requestType, final Hash hash) {
    this.requestType = requestType;
    this.hash = hash;
  }

  public static AccountTrieNodeDataRequest createAccountDataRequest(final Hash hash) {
    return new AccountTrieNodeDataRequest(hash);
  }

  public static StorageTrieNodeDataRequest createStorageDataRequest(final Hash hash) {
    return new StorageTrieNodeDataRequest(hash);
  }

  public static CodeNodeDataRequest createCodeRequest(final Hash hash) {
    return new CodeNodeDataRequest(hash);
  }

  public static BytesValue serialize(final NodeDataRequest request) {
    return RLP.encode(request::writeTo);
  }

  public static NodeDataRequest deserialize(final BytesValue encoded) {
    RLPInput in = RLP.input(encoded);
    in.enterList();
    RequestType requestType = RequestType.fromValue(in.readByte());
    Hash hash = Hash.wrap(in.readBytes32());
    int failureCount = in.readIntScalar();
    in.leaveList();

    NodeDataRequest deserialized;
    switch (requestType) {
      case ACCOUNT_TRIE_NODE:
        deserialized = createAccountDataRequest(hash);
        break;
      case STORAGE_TRIE_NODE:
        deserialized = createStorageDataRequest(hash);
        break;
      case CODE:
        deserialized = createCodeRequest(hash);
        break;
      default:
        throw new IllegalArgumentException(
            "Unable to deserialize provided data into a valid "
                + NodeDataRequest.class.getSimpleName());
    }

    deserialized.setFailureCount(failureCount);
    return deserialized;
  }

  private void writeTo(final RLPOutput out) {
    out.startList();
    out.writeByte(requestType.getValue());
    out.writeBytesValue(hash);
    out.writeIntScalar(failedRequestCount.get());
    out.endList();
  }

  public RequestType getRequestType() {
    return requestType;
  }

  public Hash getHash() {
    return hash;
  }

  public BytesValue getData() {
    return data;
  }

  public NodeDataRequest setData(final BytesValue data) {
    this.data = data;
    return this;
  }

  public int trackFailure() {
    return failedRequestCount.incrementAndGet();
  }

  private void setFailureCount(final int failures) {
    failedRequestCount.set(failures);
  }

  public abstract void persist(final WorldStateStorage.Updater updater);

  public abstract Stream<NodeDataRequest> getChildRequests();

  public abstract Optional<BytesValue> getExistingData(final WorldStateStorage worldStateStorage);
}
