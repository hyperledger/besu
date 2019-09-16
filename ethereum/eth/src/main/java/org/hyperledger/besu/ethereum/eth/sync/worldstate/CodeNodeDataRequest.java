/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.ethereum.eth.sync.worldstate;

import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage.Updater;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.Optional;
import java.util.stream.Stream;

class CodeNodeDataRequest extends NodeDataRequest {

  CodeNodeDataRequest(final Hash hash) {
    super(RequestType.CODE, hash);
  }

  @Override
  protected void doPersist(final Updater updater) {
    updater.putCode(getHash(), getData());
  }

  @Override
  public Stream<NodeDataRequest> getChildRequests() {
    // Code nodes have nothing further to download
    return Stream.empty();
  }

  @Override
  public Optional<BytesValue> getExistingData(final WorldStateStorage worldStateStorage) {
    return worldStateStorage.getCode(getHash());
  }
}
