/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.evm.worldstate;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.MessageFrame;

import java.util.Collection;
import java.util.Optional;

/**
 * The EVM world updater. This class is a wrapper around a WorldUpdater that provides an
 * AuthorizedCodeService to manage the authorized code for accounts.
 */
public class EVMWorldUpdater implements WorldUpdater {
  private final WorldUpdater rootWorldUpdater;
  private final AuthorizedCodeService authorizedCodeService;

  /**
   * Instantiates a new EVM world updater.
   *
   * @param rootWorldUpdater the root world updater
   */
  public EVMWorldUpdater(final WorldUpdater rootWorldUpdater) {
    this(rootWorldUpdater, new AuthorizedCodeService());
  }

  private EVMWorldUpdater(
      final WorldUpdater rootWorldUpdater, final AuthorizedCodeService authorizedCodeService) {
    this.rootWorldUpdater = rootWorldUpdater;
    this.authorizedCodeService = authorizedCodeService;
  }

  /**
   * Authorized code service.
   *
   * @return the authorized code service
   */
  public AuthorizedCodeService authorizedCodeService() {
    return authorizedCodeService;
  }

  @Override
  public MutableAccount createAccount(final Address address, final long nonce, final Wei balance) {
    return authorizedCodeService.processMutableAccount(
        this, rootWorldUpdater.createAccount(address, nonce, balance), address);
  }

  @Override
  public MutableAccount getAccount(final Address address) {
    return authorizedCodeService.processMutableAccount(
        this, rootWorldUpdater.getAccount(address), address);
  }

  @Override
  public MutableAccount getOrCreate(final Address address) {
    return authorizedCodeService.processMutableAccount(
        this, rootWorldUpdater.getOrCreate(address), address);
  }

  @Override
  public MutableAccount getOrCreateSenderAccount(final Address address) {
    return authorizedCodeService.processMutableAccount(
        this, rootWorldUpdater.getOrCreateSenderAccount(address), address);
  }

  @Override
  public MutableAccount getSenderAccount(final MessageFrame frame) {
    return authorizedCodeService.processMutableAccount(
        this, rootWorldUpdater.getSenderAccount(frame), frame.getSenderAddress());
  }

  @Override
  public void deleteAccount(final Address address) {
    rootWorldUpdater.deleteAccount(address);
  }

  @Override
  public Collection<? extends Account> getTouchedAccounts() {
    return rootWorldUpdater.getTouchedAccounts();
  }

  @Override
  public Collection<Address> getDeletedAccountAddresses() {
    return rootWorldUpdater.getDeletedAccountAddresses();
  }

  @Override
  public void revert() {
    rootWorldUpdater.revert();
  }

  @Override
  public void commit() {
    rootWorldUpdater.commit();
  }

  @Override
  public Optional<WorldUpdater> parentUpdater() {
    return rootWorldUpdater.parentUpdater();
  }

  @Override
  public WorldUpdater updater() {
    return new EVMWorldUpdater(rootWorldUpdater.updater(), authorizedCodeService);
  }

  @Override
  public Account get(final Address address) {
    return authorizedCodeService.processAccount(this, rootWorldUpdater.get(address), address);
  }
}
