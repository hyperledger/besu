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

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;

public class NodeDataRequestTest {

  @Test
  public void serializesAccountTrieNodeRequests() {
    BlockDataGenerator gen = new BlockDataGenerator(0);
    AccountTrieNodeDataRequest request =
        NodeDataRequest.createAccountDataRequest(gen.hash(), Optional.of(Bytes.EMPTY));
    NodeDataRequest sedeRequest = serializeThenDeserialize(request);
    assertRequestsEquals(sedeRequest, request);
    assertThat(sedeRequest).isInstanceOf(AccountTrieNodeDataRequest.class);
  }

  @Test
  public void serializesAccountTrieNodeRequestsWithLocation() {
    BlockDataGenerator gen = new BlockDataGenerator(0);
    AccountTrieNodeDataRequest request =
        NodeDataRequest.createAccountDataRequest(gen.hash(), Optional.of(Bytes.of(3)));
    NodeDataRequest sedeRequest = serializeThenDeserialize(request);
    assertRequestsEquals(sedeRequest, request);
    assertThat(sedeRequest).isInstanceOf(AccountTrieNodeDataRequest.class);
  }

  @Test
  public void serializesStorageTrieNodeRequests() {
    BlockDataGenerator gen = new BlockDataGenerator(0);
    StorageTrieNodeDataRequest request =
        NodeDataRequest.createStorageDataRequest(
            gen.hash(), Optional.of(Hash.EMPTY), Optional.of(Bytes.EMPTY));
    NodeDataRequest sedeRequest = serializeThenDeserialize(request);
    assertRequestsEquals(sedeRequest, request);
    assertThat(sedeRequest).isInstanceOf(StorageTrieNodeDataRequest.class);
  }

  @Test
  public void serializesStorageTrieNodeRequestsWithAccountHashAndLocation() {
    BlockDataGenerator gen = new BlockDataGenerator(0);
    StorageTrieNodeDataRequest request =
        NodeDataRequest.createStorageDataRequest(
            gen.hash(), Optional.of(Hash.ZERO), Optional.of(Bytes.of(3)));
    NodeDataRequest sedeRequest = serializeThenDeserialize(request);
    assertRequestsEquals(sedeRequest, request);
    assertThat(sedeRequest).isInstanceOf(StorageTrieNodeDataRequest.class);
  }

  @Test
  public void serializesCodeRequests() {
    BlockDataGenerator gen = new BlockDataGenerator(0);
    CodeNodeDataRequest request = NodeDataRequest.createCodeRequest(gen.hash(), Optional.empty());
    NodeDataRequest sedeRequest = serializeThenDeserialize(request);
    assertRequestsEquals(sedeRequest, request);
    assertThat(sedeRequest).isInstanceOf(CodeNodeDataRequest.class);
  }

  @Test
  public void serializesCodeRequestsWithAccountHash() {
    BlockDataGenerator gen = new BlockDataGenerator(0);
    CodeNodeDataRequest request =
        NodeDataRequest.createCodeRequest(gen.hash(), Optional.of(Hash.ZERO));
    NodeDataRequest sedeRequest = serializeThenDeserialize(request);
    assertRequestsEquals(sedeRequest, request);
    assertThat(sedeRequest).isInstanceOf(CodeNodeDataRequest.class);
  }

  private NodeDataRequest serializeThenDeserialize(final NodeDataRequest request) {
    return NodeDataRequest.deserialize(NodeDataRequest.serialize(request));
  }

  private void assertRequestsEquals(final NodeDataRequest actual, final NodeDataRequest expected) {
    assertThat(actual.getRequestType()).isEqualTo(expected.getRequestType());
    assertThat(actual.getHash()).isEqualTo(expected.getHash());
    assertThat(actual.getData()).isEqualTo(expected.getData());
    assertThat(actual.getLocation()).isEqualTo(expected.getLocation());
  }
}
