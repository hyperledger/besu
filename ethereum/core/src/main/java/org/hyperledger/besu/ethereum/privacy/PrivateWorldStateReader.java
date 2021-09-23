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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateBlockMetadata;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateTransactionMetadata;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class PrivateWorldStateReader {

  private final PrivateStateRootResolver privateStateRootResolver;
  private final WorldStateArchive privateWorldStateArchive;
  private final PrivateStateStorage privateStateStorage;

  public PrivateWorldStateReader(
      final PrivateStateRootResolver privateStateRootResolver,
      final WorldStateArchive privateWorldStateArchive,
      final PrivateStateStorage privateStateStorage) {
    this.privateStateRootResolver = privateStateRootResolver;
    this.privateWorldStateArchive = privateWorldStateArchive;
    this.privateStateStorage = privateStateStorage;
  }

  public Optional<Bytes> getContractCode(
      final String privacyGroupId, final Hash blockHash, final Address contractAddress) {
    final Hash latestStateRoot =
        privateStateRootResolver.resolveLastStateRoot(
            Bytes32.wrap(Bytes.fromBase64String(privacyGroupId)), blockHash);

    return privateWorldStateArchive
        .get(latestStateRoot, blockHash)
        .flatMap(worldState -> Optional.ofNullable(worldState.get(contractAddress)))
        .flatMap(account -> Optional.ofNullable(account.getCode()));
  }

  public List<PrivateTransactionMetadata> getPrivateTransactionMetadataList(
      final String privacyGroupId, final Hash blockHash) {
    final Bytes32 privacyGroupIdBytes = Bytes32.wrap(Bytes.fromBase64String(privacyGroupId));
    final Optional<PrivateBlockMetadata> privateBlockMetadata =
        privateStateStorage.getPrivateBlockMetadata(blockHash, privacyGroupIdBytes);

    return privateBlockMetadata
        .map(PrivateBlockMetadata::getPrivateTransactionMetadataList)
        .orElse(Collections.emptyList());
  }

  public Optional<PrivateTransactionReceipt> getPrivateTransactionReceipt(
      final Hash blockHash, final Hash transactionHash) {
    return privateStateStorage.getTransactionReceipt(blockHash, transactionHash);
  }
}
