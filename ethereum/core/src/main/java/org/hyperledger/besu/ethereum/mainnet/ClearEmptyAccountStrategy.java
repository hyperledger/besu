/*
 * Copyright Hyperledger Besu Contributors.
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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.ArrayList;
import java.util.List;

/** A helper class to store the historical block hash. */
public interface ClearEmptyAccountStrategy {

  void process(final WorldUpdater worldUpdater);

  class NotClearEmptyAccount implements ClearEmptyAccountStrategy {
    @Override
    public void process(final WorldUpdater worldUpdater) {
      // nothing to do
    }
  }

  class ClearEmptyAccount implements ClearEmptyAccountStrategy {
    @Override
    public void process(final WorldUpdater worldUpdater) {
      new ArrayList<>(worldUpdater.getTouchedAccounts())
          .stream()
              .filter(Account::isEmpty)
              .forEach(a -> worldUpdater.deleteAccount(a.getAddress()));
    }
  }

  class ClearEmptyAccountWithException implements ClearEmptyAccountStrategy {

    List<Address> exceptionList;

    public ClearEmptyAccountWithException(final List<Address> exceptionList) {
      this.exceptionList = exceptionList;
    }

    @Override
    public void process(final WorldUpdater worldUpdater) {
      new ArrayList<>(worldUpdater.getTouchedAccounts())
          .stream()
              .filter(account -> !exceptionList.contains(account.getAddress()))
              .filter(Account::isEmpty)
              .forEach(a -> worldUpdater.deleteAccount(a.getAddress()));
    }
  }
}
