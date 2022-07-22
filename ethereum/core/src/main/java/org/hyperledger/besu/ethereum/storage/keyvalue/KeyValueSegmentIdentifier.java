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

import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;

import org.bouncycastle.util.Arrays;

public enum KeyValueSegmentIdentifier implements SegmentIdentifier {
  BLOCKCHAIN(new byte[] {1}),
  WORLD_STATE(new byte[] {2}, new int[] {0, 1}),
  PRIVATE_TRANSACTIONS(new byte[] {3}),
  PRIVATE_STATE(new byte[] {4}),
  PRUNING_STATE(new byte[] {5}, new int[] {0, 1}),
  ACCOUNT_INFO_STATE(new byte[] {6}, new int[] {2}),
  CODE_STORAGE(new byte[] {7}, new int[] {2}),
  ACCOUNT_STORAGE_STORAGE(new byte[] {8}, new int[] {2}),
  TRIE_BRANCH_STORAGE(new byte[] {9}, new int[] {2}),
  TRIE_LOG_STORAGE(new byte[] {10}, new int[] {2}),
  GOQUORUM_PRIVATE_WORLD_STATE(new byte[] {11}),
  GOQUORUM_PRIVATE_STORAGE(new byte[] {12}),
  BACKWARD_SYNC_HEADERS(new byte[] {13}),
  BACKWARD_SYNC_BLOCKS(new byte[] {14}),
  BACKWARD_SYNC_CHAIN(new byte[] {15});

  private final byte[] id;
  private final int[] versionList;

  KeyValueSegmentIdentifier(final byte[] id) {
    this(id, new int[] {0, 1, 2});
  }

  KeyValueSegmentIdentifier(final byte[] id, final int[] versionList) {
    this.id = id;
    this.versionList = versionList;
  }

  @Override
  public String getName() {
    return name();
  }

  @Override
  public byte[] getId() {
    return id;
  }

  @Override
  public boolean includeInDatabaseVersion(final int version) {
    return Arrays.contains(versionList, version);
  }
}
