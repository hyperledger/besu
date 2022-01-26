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

import static org.hyperledger.besu.ethereum.core.PrivacyParameters.DEFAULT_FLEXIBLE_PRIVACY_MANAGEMENT;
import static org.hyperledger.besu.ethereum.core.PrivacyParameters.FLEXIBLE_PRIVACY_PROXY;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.privacy.group.FlexibleGroupManagement;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.plugin.data.PrivacyGenesis;
import org.hyperledger.besu.plugin.services.privacy.PrivacyGroupGenesisProvider;

import java.math.BigInteger;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PrivateStateGenesisAllocator {
  private static final Logger LOG = LoggerFactory.getLogger(PrivateStateGenesisAllocator.class);

  private final Boolean isFlexiblePrivacyEnabled;
  private final PrivacyGroupGenesisProvider privacyGroupGenesisProvider;

  public PrivateStateGenesisAllocator(
      final Boolean isFlexiblePrivacyEnabled,
      final PrivacyGroupGenesisProvider privacyGroupGenesisProvider) {
    this.isFlexiblePrivacyEnabled = isFlexiblePrivacyEnabled;
    this.privacyGroupGenesisProvider = privacyGroupGenesisProvider;
  }

  public void applyGenesisToPrivateWorldState(
      final MutableWorldState disposablePrivateState,
      final WorldUpdater privateWorldStateUpdater,
      final Bytes privacyGroupId,
      final long blockNumber) {

    final PrivacyGenesis genesis =
        privacyGroupGenesisProvider.getPrivacyGenesis(privacyGroupId, blockNumber);

    if (genesis.getAccounts().size() > 0) {

      LOG.debug(
          "Applying {} privacy accounts onto {} private state genesis at {}",
          genesis.getAccounts().size(),
          privacyGroupId,
          blockNumber);

      genesis
          .getAccounts()
          .forEach(
              (genesisAccount) -> {
                final Address address = Address.fromPlugin(genesisAccount.getAddress());
                if (address.toBigInteger().compareTo(BigInteger.valueOf(Byte.MAX_VALUE)) < 0) {
                  LOG.warn(
                      "Genesis address {} is in reserved range and may be overwritten", address);
                }

                final MutableAccount account =
                    privateWorldStateUpdater.createAccount(address).getMutable();

                LOG.debug("{} applied to genesis", address.toHexString());

                account.setNonce(genesisAccount.getNonce());
                account.setBalance(Wei.fromQuantity(genesisAccount.getBalance()));
                account.setCode(genesisAccount.getCode());

                genesisAccount.getStorage().forEach(account::setStorageValue);
              });
    }

    if (isFlexiblePrivacyEnabled) {
      // inject management
      final MutableAccount managementContract =
          privateWorldStateUpdater.createAccount(DEFAULT_FLEXIBLE_PRIVACY_MANAGEMENT).getMutable();

      // this is the code for the simple management contract
      managementContract.setCode(FlexibleGroupManagement.DEFAULT_GROUP_MANAGEMENT_RUNTIME_BYTECODE);

      // inject proxy
      final MutableAccount procyContract =
          privateWorldStateUpdater.createAccount(FLEXIBLE_PRIVACY_PROXY).getMutable();

      // this is the code for the proxy contract
      procyContract.setCode(FlexibleGroupManagement.PROXY_RUNTIME_BYTECODE);
      // manually set the management contract address so the proxy can trust it
      procyContract.setStorageValue(
          UInt256.ZERO, UInt256.fromBytes(Bytes32.leftPad(DEFAULT_FLEXIBLE_PRIVACY_MANAGEMENT)));
    }

    privateWorldStateUpdater.commit();
    disposablePrivateState.persist(null);
  }
}
