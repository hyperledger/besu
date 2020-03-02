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

import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class PrivateWorldStateReader {

  private final PrivateStateRootResolver privateStateRootResolver;
  private final WorldStateArchive privateWorldStateArchive;

  public PrivateWorldStateReader(
      final PrivateStateRootResolver privateStateRootResolver,
      final WorldStateArchive privateWorldStateArchive) {
    this.privateStateRootResolver = privateStateRootResolver;
    this.privateWorldStateArchive = privateWorldStateArchive;
  }

  public Optional<Bytes> getContractCode(
      final String privacyGroupId, final Hash blockHash, final Address contractAddress) {
    final Hash latestStateRoot =
        privateStateRootResolver.resolveLastStateRoot(
            Bytes32.wrap(Bytes.fromBase64String(privacyGroupId)), blockHash);

    return privateWorldStateArchive
        .get(latestStateRoot)
        .flatMap(worldState -> Optional.ofNullable(worldState.get(contractAddress)))
        .flatMap(account -> Optional.ofNullable(account.getCode()));
  }
}
