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

import java.util.List;
import java.util.stream.Collectors;

import org.bouncycastle.util.Arrays;

public enum KeyValueSegmentIdentifier implements SegmentIdentifier {
  BLOCKCHAIN(new byte[] {1}, false),
  WORLD_STATE(new byte[] {2}, new int[] {0, 1}, false),
  PRIVATE_TRANSACTIONS(new byte[] {3}, false),
  PRIVATE_STATE(new byte[] {4}, false),
  PRUNING_STATE(new byte[] {5}, new int[] {0, 1}, false),
  ACCOUNT_INFO_STATE(new byte[] {6}, new int[] {2}, false),
  CODE_STORAGE(new byte[] {7}, new int[] {2}, false),
  ACCOUNT_STORAGE_STORAGE(new byte[] {8}, new int[] {2}, false),
  TRIE_BRANCH_STORAGE(new byte[] {9}, new int[] {2}, false),
  TRIE_LOG_STORAGE(new byte[] {10}, new int[] {2}, false),
  GOQUORUM_PRIVATE_WORLD_STATE(new byte[] {11}, false),
  GOQUORUM_PRIVATE_STORAGE(new byte[] {12}, false),
  BACKWARD_SYNC_HEADERS(new byte[] {13}, false),
  BACKWARD_SYNC_BLOCKS(new byte[] {14}, false),
  BACKWARD_SYNC_CHAIN(new byte[] {15}, false),
  SNAPSYNC_MISSING_ACCOUNT_RANGE(new byte[] {16}, false),
  SNAPSYNC_ACCOUNT_TO_FIX(new byte[] {17}, false),
  CHAIN_PRUNER_STATE(new byte[] {18}, true);

  private final byte[] id;
  private final int[] versionList;
  private final boolean ignore;

  KeyValueSegmentIdentifier(final byte[] id, final boolean ignore) {
    this(id, new int[] {0, 1, 2}, ignore);
  }

  KeyValueSegmentIdentifier(final byte[] id, final int[] versionList, final boolean ignore) {
    this.id = id;
    this.versionList = versionList;
    this.ignore = ignore;
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

  public static List<SegmentIdentifier> getValues() {
    return getValues(List.of());
  }

  public static List<SegmentIdentifier> getValues(
      final List<KeyValueSegmentIdentifier> notIgnored) {
    return java.util.Arrays.stream(KeyValueSegmentIdentifier.values())
        .filter(identifier -> !identifier.ignore || notIgnored.contains(identifier))
        .collect(Collectors.toList());
  }
}
