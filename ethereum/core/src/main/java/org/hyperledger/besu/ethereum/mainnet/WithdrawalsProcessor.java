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
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.ethereum.core.Withdrawal;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.AccessLocationTracker;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.List;
import java.util.Optional;

public class WithdrawalsProcessor {

  public void processWithdrawals(
      final List<Withdrawal> withdrawals,
      final WorldUpdater withdrawalsUpdater,
      final Optional<AccessLocationTracker> accessLocationTracker) {
    for (final Withdrawal withdrawal : withdrawals) {
      final MutableAccount account = withdrawalsUpdater.getOrCreate(withdrawal.getAddress());
      account.incrementBalance(withdrawal.getAmount().getAsWei());
      accessLocationTracker.ifPresent(t -> t.addTouchedAccount(account.getAddress()));
    }
    withdrawalsUpdater.clearAccountsThatAreEmpty();
    withdrawalsUpdater.commit();
  }
}
