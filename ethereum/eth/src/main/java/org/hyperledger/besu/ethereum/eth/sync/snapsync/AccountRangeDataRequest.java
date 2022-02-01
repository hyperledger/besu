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
import static org.hyperledger.besu.ethereum.eth.sync.snapsync.RangeManager.MIN_RANGE;
import static org.hyperledger.besu.ethereum.eth.sync.snapsync.RangeManager.findNewBeginElementInRange;
import static org.hyperledger.besu.ethereum.eth.sync.snapsync.RequestType.ACCOUNT_RANGE;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.messages.snap.AccountRangeMessage;
import org.hyperledger.besu.ethereum.eth.messages.snap.GetAccountRangeMessage;
import org.hyperledger.besu.ethereum.eth.sync.worldstate.WorldDownloadState;
import org.hyperledger.besu.ethereum.proof.WorldStateProofProvider;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.trie.MerklePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.MerkleTrieException;
import org.hyperledger.besu.ethereum.trie.MissingNode;
import org.hyperledger.besu.ethereum.trie.Node;
import org.hyperledger.besu.ethereum.trie.StoredMerklePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.StoredNodeFactory;
import org.hyperledger.besu.ethereum.worldstate.StateTrieAccountValue;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage.Updater;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;

import com.google.common.collect.Lists;
import kotlin.collections.ArrayDeque;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/** Returns a list of accounts and the merkle proofs of an entire range */
public class AccountRangeDataRequest extends SnapDataRequest {

  private static final Logger LOG = LogManager.getLogger();

  private static final int MAX_ACCOUNT_PER_REQUEST = 85;

  private final GetAccountRangeMessage request;
  private GetAccountRangeMessage.Range range;
  private AccountRangeMessage.AccountRangeData response;
  private final int depth;
  private final long priority;

  protected AccountRangeDataRequest(
      final Hash originalRootHash,
      final Bytes32 startKeyHash,
      final Bytes32 endKeyHash,
      final int depth,
      final long priority) {
    super(ACCOUNT_RANGE, originalRootHash);
    this.depth = depth;
    this.priority = priority;
    LOG.trace(
        "create get account range data request with root hash={} from {} to {}",
        originalRootHash,
        startKeyHash,
        endKeyHash);
    request =
        GetAccountRangeMessage.create(
            originalRootHash, startKeyHash, endKeyHash, BigInteger.valueOf(524288));
  }

  @Override
  protected int doPersist(final WorldStateStorage worldStateStorage, final Updater updater) {
    final GetAccountRangeMessage.Range requestData = getRange();
    final AccountRangeMessage.AccountRangeData responseData = getResponse();

    Map<Bytes32, Bytes> proofsEntries = Collections.synchronizedMap(new HashMap<>());
    for (Bytes proof : responseData.proofs()) {
      proofsEntries.put(Hash.hash(proof), proof);
    }

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
    final MerklePatriciaTrie<Bytes, Bytes> trie =
        new StoredMerklePatriciaTrie<>(snapStoredNodeFactory, requestData.worldStateRootHash());

    for (Map.Entry<Bytes32, Bytes> account : responseData.accounts().entrySet()) {
      trie.put(account.getKey(), account.getValue());
    }

    final AtomicInteger nbNodesSaved = new AtomicInteger();

    trie.commit(
        (location, hash, value) -> {
          if (worldStateStorage.getTrieNodeByLocation(location).isEmpty()) {
            updater.putAccountStateTrieNode(location, hash, value);
            nbNodesSaved.getAndIncrement();
          }
        });
    return nbNodesSaved.get();
  }

  @Override
  protected boolean isTaskCompleted(
      final WorldDownloadState<SnapDataRequest> downloadState,
      final SnapSyncState fastSyncState,
      final EthPeers ethPeers,
      final WorldStateProofProvider worldStateProofProvider) {
    final GetAccountRangeMessage.Range requestData = getRange();
    final AccountRangeMessage.AccountRangeData accountData = getResponse();

    // check if there is a response
    if (accountData.accounts().isEmpty() && accountData.proofs().isEmpty()) {
      return false;
    }

    // validate the proof of the range
    return worldStateProofProvider.isValidRangeProof(
        requestData.startKeyHash(),
        requestData.endKeyHash(),
        requestData.worldStateRootHash(),
        accountData.proofs(),
        accountData.accounts());
  }

  public GetAccountRangeMessage getAccountRangeMessage() {
    return request;
  }

  public GetAccountRangeMessage.Range getRange() {
    if (range == null) {
      range = request.range(false);
    }
    return range;
  }

  public AccountRangeMessage.AccountRangeData getResponse() {
    if (response == null) {
      response = new AccountRangeMessage(getData().orElseThrow()).accountData(true);
    }
    return response;
  }

  @Override
  public Stream<SnapDataRequest> getChildRequests(final WorldStateStorage worldStateStorage) {
    final GetAccountRangeMessage.Range requestData = getRange();
    final AccountRangeMessage.AccountRangeData responseData = getResponse();

    final List<Bytes32> accountsBytecodesToComplete = new ArrayList<>();
    final List<Bytes32> missingBytecodes = new ArrayList<>();

    final List<Bytes32> accountsStorageToComplete = new ArrayList<>();
    final List<Bytes32> missingStorageRoots = new ArrayList<>();

    if (responseData.accounts().isEmpty()) {
      return Stream.empty();
    } else {
      final List<SnapDataRequest> childRequests = new ArrayList<>();

      // new request is added if the response does not match all the requested range
      findNewBeginElementInRange(
              requestData.worldStateRootHash(),
              responseData.proofs(),
              responseData.accounts(),
              requestData.endKeyHash())
          .ifPresent(
              missingRightElement ->
                  childRequests.add(
                      createAccountRangeDataRequest(
                          requestData.worldStateRootHash(),
                          missingRightElement,
                          requestData.endKeyHash(),
                          depth,
                          priority)));

      // find missing storages and code
      for (Map.Entry<Bytes32, Bytes> account : responseData.accounts().entrySet()) {
        final StateTrieAccountValue accountValue =
            StateTrieAccountValue.readFrom(RLP.input(account.getValue()));
        if (!accountValue.getStorageRoot().equals(Hash.EMPTY_TRIE_HASH)) {
          accountsStorageToComplete.add(account.getKey());
          missingStorageRoots.add(accountValue.getStorageRoot());
        }
        if (!accountValue.getCodeHash().equals(Hash.EMPTY)) {
          accountsBytecodesToComplete.add(account.getKey());
          missingBytecodes.add(accountValue.getCodeHash());
        }
      }

      final List<List<Bytes32>> storageRootsPartition =
          Lists.partition(missingStorageRoots, MAX_ACCOUNT_PER_REQUEST);
      final List<List<Bytes32>> accountsStoragePartition =
          Lists.partition(accountsStorageToComplete, MAX_ACCOUNT_PER_REQUEST);
      for (int i = 0; i < storageRootsPartition.size(); i++) {
        childRequests.add(
            createStorageRangeDataRequest(
                new ArrayDeque<>(accountsStoragePartition.get(i)),
                new ArrayDeque<>(storageRootsPartition.get(i)),
                MIN_RANGE,
                MAX_RANGE,
                depth + 1,
                priority * 16 + i));
      }

      final List<List<Bytes32>> bytesCodesPartition =
          Lists.partition(missingBytecodes, MAX_ACCOUNT_PER_REQUEST);
      final List<List<Bytes32>> accountsBytecodePartition =
          Lists.partition(accountsBytecodesToComplete, MAX_ACCOUNT_PER_REQUEST);
      for (int i = 0; i < bytesCodesPartition.size(); i++) {
        childRequests.add(
            createBytecodeRequest(
                new ArrayDeque<>(accountsBytecodePartition.get(i)),
                new ArrayDeque<>(bytesCodesPartition.get(i)),
                depth + 1,
                priority * 16 + i));
      }

      return childRequests.stream();
    }
  }

  @Override
  public void clear() {
    range = null;
    response = null;
  }

  @Override
  public long getPriority() {
    return priority;
  }

  @Override
  public int getDepth() {
    return depth;
  }
}
