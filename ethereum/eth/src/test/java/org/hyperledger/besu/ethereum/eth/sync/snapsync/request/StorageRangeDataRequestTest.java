/*
 * Copyright Hyperledger Besu Contributors.
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

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.proof.WorldStateProofProvider;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;

import java.util.Collections;

import kotlin.collections.ArrayDeque;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class StorageRangeDataRequestTest {

  @Mock WorldStateStorage storage;
  WorldStateProofProvider worldstateProofProvider = new WorldStateProofProvider(storage);

  @Test
  public void assertEmptySlotsWithProofOfExclusionCompletes() {

    var storageRangeRequest =
        new StorageRangeDataRequest(
            Hash.EMPTY_TRIE_HASH, Bytes32.ZERO, Hash.EMPTY_TRIE_HASH, Bytes32.ZERO, Hash.LAST);

    var proofs = new ArrayDeque<Bytes>();
    proofs.add(0, Hash.EMPTY_TRIE_HASH);

    storageRangeRequest.addResponse(
        null, worldstateProofProvider, Collections.emptyNavigableMap(), proofs);
    // valid proof of exclusion received
    assertThat(storageRangeRequest.isProofValid()).isTrue();
    assertThat(storageRangeRequest.isResponseReceived()).isTrue();
  }

  @Test
  public void assertEmptySlotsWithInvalidProofCompletes() {
    var storageRangeRequest =
        new StorageRangeDataRequest(
            Hash.EMPTY_TRIE_HASH, Bytes32.ZERO, Hash.EMPTY_TRIE_HASH, Bytes32.ZERO, Hash.LAST);

    var proofs = new ArrayDeque<Bytes>();
    proofs.add(0, Hash.ZERO);

    storageRangeRequest.addResponse(
        null, worldstateProofProvider, Collections.emptyNavigableMap(), proofs);
    // TODO: expect invalid proof, but response received:
    // assertThat(storageRangeRequest.isProofValid()).isFalse();
    assertThat(storageRangeRequest.isResponseReceived()).isTrue();
  }
}
