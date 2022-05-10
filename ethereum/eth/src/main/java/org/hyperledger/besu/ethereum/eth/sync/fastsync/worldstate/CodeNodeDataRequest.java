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
package org.hyperledger.besu.ethereum.eth.sync.fastsync.worldstate;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage.Updater;

import java.util.Optional;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;

class CodeNodeDataRequest extends NodeDataRequest {

  final Optional<Hash> accountHash;

  CodeNodeDataRequest(final Hash hash, final Optional<Hash> accountHash) {
    super(RequestType.CODE, hash);
    this.accountHash = accountHash;
  }

  @Override
  protected void doPersist(final Updater updater) {
    updater.putCode(accountHash.orElse(Hash.EMPTY), getHash(), getData());
  }

  @Override
  public Stream<NodeDataRequest> getChildRequests(final WorldStateStorage worldStateStorage) {
    // Code nodes have nothing further to download
    return Stream.empty();
  }

  @Override
  public Optional<Bytes> getExistingData(final WorldStateStorage worldStateStorage) {
    return worldStateStorage
        .getCode(getHash(), accountHash.orElse(Hash.EMPTY))
        .filter(codeBytes -> Hash.hash(codeBytes).equals(getHash()));
  }

  public Optional<Hash> getAccountHash() {
    return accountHash;
  }

  @Override
  protected void writeTo(final RLPOutput out) {
    out.startList();
    out.writeByte(getRequestType().getValue());
    out.writeBytes(getHash());
    getAccountHash().ifPresent(out::writeBytes);
    out.endList();
  }
}
