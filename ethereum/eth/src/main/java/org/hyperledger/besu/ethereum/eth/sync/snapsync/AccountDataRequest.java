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

import static org.hyperledger.besu.ethereum.eth.sync.snapsync.RequestType.ACCOUNT_RANGE;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.messages.snap.AccountRangeMessage;
import org.hyperledger.besu.ethereum.eth.messages.snap.GetAccountRangeMessage;
import org.hyperledger.besu.ethereum.eth.sync.worldstate.WorldDownloadState;
import org.hyperledger.besu.ethereum.proof.WorldStateProofProvider;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.worldstate.StateTrieAccountValue;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage.Updater;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import kotlin.collections.ArrayDeque;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;

/** Returns a list of accounts and the merkle proofs of an entire range */
public class AccountDataRequest extends SnapDataRequest {

  private static final Logger LOG = LogManager.getLogger();

  private final GetAccountRangeMessage request;
  private GetAccountRangeMessage.Range range;
  private AccountRangeMessage.AccountRangeData response;
  private final Hash accountHash;
  private final Bytes32 startStorageRange;
  private final Bytes32 endStorageRange;
  private final int depth;
  private final long priority;

  protected AccountDataRequest(
      final Hash originalRootHash,
      final Hash accountHash,
      final Bytes32 startStorageRange,
      final Bytes32 endStorageRange,
      final int depth,
      final long priority) {
    super(ACCOUNT_RANGE, originalRootHash);
    this.accountHash = accountHash;
    this.startStorageRange = startStorageRange;
    this.endStorageRange = endStorageRange;
    this.depth = depth;
    this.priority = priority;
    LOG.info(
        "create get account data request with root hash={} for account {} with storage range from {} to {}",
        originalRootHash,
        accountHash,
        startStorageRange,
        endStorageRange);
    request =
        GetAccountRangeMessage.create(
            originalRootHash, accountHash, accountHash, BigInteger.valueOf(524288));
  }

  @Override
  protected int doPersist(final WorldStateStorage worldStateStorage, final Updater updater) {
    return 0;
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
    if (accountData.accounts().isEmpty()) {
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

    final AccountRangeMessage.AccountRangeData responseData = getResponse();

    final List<SnapDataRequest> childRequests = new ArrayList<>();

    final StateTrieAccountValue accountValue =
        StateTrieAccountValue.readFrom(RLP.input(responseData.accounts().lastEntry().getValue()));
    if (!accountValue.getStorageRoot().equals(Hash.EMPTY_TRIE_HASH)) {
      childRequests.add(
          createStorageRangeDataRequest(
              new ArrayDeque<>(List.of(accountHash)),
              new ArrayDeque<>(List.of(accountValue.getStorageRoot())),
              startStorageRange,
              endStorageRange,
              depth + 1,
              priority * 16));
    }
    if (!accountValue.getCodeHash().equals(Hash.EMPTY)
        && worldStateStorage.getCode(null, accountHash).isEmpty()) {
      childRequests.add(
          createBytecodeRequest(
              new ArrayDeque<>(List.of(accountHash)),
              new ArrayDeque<>(List.of(accountValue.getCodeHash())),
              depth + 1,
              priority * 16));
    }
    return childRequests.stream();
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
