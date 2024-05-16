/*
 * Copyright contributors to Hyperledger Besu.
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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.privacy.storage.PrivacyGroupHeadBlockMap;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateBlockMetadata;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateMetadataUpdater;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;
import org.hyperledger.besu.ethereum.trie.MerkleTrie;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes32;

public class PrivateStateRootResolver {
  public static final Hash EMPTY_ROOT_HASH = Hash.wrap(MerkleTrie.EMPTY_TRIE_NODE_HASH);

  private final PrivateStateStorage privateStateStorage;

  public PrivateStateRootResolver(final PrivateStateStorage privateStateStorage) {
    this.privateStateStorage = privateStateStorage;
  }

  public Hash resolveLastStateRoot(
      final Bytes32 privacyGroupId, final PrivateMetadataUpdater privateMetadataUpdater) {
    final PrivateBlockMetadata privateBlockMetadata =
        privateMetadataUpdater.getPrivateBlockMetadata(privacyGroupId);
    if (privateBlockMetadata != null) {
      return privateBlockMetadata.getLatestStateRoot().get();
    } else {
      final Hash blockHashForLastBlockWithTx =
          privateMetadataUpdater.getPrivacyGroupHeadBlockMap().get(privacyGroupId);
      if (blockHashForLastBlockWithTx != null) {
        return privateStateStorage
            .getPrivateBlockMetadata(blockHashForLastBlockWithTx, privacyGroupId)
            .flatMap(PrivateBlockMetadata::getLatestStateRoot)
            .orElseThrow(
                () ->
                    new RuntimeException(
                        "Privacy inconsistent state: PrivateBlockMetadata does not exist for Block "
                            + blockHashForLastBlockWithTx));
      } else {
        return EMPTY_ROOT_HASH;
      }
    }
  }

  public Hash resolveLastStateRoot(final Bytes32 privacyGroupId, final Hash blockHash) {
    final Optional<PrivateBlockMetadata> privateBlockMetadataOptional =
        privateStateStorage.getPrivateBlockMetadata(blockHash, privacyGroupId);
    if (privateBlockMetadataOptional.isPresent()) {
      // Check if block already has meta data for the privacy group
      return privateBlockMetadataOptional.get().getLatestStateRoot().orElse(EMPTY_ROOT_HASH);
    }

    final Optional<PrivacyGroupHeadBlockMap> maybePrivacyGroupHeadBlockMap =
        privateStateStorage.getPrivacyGroupHeadBlockMap(blockHash);
    if (maybePrivacyGroupHeadBlockMap.isPresent()) {
      return resolveLastStateRoot(privacyGroupId, maybePrivacyGroupHeadBlockMap.get());
    } else {
      return EMPTY_ROOT_HASH;
    }
  }

  private Hash resolveLastStateRoot(
      final Bytes32 privacyGroupId, final PrivacyGroupHeadBlockMap privacyGroupHeadBlockMap) {
    final Hash lastRootHash;
    if (privacyGroupHeadBlockMap.containsKey(privacyGroupId)) {
      // Check this PG head block is being tracked
      final Hash blockHashForLastBlockWithTx = privacyGroupHeadBlockMap.get(privacyGroupId);
      lastRootHash =
          privateStateStorage
              .getPrivateBlockMetadata(blockHashForLastBlockWithTx, privacyGroupId)
              .flatMap(PrivateBlockMetadata::getLatestStateRoot)
              .orElseThrow(
                  () ->
                      new RuntimeException(
                          "Privacy inconsistent state: PrivateBlockMetadata does not exist for Block "
                              + blockHashForLastBlockWithTx));
    } else {
      // First transaction for this PG
      lastRootHash = EMPTY_ROOT_HASH;
    }
    return lastRootHash;
  }
}
