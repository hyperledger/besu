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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.core.PrivacyParameters.DEFAULT_FLEXIBLE_PRIVACY_MANAGEMENT;
import static org.hyperledger.besu.ethereum.core.PrivacyParameters.FLEXIBLE_PRIVACY_PROXY;
import static org.hyperledger.besu.ethereum.privacy.group.FlexibleGroupManagement.DEFAULT_GROUP_MANAGEMENT_RUNTIME_BYTECODE;
import static org.hyperledger.besu.ethereum.privacy.group.FlexibleGroupManagement.PROXY_RUNTIME_BYTECODE;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.trie.MerklePatriciaTrie;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.plugin.data.PrivacyGenesis;
import org.hyperledger.besu.plugin.data.PrivacyGenesisAccount;
import org.hyperledger.besu.plugin.data.Quantity;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.Test;

public class PrivateStateGenesisAllocatorTest {
  public static final Hash EMPTY_ROOT_HASH = Hash.wrap(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH);

  private final MutableWorldState worldState =
      InMemoryKeyValueStorageProvider.createInMemoryWorldState();

  final WorldUpdater updater = worldState.updater();

  Address genesisAddress = Address.fromHexString("0x1000000000000000000000000000000000000001");

  PrivacyGenesis privacyGenesis =
      () ->
          List.of(
              new PrivacyGenesisAccount() {
                @Override
                public org.hyperledger.besu.plugin.data.Address getAddress() {
                  return genesisAddress;
                }

                @Override
                public Map<UInt256, UInt256> getStorage() {
                  return Collections.emptyMap();
                }

                @Override
                public Long getNonce() {
                  return 0L;
                }

                @Override
                public Quantity getBalance() {
                  return Wei.ONE;
                }

                @Override
                public Bytes getCode() {
                  return Bytes.fromHexString("0x42");
                }
              });

  @Test
  public void whenOnchainDisabledAndNoAccountsProvidedNoGenesisIsApplied() {
    PrivateStateGenesisAllocator privateStateGenesisAllocator =
        new PrivateStateGenesisAllocator(
            false, (privacyGroupId, blockNumber) -> Collections::emptyList);

    privateStateGenesisAllocator.applyGenesisToPrivateWorldState(
        worldState, updater, Bytes.EMPTY, 0);

    assertThat(worldState.frontierRootHash()).isEqualTo(EMPTY_ROOT_HASH);
  }

  @Test
  public void whenOnchainEnabledAndNoAccountsProvidedPrivacyManagementContractIsApplied() {
    PrivateStateGenesisAllocator privateStateGenesisAllocator =
        new PrivateStateGenesisAllocator(
            true, (privacyGroupId, blockNumber) -> Collections::emptyList);

    privateStateGenesisAllocator.applyGenesisToPrivateWorldState(
        worldState, updater, Bytes.EMPTY, 0);

    assertThat(worldState.frontierRootHash()).isNotEqualTo(EMPTY_ROOT_HASH);

    assertManagementContractApplied();
  }

  @Test
  public void whenOnchainEnabledAndAccountsProvidedPrivacyManagementContractAndGenesisIsApplied() {
    PrivateStateGenesisAllocator privateStateGenesisAllocator =
        new PrivateStateGenesisAllocator(true, (privacyGroupId, blockNumber) -> privacyGenesis);

    privateStateGenesisAllocator.applyGenesisToPrivateWorldState(
        worldState, updater, Bytes.EMPTY, 0);

    assertThat(worldState.frontierRootHash()).isNotEqualTo(EMPTY_ROOT_HASH);

    assertManagementContractApplied();
    assertGenesisAccountApplied();
  }

  @Test
  public void whenOnchainDisabledAndAccountsProvidedPrivacyManagementContractAndGenesisIsApplied() {
    PrivateStateGenesisAllocator privateStateGenesisAllocator =
        new PrivateStateGenesisAllocator(false, (privacyGroupId, blockNumber) -> privacyGenesis);

    privateStateGenesisAllocator.applyGenesisToPrivateWorldState(
        worldState, updater, Bytes.EMPTY, 0);

    assertThat(worldState.frontierRootHash()).isNotEqualTo(EMPTY_ROOT_HASH);

    assertThat(worldState.get(FLEXIBLE_PRIVACY_PROXY)).isEqualTo(null);

    assertGenesisAccountApplied();
  }

  private void assertGenesisAccountApplied() {
    Account genesisAccount = worldState.get(genesisAddress);

    assertThat(genesisAccount.getCode()).isEqualTo(Bytes.fromHexString("0x42"));
    assertThat(genesisAccount.getBalance()).isEqualTo(Wei.ONE);
  }

  private void assertManagementContractApplied() {
    Account managementProxy = worldState.get(FLEXIBLE_PRIVACY_PROXY);
    assertThat(managementProxy.getCode()).isEqualTo(PROXY_RUNTIME_BYTECODE);
    assertThat(managementProxy.getStorageValue(UInt256.ZERO))
        .isEqualTo(UInt256.fromBytes(DEFAULT_FLEXIBLE_PRIVACY_MANAGEMENT));

    Account managementContract = worldState.get(DEFAULT_FLEXIBLE_PRIVACY_MANAGEMENT);
    assertThat(managementContract.getCode()).isEqualTo(DEFAULT_GROUP_MANAGEMENT_RUNTIME_BYTECODE);
  }
}
