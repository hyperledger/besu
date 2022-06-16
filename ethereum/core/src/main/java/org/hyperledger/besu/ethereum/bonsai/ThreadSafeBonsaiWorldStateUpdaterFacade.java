/*
 *
 *  * Copyright Hyperledger Besu Contributors.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  * the License. You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 *  * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *  * specific language governing permissions and limitations under the License.
 *  *
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.hyperledger.besu.ethereum.bonsai;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

public class ThreadSafeBonsaiWorldStateUpdaterFacade implements BonsaiWorldView, WorldUpdater {

  final BonsaiWorldStateUpdater delegate;

  public ThreadSafeBonsaiWorldStateUpdaterFacade(final BonsaiWorldStateUpdater delegate) {
    this.delegate = delegate;
  }

  @Override
  public synchronized Optional<Bytes> getCode(final Address address) {
    return delegate.getCode(address);
  }

  @Override
  public synchronized Optional<Bytes> getStateTrieNode(final Bytes location) {
    return delegate.getStateTrieNode(location);
  }

  @Override
  public synchronized UInt256 getStorageValue(final Address address, final UInt256 key) {
    return delegate.getStorageValue(address, key);
  }

  @Override
  public synchronized Optional<UInt256> getStorageValueBySlotHash(
      final Address address, final Hash slotHash) {
    return delegate.getStorageValueBySlotHash(address, slotHash);
  }

  @Override
  public synchronized UInt256 getPriorStorageValue(final Address address, final UInt256 key) {
    return delegate.getPriorStorageValue(address, key);
  }

  @Override
  public synchronized Map<Bytes32, Bytes> getAllAccountStorage(
      final Address address, final Hash rootHash) {
    return Collections.unmodifiableMap(delegate.getAllAccountStorage(address, rootHash));
  }

  @Override
  public synchronized Account get(final Address address) {
    return delegate.get(address);
  }

  @Override
  public synchronized WorldUpdater updater() {
    return delegate.updater();
  }

  @Override
  public synchronized EvmAccount createAccount(
      final Address address, final long nonce, final Wei balance) {
    // todo EvmAccount is not thread safe...
    return delegate.createAccount(address, nonce, balance);
  }

  @Override
  public synchronized EvmAccount createAccount(final Address address) {
    // todo EvmAccount is not thread safe...
    return delegate.createAccount(address);
  }

  @Override
  public synchronized EvmAccount getOrCreate(final Address address) {
    // todo EvmAccount is not thread safe...
    return delegate.getOrCreate(address);
  }

  @Override
  public synchronized EvmAccount getOrCreateSenderAccount(final Address address) {
    // todo EvmAccount is not thread safe...
    return delegate.getOrCreateSenderAccount(address);
  }

  @Override
  public synchronized EvmAccount getAccount(final Address address) {
    // todo EvmAccount is not thread safe...
    return delegate.getAccount(address);
  }

  @Override
  public synchronized EvmAccount getSenderAccount(final MessageFrame frame) {
    // todo EvmAccount is not thread safe...
    return delegate.getSenderAccount(frame);
  }

  @Override
  public synchronized void deleteAccount(final Address address) {
    delegate.deleteAccount(address);
  }

  @Override
  public synchronized Collection<? extends Account> getTouchedAccounts() {
    return delegate.getTouchedAccounts();
  }

  @Override
  public synchronized Collection<Address> getDeletedAccountAddresses() {
    return delegate.getDeletedAccountAddresses();
  }

  @Override
  public synchronized void revert() {
    delegate.revert();
  }

  @Override
  public synchronized void commit() {
    delegate.commit();
  }

  @Override
  public synchronized Optional<WorldUpdater> parentUpdater() {
    // todo: parentUpdater might not be threadSafe...
    return delegate.parentUpdater();
  }

  protected synchronized void reset() {
    delegate.reset();
  }

  public synchronized Set<Address> getStorageToClear() {
    return new HashSet<>(delegate.getStorageToClear());
  }

  public synchronized TrieLogLayer generateTrieLog(final Hash blockHash) {
    // todo: TrieLogLayer is not ThreadSafe...
    return delegate.generateTrieLog(blockHash);
  }

  public synchronized Map<Address, Map<Hash, BonsaiValue<UInt256>>> getStorageToUpdate() {
    // todo BonsaiValue is not thread safe...
    return Collections.unmodifiableMap(delegate.getStorageToUpdate());
  }

  public synchronized Map<Address, BonsaiValue<BonsaiAccount>> getAccountsToUpdate() {
    // todo BonsaiValue is not thread safe...
    return Collections.unmodifiableMap(delegate.getAccountsToUpdate());
  }

  public synchronized Map<Address, BonsaiValue<Bytes>> getCodeToUpdate() {
    // todo BonsaiValue is not thread safe...
    return Collections.unmodifiableMap(delegate.getCodeToUpdate());
  }

  public synchronized void rollBack(final TrieLogLayer layer) {
    delegate.rollBack(layer);
  }

  public synchronized void rollForward(final TrieLogLayer layer) {
    delegate.rollForward(layer);
  }
}
