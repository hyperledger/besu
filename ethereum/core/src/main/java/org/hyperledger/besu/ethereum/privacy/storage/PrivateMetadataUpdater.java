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
package org.hyperledger.besu.ethereum.privacy.storage;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionReceipt;

import java.util.HashMap;
import java.util.Map;

import org.apache.tuweni.bytes.Bytes32;

public class PrivateMetadataUpdater {

  private final PrivateStateStorage privateStateKeyValueStorage;
  private final BlockHeader blockHeader;
  private final PrivateStateStorage.Updater updater;
  private final PrivacyGroupHeadBlockMap privacyGroupHeadBlockMap;
  private final Map<Bytes32, PrivateBlockMetadata> privateBlockMetadataMap = new HashMap<>();

  public PrivateMetadataUpdater(
      final BlockHeader blockHeader, final PrivateStateStorage keyValueStorage) {
    this.privateStateKeyValueStorage = keyValueStorage;
    this.blockHeader = blockHeader;
    this.updater = privateStateKeyValueStorage.updater();
    this.privacyGroupHeadBlockMap =
        privateStateKeyValueStorage
            .getPrivacyGroupHeadBlockMap(blockHeader.getParentHash())
            .orElse(PrivacyGroupHeadBlockMap.empty());
  }

  public PrivateBlockMetadata getPrivateBlockMetadata(final Bytes32 privacyGroupId) {
    return privateBlockMetadataMap.get(privacyGroupId);
  }

  public PrivacyGroupHeadBlockMap getPrivacyGroupHeadBlockMap() {
    return privacyGroupHeadBlockMap;
  }

  public void putTransactionReceipt(
      final Bytes32 transactionHash, final PrivateTransactionReceipt receipt) {
    updater.putTransactionReceipt(blockHeader.getBlockHash(), transactionHash, receipt);
  }

  public void addPrivateTransactionMetadata(
      final Bytes32 privacyGroupId, final PrivateTransactionMetadata metadata) {
    PrivateBlockMetadata privateBlockMetadata = privateBlockMetadataMap.get(privacyGroupId);
    if (privateBlockMetadata == null) {
      privateBlockMetadata = PrivateBlockMetadata.empty();
    }
    privateBlockMetadata.addPrivateTransactionMetadata(metadata);
    privateBlockMetadataMap.put(privacyGroupId, privateBlockMetadata);
  }

  public void updatePrivacyGroupHeadBlockMap(final Bytes32 privacyGroupId) {
    privacyGroupHeadBlockMap.put(privacyGroupId, blockHeader.getHash());
  }

  public void commit() {
    if (privacyGroupHeadBlockMap.size() > 0) {
      updater.putPrivacyGroupHeadBlockMap(blockHeader.getHash(), privacyGroupHeadBlockMap);
    }
    privateBlockMetadataMap.entrySet().stream()
        .forEach(
            entry ->
                updater.putPrivateBlockMetadata(
                    blockHeader.getHash(), entry.getKey(), entry.getValue()));

    updater.commit();
  }
}
