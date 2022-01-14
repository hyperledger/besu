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

import static org.hyperledger.besu.ethereum.eth.sync.snapsync.RequestType.BYTECODES;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.messages.snap.ByteCodesMessage;
import org.hyperledger.besu.ethereum.eth.messages.snap.GetByteCodesMessage;
import org.hyperledger.besu.ethereum.eth.sync.worldstate.WorldDownloadState;
import org.hyperledger.besu.ethereum.proof.WorldStateProofProvider;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage.Updater;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import kotlin.collections.ArrayDeque;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/** Returns a list of bytecodes */
public class GetBytecodeRequest extends SnapDataRequest {

  private static final Logger LOG = LogManager.getLogger();

  private final GetByteCodesMessage request;
  private GetByteCodesMessage.CodeHashes codeHashes;
  private ByteCodesMessage.ByteCodes response;

  protected GetBytecodeRequest(
      final Hash rootHash,
      final ArrayDeque<Bytes32> accountHashes,
      final ArrayDeque<Bytes32> codeHashes) {
    super(BYTECODES, rootHash);
    LOG.trace(
        "create get bytecode data request for {} accounts with root hash={}",
        accountHashes.size(),
        rootHash);
    request =
        GetByteCodesMessage.create(
            Optional.of(accountHashes), codeHashes, BigInteger.valueOf(524288));
  }

  @Override
  protected int doPersist(final WorldStateStorage worldStateStorage, final Updater updater) {

    final ByteCodesMessage.ByteCodes byteCodes = getResponse();

    final ArrayDeque<Bytes32> accounts = getByteCodesMessage().getAccountHashes().orElseThrow();

    final AtomicInteger nbNodesSaved = new AtomicInteger();

    for (int i = 0; i < byteCodes.codes().size(); i++) {
      final Bytes code = byteCodes.codes().get(i);
      updater.putCode(Hash.wrap(accounts.get(i)), Hash.hash(code), code);
      nbNodesSaved.getAndIncrement();
    }

    return nbNodesSaved.get();
  }

  @Override
  protected boolean isTaskCompleted(
      final WorldDownloadState<SnapDataRequest> downloadState,
      final SnapSyncState fastSyncState,
      final EthPeers ethPeers,
      final WorldStateProofProvider worldStateProofProvider) {
    return getCodeHashes().hashes().size() >= getResponse().codes().size()
        && getByteCodesMessage().getAccountHashes().isPresent();
  }

  public GetByteCodesMessage getByteCodesMessage() {
    return request;
  }

  public GetByteCodesMessage.CodeHashes getCodeHashes() {
    if (codeHashes == null) {
      codeHashes = request.codeHashes(false);
    }
    return codeHashes;
  }

  public ByteCodesMessage.ByteCodes getResponse() {
    if (response == null) {
      response = new ByteCodesMessage(getData().orElseThrow()).bytecodes(true);
    }
    return response;
  }

  @Override
  public Stream<SnapDataRequest> getChildRequests(final WorldStateStorage worldStateStorage) {
    final GetByteCodesMessage.CodeHashes requestData = getCodeHashes();
    final ByteCodesMessage.ByteCodes responseData = getResponse();

    final List<SnapDataRequest> childRequests = new ArrayList<>();

    // new request is there are some missing accounts
    if (requestData.hashes().size() > responseData.codes().size()) {
      final int lastAccountIndex = responseData.codes().size();
      final ArrayDeque<Bytes32> missingAccounts =
          new ArrayDeque<>(
              getByteCodesMessage().getAccountHashes().orElseThrow().stream()
                  .skip(lastAccountIndex)
                  .collect(Collectors.toList()));
      final ArrayDeque<Bytes32> missingCodes =
          new ArrayDeque<>(
              requestData.hashes().stream().skip(lastAccountIndex).collect(Collectors.toList()));

      childRequests.add(createBytecodeRequest(missingAccounts, missingCodes));
    }

    return childRequests.stream();
  }

  @Override
  public void clear() {
    codeHashes = null;
    response = null;
  }

  @Override
  public long getPriority() {
    return 0;
  }

  @Override
  public int getDepth() {
    return 0;
  }
}
