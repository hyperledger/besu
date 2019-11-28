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
package org.hyperledger.besu.ethereum.privacy;

import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.privacy.storage.PrivacyGroupHeadBlockMap;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateBlockMetadata;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;
import org.hyperledger.besu.ethereum.trie.MerklePatriciaTrie;
import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.Optional;

public class PrivateStateRootResolver {
  public static final Hash EMPTY_ROOT_HASH = Hash.wrap(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH);

  private final PrivateStateStorage privateStateStorage;

  public PrivateStateRootResolver(final PrivateStateStorage privateStateStorage) {
    this.privateStateStorage = privateStateStorage;
  }

  public Hash resolveLastStateRoot(final BytesValue privacyGroupId, final Hash blockHash) {
    final Optional<PrivateBlockMetadata> privateBlockMetadataOptional =
        privateStateStorage.getPrivateBlockMetadata(blockHash, Bytes32.wrap(privacyGroupId));
    if (privateBlockMetadataOptional.isPresent()) {
      // Check if block already has meta data for the privacy group
      return privateBlockMetadataOptional.get().getLatestStateRoot();
    }
    final PrivacyGroupHeadBlockMap privacyGroupHeadBlockMap =
        privateStateStorage
            .getPrivacyGroupHeadBlockMap(blockHash)
            .orElse(PrivacyGroupHeadBlockMap.EMPTY);
    return resolveLastStateRoot(privacyGroupId, privacyGroupHeadBlockMap);
  }

  private Hash resolveLastStateRoot(
      final BytesValue privacyGroupId, final PrivacyGroupHeadBlockMap privacyGroupHeadBlockMap) {
    final Hash lastRootHash;
    if (privacyGroupHeadBlockMap.containsKey(Bytes32.wrap(privacyGroupId))) {
      // Check this PG head block is being tracked
      final Hash blockHashForLastBlockWithTx =
          privacyGroupHeadBlockMap.get(Bytes32.wrap(privacyGroupId));
      lastRootHash =
          privateStateStorage
              .getPrivateBlockMetadata(blockHashForLastBlockWithTx, Bytes32.wrap(privacyGroupId))
              .get()
              .getLatestStateRoot();
    } else {
      // First transaction for this PG
      lastRootHash = EMPTY_ROOT_HASH;
    }
    return lastRootHash;
  }
}
