/*
 * Copyright contributors to Hyperledger Besu
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
package org.hyperledger.besu.ethereum.eth.sync.snapsync;

import static org.hyperledger.besu.ethereum.eth.sync.snapsync.RangeManager.MAX_RANGE;
import static org.hyperledger.besu.ethereum.eth.sync.snapsync.RangeManager.findNewBeginElementInRange;
import static org.hyperledger.besu.ethereum.eth.sync.snapsync.RequestType.STORAGE_RANGE;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.messages.snap.GetStorageRangeMessage;
import org.hyperledger.besu.ethereum.eth.messages.snap.StorageRangeMessage;
import org.hyperledger.besu.ethereum.proof.WorldStateProofProvider;
import org.hyperledger.besu.ethereum.trie.MerklePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.MerkleTrieException;
import org.hyperledger.besu.ethereum.trie.MissingNode;
import org.hyperledger.besu.ethereum.trie.Node;
import org.hyperledger.besu.ethereum.trie.StoredMerklePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.StoredNodeFactory;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage.Updater;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import kotlin.collections.ArrayDeque;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.rlp.RLP;

/** Returns a list of storages and the merkle proofs of an entire range */
public class StorageRangeDataRequest extends SnapDataRequest {

  private final GetStorageRangeMessage request;
  private GetStorageRangeMessage.StorageRange range;
  private StorageRangeMessage.SlotRangeData response;

  public static StorageRangeDataRequest createStorageRangeDataRequest(
      final Hash rootHash,
      final ArrayDeque<Bytes32> accountsHashes,
      final ArrayDeque<Bytes32> storageRoots,
      final Bytes32 startKeyHash,
      final Bytes32 endKeyHash) {
    return new StorageRangeDataRequest(
        rootHash, accountsHashes, storageRoots, startKeyHash, endKeyHash);
  }

  protected StorageRangeDataRequest(
      final Hash rootHash,
      final ArrayDeque<Bytes32> accountsHashes,
      final ArrayDeque<Bytes32> storageRoots,
      final Bytes32 startKeyHash,
      final Bytes32 endKeyHash) {
    super(STORAGE_RANGE);
    request =
        GetStorageRangeMessage.create(
            rootHash,
            accountsHashes,
            Optional.of(storageRoots),
            startKeyHash,
            endKeyHash,
            BigInteger.valueOf(524288));
  }

  @SuppressWarnings("unused")
  protected StorageRangeDataRequest(final Bytes data) {
    super(STORAGE_RANGE);
    request = new GetStorageRangeMessage(data);
  }

  @Override
  protected void doPersist(final WorldStateStorage worldStateStorage, final Updater updater) {
    if (getData().isPresent()) {
      final StorageRangeMessage.SlotRangeData slotRangeData = getResponse();

      Map<Bytes32, Bytes> proofsEntries = Collections.synchronizedMap(new HashMap<>());
      for (Bytes proof : slotRangeData.proofs()) {
        proofsEntries.put(Hash.hash(proof), proof);
      }

      final ArrayDeque<Bytes32> storageRoots =
          getStorageRangeMessage().getStorageRoots().orElseThrow();

      for (int i = 0; i < slotRangeData.slots().size(); i++) {

        final Hash accountHash = Hash.wrap(getRange().hashes().get(i));

        final StoredNodeFactory<Bytes> snapStoredNodeFactory =
            new StoredNodeFactory<>(
                (location, hash) -> Optional.ofNullable(proofsEntries.get(hash)),
                Function.identity(),
                Function.identity()) {
              @Override
              public Optional<Node<Bytes>> retrieve(final Bytes location, final Bytes32 hash)
                  throws MerkleTrieException {
                return super.retrieve(location, hash)
                    .or(() -> Optional.of(new MissingNode<>(hash, location)));
              }
            };
        MerklePatriciaTrie<Bytes, Bytes> trie =
            new StoredMerklePatriciaTrie<>(snapStoredNodeFactory, storageRoots.get(i));

        for (Map.Entry<Bytes32, Bytes> account : slotRangeData.slots().get(i).entrySet()) {
          trie.put(account.getKey(), account.getValue());
          if (updater instanceof BonsaiWorldStateKeyValueStorage.Updater) {
            ((BonsaiWorldStateKeyValueStorage.Updater) updater)
                .putStorageValueBySlotHash(
                    accountHash,
                    Hash.wrap(account.getKey()),
                    Bytes32.leftPad(RLP.decodeValue(account.getValue())));
          }
        }
        trie.commit(
            (location, nodeHash, value) -> {
              updater.putAccountStorageTrieNode(accountHash, location, nodeHash, value);
            });
      }
    }
  }

  @Override
  protected boolean isValidResponse(
      final SnapSyncState fastSyncState,
      final EthPeers ethPeers,
      final WorldStateProofProvider worldStateProofProvider) {
    final GetStorageRangeMessage.StorageRange requestData = getRange();
    final StorageRangeMessage.SlotRangeData responseData = getResponse();

    if (responseData.slots().isEmpty() && responseData.proofs().isEmpty()) {
      return !fastSyncState.isPivotBlockChanged();
    }

    // reject if the peer sent more slots than requested
    if (requestData.hashes().size() < responseData.slots().getSize()) {
      return false;
    }

    for (int i = 0; i < responseData.slots().getSize(); i++) {
      // if this is not the last account in the list we must validate the full range
      final boolean isLastRange = i == responseData.slots().getSize() - 1;
      final Hash endHash = isLastRange ? requestData.endKeyHash() : MAX_RANGE;
      final Optional<List<Bytes>> proofs =
          isLastRange ? Optional.of(responseData.proofs()) : Optional.empty();
      if (!worldStateProofProvider.isValidRangeProof(
          requestData.startKeyHash(),
          endHash,
          request.getStorageRoots().orElseThrow().get(i),
          proofs,
          responseData.slots().get(i))) {
        return false;
      }
    }
    return true;
  }

  public GetStorageRangeMessage getStorageRangeMessage() {
    return request;
  }

  public GetStorageRangeMessage.StorageRange getRange() {
    if (range == null) {
      range = request.range(false);
    }
    return range;
  }

  public StorageRangeMessage.SlotRangeData getResponse() {
    if (response == null) {
      response = new StorageRangeMessage(getData().orElseThrow()).slotsData(true);
    }
    return response;
  }

  @Override
  public Stream<SnapDataRequest> getChildRequests(final WorldStateStorage worldStateStorage) {
    final GetStorageRangeMessage.StorageRange requestData = getRange();
    final StorageRangeMessage.SlotRangeData responseData = getResponse();
    final List<SnapDataRequest> childRequests = new ArrayList<>();

    // flag storages if peers cannot find them
    if (responseData.slots().isEmpty() && responseData.proofs().isEmpty()) {
      return Stream.empty();
    } else {
      // new request if there are some missing accounts
      if (responseData.slots().isEmpty()) {
        return Stream.empty();
      } else if (requestData.hashes().size() > responseData.slots().getSize()) {
        final int lastSlotIndex = responseData.slots().size();
        final ArrayDeque<Bytes32> missingAccounts =
            new ArrayDeque<>(
                requestData.hashes().stream().skip(lastSlotIndex).collect(Collectors.toList()));
        final ArrayDeque<Bytes32> missingStorageRoots =
            new ArrayDeque<>(
                getStorageRangeMessage().getStorageRoots().orElseThrow().stream()
                    .skip(lastSlotIndex)
                    .collect(Collectors.toList()));

        childRequests.add(
            createStorageRangeDataRequest(
                requestData.worldStateRootHash(),
                missingAccounts,
                missingStorageRoots,
                requestData.startKeyHash(),
                requestData.endKeyHash()));
      } else {
        // new request if there are some missing storage slots
        findNewBeginElementInRange(getRange().endKeyHash(), responseData.slots().last())
            .ifPresent(
                missingRightElement ->
                    childRequests.add(
                        createStorageRangeDataRequest(
                            requestData.worldStateRootHash(),
                            new ArrayDeque<>(List.of(requestData.hashes().last())),
                            new ArrayDeque<>(
                                List.of(
                                    getStorageRangeMessage()
                                        .getStorageRoots()
                                        .orElseThrow()
                                        .last())),
                            missingRightElement,
                            requestData.endKeyHash())));
      }
    }
    return childRequests.stream();
  }

  @Override
  public void clear() {
    range = null;
    response = null;
  }

  @Override
  public String toString() {
    return "StorageRangeDataRequest{"
        + "request="
        + request
        + ", range="
        + range
        + ", response="
        + response
        + '}';
  }
}
