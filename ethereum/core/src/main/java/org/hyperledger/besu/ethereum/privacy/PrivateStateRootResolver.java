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

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
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

  public Hash resolveLastStateRoot(
      final Blockchain blockchain,
      final ProcessableBlockHeader latestBlockHeader,
      final BytesValue privacyGroupId) {
    Hash parentHash;
    if (latestBlockHeader instanceof BlockHeader) {
      parentHash = ((BlockHeader) latestBlockHeader).getHash();
      final Optional<Hash> maybeRoot = resolveStateRootFor(parentHash, privacyGroupId);
      if (maybeRoot.isPresent()) {
        return maybeRoot.get();
      }
    }
    parentHash = latestBlockHeader.getParentHash();
    while (!parentHash.equals(blockchain.getGenesisBlock().getHeader().getParentHash())) {
      final Optional<Hash> maybeRoot = resolveStateRootFor(parentHash, privacyGroupId);
      if (maybeRoot.isPresent()) {
        return maybeRoot.get();
      }
      final Block parentBlock = blockchain.getBlockByHash(parentHash).get();
      parentHash = parentBlock.getHeader().getParentHash();
    }
    return EMPTY_ROOT_HASH;
  }

  private Optional<Hash> resolveStateRootFor(
      final Hash blockHash, final BytesValue privacyGroupId) {
    return privateStateStorage
        .getPrivateBlockMetadata(blockHash, Bytes32.wrap(privacyGroupId))
        .map(PrivateBlockMetadata::getLatestStateRoot);
  }
}
