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
 *
 */

package org.hyperledger.besu.ethereum.trie.diffbased.verkle.worldview;

import org.hyperledger.besu.datatypes.AccountValue;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.trie.diffbased.common.DiffBasedValue;
import org.hyperledger.besu.ethereum.trie.diffbased.common.worldview.DiffBasedWorldState;
import org.hyperledger.besu.ethereum.trie.diffbased.common.worldview.DiffBasedWorldView;
import org.hyperledger.besu.ethereum.trie.diffbased.common.worldview.accumulator.DiffBasedWorldStateUpdateAccumulator;
import org.hyperledger.besu.ethereum.trie.diffbased.common.worldview.accumulator.preload.Consumer;
import org.hyperledger.besu.ethereum.trie.diffbased.verkle.VerkleAccount;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.worldstate.UpdateTrackingAccount;

import java.util.Optional;

import org.apache.tuweni.units.bigints.UInt256;

public class VerkleWorldStateUpdateAccumulator
    extends DiffBasedWorldStateUpdateAccumulator<VerkleAccount> {

  public VerkleWorldStateUpdateAccumulator(
      final DiffBasedWorldView world,
      final Consumer<DiffBasedValue<VerkleAccount>> accountPreloader,
      final Consumer<StorageSlotKey> storagePreloader,
      final EvmConfiguration evmConfiguration) {
    super(world, accountPreloader, storagePreloader, evmConfiguration);
  }

  @Override
  public DiffBasedWorldStateUpdateAccumulator<VerkleAccount> copy() {
    final VerkleWorldStateUpdateAccumulator copy =
        new VerkleWorldStateUpdateAccumulator(
            wrappedWorldView(),
            getAccountPreloader(),
            getStoragePreloader(),
            getEvmConfiguration());
    copy.cloneFromUpdater(this);
    return copy;
  }

  @Override
  protected VerkleAccount copyAccount(final VerkleAccount account) {
    return new VerkleAccount(account);
  }

  @Override
  protected VerkleAccount copyAccount(
      final VerkleAccount toCopy, final DiffBasedWorldView context, final boolean mutable) {
    return new VerkleAccount(toCopy, context, mutable);
  }

  @Override
  protected VerkleAccount createAccount(
      final DiffBasedWorldView context,
      final Address address,
      final AccountValue stateTrieAccount,
      final boolean mutable) {
    return new VerkleAccount(context, address, stateTrieAccount, mutable);
  }

  @Override
  protected VerkleAccount createAccount(
      final DiffBasedWorldView context,
      final Address address,
      final Hash addressHash,
      final long nonce,
      final Wei balance,
      final Hash storageRoot,
      final Hash codeHash,
      final boolean mutable) {
    return new VerkleAccount(
        context, address, addressHash, nonce, balance, storageRoot, codeHash, mutable);
  }

  @Override
  protected VerkleAccount createAccount(
      final DiffBasedWorldView context, final UpdateTrackingAccount<VerkleAccount> tracked) {
    return new VerkleAccount(context, tracked);
  }

  @Override
  protected void assertCloseEnoughForDiffing(
      final VerkleAccount source, final AccountValue account, final String context) {
    VerkleAccount.assertCloseEnoughForDiffing(source, account, context);
  }

  @Override
  public UInt256 getStorageValue(final Address address, final UInt256 slotKey) {
    StorageSlotKey storageSlotKey =
            new StorageSlotKey(hashAndSaveSlotPreImage(slotKey), Optional.of(slotKey));
    return getStorageValueByStorageSlotKey(address, storageSlotKey).orElse(null);
  }

  @Override
  protected Optional<UInt256> getStorageValueByStorageSlotKey(
      final DiffBasedWorldState worldState,
      final Address address,
      final StorageSlotKey storageSlotKey) {
    return worldState.getStorageValueByStorageSlotKey(address, storageSlotKey);
  }

  @Override
  protected boolean shouldIgnoreIdenticalValuesDuringAccountRollingUpdate() {
    return false;
  }
}
