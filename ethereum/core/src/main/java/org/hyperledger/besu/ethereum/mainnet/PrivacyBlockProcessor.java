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
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.EnclaveClientException;
import org.hyperledger.besu.enclave.types.ReceiveResponse;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.privacy.PrivateStateRehydration;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionWithMetadata;
import org.hyperledger.besu.ethereum.privacy.storage.PrivacyGroupHeadBlockMap;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class PrivacyBlockProcessor implements BlockProcessor {
  private final BlockProcessor blockProcessor;
  private final ProtocolSchedule<?> protocolSchedule;
  private final Enclave enclave;
  private final PrivateStateStorage privateStateStorage;
  private final WorldStateArchive privateWorldStateArchive;
  private WorldStateArchive publicWorldStateArchive;

  public <C> PrivacyBlockProcessor(
      final BlockProcessor blockProcessor,
      final ProtocolSchedule<C> protocolSchedule,
      final Enclave enclave,
      final PrivateStateStorage privateStateStorage,
      final WorldStateArchive privateWorldStateArchive) {
    this.blockProcessor = blockProcessor;
    this.protocolSchedule = protocolSchedule;
    this.enclave = enclave;
    this.privateStateStorage = privateStateStorage;
    this.privateWorldStateArchive = privateWorldStateArchive;
  }

  public void setPublicWorldStateArchive(final WorldStateArchive publicWorldStateArchive) {
    this.publicWorldStateArchive = publicWorldStateArchive;
  }

  @Override
  public Result processBlock(
      final Blockchain blockchain,
      final MutableWorldState worldState,
      final BlockHeader blockHeader,
      final List<Transaction> transactions,
      final List<BlockHeader> ommers) {
    final PrivacyGroupHeadBlockMap preProcessPrivacyGroupHeadBlockMap =
        new PrivacyGroupHeadBlockMap(
            privateStateStorage
                .getPrivacyGroupHeadBlockMap(blockHeader.getParentHash())
                .orElse(PrivacyGroupHeadBlockMap.EMPTY));
    transactions.stream()
        .filter(
            t ->
                t.getTo().isPresent()
                    && t.getTo().equals(Optional.of(Address.ONCHAIN_PRIVACY))
                    && t.getPayload().size() == 64)
        .forEach(
            t -> {
              final Bytes32 addKey = Bytes32.wrap(t.getPayload().slice(32, 32));
              try {
                final ReceiveResponse receiveResponse = enclave.receive(addKey.toBase64String());
                final List<PrivateTransactionWithMetadata> privateTransactionWithMetadataList =
                    deserializeAddToGroupPayload(
                        Bytes.wrap(Base64.getDecoder().decode(receiveResponse.getPayload())));
                final Bytes32 privacyGroupId =
                    Bytes32.wrap(
                        privateTransactionWithMetadataList
                            .get(0)
                            .getPrivateTransaction()
                            .getPrivacyGroupId()
                            .get());
                if (!preProcessPrivacyGroupHeadBlockMap.containsKey(privacyGroupId)) {
                  final PrivateStateRehydration privateStateRehydration =
                      new PrivateStateRehydration(
                          privateStateStorage,
                          blockchain,
                          protocolSchedule,
                          publicWorldStateArchive,
                          privateWorldStateArchive);
                  privateStateRehydration.rehydrate(privateTransactionWithMetadataList);
                  privateStateStorage.updater().putAddDataKey(privacyGroupId, addKey).commit();
                }
              } catch (final EnclaveClientException e) {
                // we were not being added
              }
            });
    final PrivacyGroupHeadBlockMap privacyGroupHeadBlockMap =
        new PrivacyGroupHeadBlockMap(
            privateStateStorage
                .getPrivacyGroupHeadBlockMap(blockHeader.getParentHash())
                .orElse(PrivacyGroupHeadBlockMap.EMPTY));
    privateStateStorage
        .updater()
        .putPrivacyGroupHeadBlockMap(blockHeader.getHash(), privacyGroupHeadBlockMap)
        .commit();
    return blockProcessor.processBlock(blockchain, worldState, blockHeader, transactions, ommers);
  }

  private List<PrivateTransactionWithMetadata> deserializeAddToGroupPayload(
      final Bytes encodedAddToGroupPayload) {
    final ArrayList<PrivateTransactionWithMetadata> deserializedResponse = new ArrayList<>();
    final BytesValueRLPInput bytesValueRLPInput =
        new BytesValueRLPInput(encodedAddToGroupPayload, false);
    final int noOfEntries = bytesValueRLPInput.enterList();
    for (int i = 0; i < noOfEntries; i++) {
      deserializedResponse.add(PrivateTransactionWithMetadata.readFrom(bytesValueRLPInput));
    }
    bytesValueRLPInput.leaveList();
    return deserializedResponse;
  }
}
