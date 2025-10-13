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
package org.hyperledger.besu.ethereum.eth.sync.fastsync.worldstate;

import static org.hyperledger.besu.ethereum.worldstate.WorldStateStorageCoordinator.applyForStrategy;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;
import org.hyperledger.besu.ethereum.trie.CompactEncoding;
import org.hyperledger.besu.ethereum.trie.MerkleTrie;
import org.hyperledger.besu.ethereum.trie.common.PmtStateTrieAccountValue;
import org.hyperledger.besu.ethereum.worldstate.WorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorageCoordinator;

import java.util.Optional;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

class AccountTrieNodeDataRequest extends TrieNodeDataRequest {

  AccountTrieNodeDataRequest(final Hash hash, final Optional<Bytes> location) {
    super(RequestType.ACCOUNT_TRIE_NODE, hash, location);
  }

  @Override
  protected void doPersist(final WorldStateKeyValueStorage.Updater updater) {
    applyForStrategy(
        updater,
        onBonsai -> {
          onBonsai.putAccountStateTrieNode(getLocation().orElse(Bytes.EMPTY), getHash(), getData());
        },
        onForest -> {
          onForest.putAccountStateTrieNode(getHash(), getData());
        });
  }

  @Override
  public Optional<Bytes> getExistingData(
      final WorldStateStorageCoordinator worldStateKeyValueStorage) {
    return getLocation()
        .flatMap(
            location ->
                worldStateKeyValueStorage
                    .getAccountStateTrieNode(location, getHash())
                    .filter(data -> Hash.hash(data).equals(getHash())));
  }

  @Override
  protected NodeDataRequest createChildNodeDataRequest(
      final Hash childHash, final Optional<Bytes> location) {
    return createAccountDataRequest(childHash, location);
  }

  @Override
  protected Stream<NodeDataRequest> getRequestsFromTrieNodeValue(
      final WorldStateStorageCoordinator worldStateKeyValueStorage,
      final Optional<Bytes> location,
      final Bytes path,
      final Bytes value) {
    final Stream.Builder<NodeDataRequest> builder = Stream.builder();
    final PmtStateTrieAccountValue accountValue =
        PmtStateTrieAccountValue.readFrom(RLP.input(value));

    final Optional<Hash> accountHash =
        Optional.of(
            Hash.wrap(
                Bytes32.wrap(
                    CompactEncoding.pathToBytes(
                        Bytes.concatenate(getLocation().orElse(Bytes.EMPTY), path)))));

    worldStateKeyValueStorage.applyWhenFlatModeEnabled(
        onBonsai -> {
          onBonsai.updater().putAccountInfoState(accountHash.get(), value).commit();
        });

    // Add code, if appropriate
    if (!accountValue.getCodeHash().equals(Hash.EMPTY)) {
      builder.add(createCodeRequest(accountValue.getCodeHash(), accountHash));
    }
    // Add storage, if appropriate
    if (!accountValue.getStorageRoot().equals(MerkleTrie.EMPTY_TRIE_NODE_HASH)) {
      // If storage is non-empty queue download

      final NodeDataRequest storageNode =
          createStorageDataRequest(accountValue.getStorageRoot(), accountHash, Optional.empty());
      builder.add(storageNode);
    }
    return builder.build();
  }

  @Override
  protected void writeTo(final RLPOutput out) {
    out.startList();
    out.writeByte(getRequestType().getValue());
    out.writeBytes(getHash());
    getLocation().ifPresent(out::writeBytes);
    out.endList();
  }
}
