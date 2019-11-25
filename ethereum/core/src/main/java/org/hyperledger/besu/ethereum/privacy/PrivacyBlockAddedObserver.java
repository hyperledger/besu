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

import static org.apache.logging.log4j.LogManager.getLogger;

import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.EnclaveException;
import org.hyperledger.besu.enclave.types.ReceiveRequest;
import org.hyperledger.besu.enclave.types.ReceiveResponse;
import org.hyperledger.besu.ethereum.chain.BlockAddedEvent;
import org.hyperledger.besu.ethereum.chain.BlockAddedObserver;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateGroupIdToLatestBlockWithTransactionMap;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;
import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.bytes.BytesValues;

import java.util.Collections;
import java.util.HashMap;

import com.google.common.collect.Maps;
import org.apache.logging.log4j.Logger;

public class PrivacyBlockAddedObserver implements BlockAddedObserver {

  private static final Logger LOG = getLogger();

  private PrivateStateStorage privateStateStorage;
  private Address address;
  private Enclave enclave;

  public PrivacyBlockAddedObserver(final PrivacyParameters privacyParameters) {
    setUpForPrivacy(privacyParameters);
  }

  @Override
  public void onBlockAdded(final BlockAddedEvent event, final Blockchain blockchain) {
    final Block block = event.getBlock();
    final HashMap<Bytes32, Hash> privacyGroupToLatestBlockWithTransactionMap =
        Maps.newHashMap(
            privateStateStorage
                .getPrivacyGroupToLatestBlockWithTransactionMap(block.getHeader().getParentHash())
                .map(PrivateGroupIdToLatestBlockWithTransactionMap::getMap)
                .orElse(Collections.emptyMap()));
    block.getBody().getTransactions().stream()
        .filter(t -> t.getTo().isPresent() && t.getTo().get().equals(address))
        .forEach(
            t -> {
              final String key = BytesValues.asBase64String(t.getData().get());
              try {
                final ReceiveResponse receiveResponse = enclave.receive(new ReceiveRequest(key));
                final BytesValue privacyGroupId =
                    BytesValues.fromBase64(receiveResponse.getPrivacyGroupId());
                privacyGroupToLatestBlockWithTransactionMap.put(
                    Bytes32.wrap(privacyGroupId), block.getHash());
              } catch (final EnclaveException e) {
                LOG.warn("Enclave does not have private transaction with key {}.", key, e);
              }
            });
    privateStateStorage
        .updater()
        .putPrivacyGroupToLatestBlockWithTransactionMap(
            block.getHash(),
            new PrivateGroupIdToLatestBlockWithTransactionMap(
                privacyGroupToLatestBlockWithTransactionMap))
        .commit();
  }

  private void setUpForPrivacy(final PrivacyParameters privacyParameters) {
    if (privacyParameters.isEnabled()) {
      privateStateStorage = privacyParameters.getPrivateStateStorage();
      address = Address.privacyPrecompiled(privacyParameters.getPrivacyAddress());
      enclave = new Enclave(privacyParameters.getEnclaveUri());
    }
  }
}
