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
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.List;

public class WithdrawalsProcessor {

  public void processWithdrawals(
      final List<Withdrawal> withdrawals, final WorldUpdater worldUpdater) {
    for (final Withdrawal withdrawal : withdrawals) {
      final MutableAccount account = worldUpdater.getOrCreate(withdrawal.getAddress());
      account.incrementBalance(withdrawal.getAmount().getAsWei());
    }
    worldUpdater.clearAccountsThatAreEmpty();
    worldUpdater.commit();
  }
}
