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
package org.hyperledger.besu.ethereum.trie.pathbased.verkle.worldview;

import org.hyperledger.besu.datatypes.AccountValue;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.StorageSlotKey;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.trie.pathbased.common.PathBasedValue;
import org.hyperledger.besu.ethereum.trie.pathbased.common.worldview.PathBasedWorldState;
import org.hyperledger.besu.ethereum.trie.pathbased.common.worldview.PathBasedWorldView;
import org.hyperledger.besu.ethereum.trie.pathbased.common.worldview.accumulator.PathBasedWorldStateUpdateAccumulator;
import org.hyperledger.besu.ethereum.trie.pathbased.common.worldview.accumulator.preload.Consumer;
import org.hyperledger.besu.ethereum.trie.pathbased.verkle.VerkleAccount;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.worldstate.UpdateTrackingAccount;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

public class VerkleWorldStateUpdateAccumulator
    extends PathBasedWorldStateUpdateAccumulator<VerkleAccount> {

  public VerkleWorldStateUpdateAccumulator(
      final PathBasedWorldView world,
      final Consumer<PathBasedValue<VerkleAccount>> accountPreloader,
      final Consumer<StorageSlotKey> storagePreloader,
      final Consumer<Bytes> codePreloader,
      final EvmConfiguration evmConfiguration) {
    super(world, accountPreloader, storagePreloader, codePreloader, evmConfiguration);
  }

  @Override
  public PathBasedWorldStateUpdateAccumulator<VerkleAccount> copy() {
    final VerkleWorldStateUpdateAccumulator copy =
        new VerkleWorldStateUpdateAccumulator(
            wrappedWorldView(),
            getAccountPreloader(),
            getStoragePreloader(),
            getCodePreloader(),
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
      final VerkleAccount toCopy, final PathBasedWorldView context, final boolean mutable) {
    return new VerkleAccount(toCopy, context, mutable);
  }

  @Override
  protected VerkleAccount createAccount(
      final PathBasedWorldView context,
      final Address address,
      final AccountValue stateTrieAccount,
      final boolean mutable) {
    return new VerkleAccount(context, address, stateTrieAccount, mutable);
  }

  @Override
  protected VerkleAccount createAccount(
      final PathBasedWorldView context,
      final Address address,
      final Hash addressHash,
      final long nonce,
      final Wei balance,
      final boolean mutable) {
    return new VerkleAccount(context, address, addressHash, nonce, balance, 0, Hash.EMPTY, mutable);
  }

  @Override
  protected VerkleAccount createAccount(
      final PathBasedWorldView context, final UpdateTrackingAccount<VerkleAccount> tracked) {
    return new VerkleAccount(context, tracked);
  }

  @Override
  protected void assertCloseEnoughForDiffing(
      final VerkleAccount source, final AccountValue account, final String context) {
    VerkleAccount.assertCloseEnoughForDiffing(source, account, context);
  }

  @Override
  protected Optional<UInt256> getStorageValueByStorageSlotKey(
      final PathBasedWorldState worldState,
      final Address address,
      final StorageSlotKey storageSlotKey) {
    return worldState.getStorageValueByStorageSlotKey(address, storageSlotKey);
  }

  @Override
  protected boolean shouldIgnoreIdenticalValuesDuringAccountRollingUpdate() {
    return false;
  }
}
