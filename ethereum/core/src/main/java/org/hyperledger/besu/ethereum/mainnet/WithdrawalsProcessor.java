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

package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.ethereum.core.Withdrawal;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface WithdrawalsProcessor {
  void processWithdrawal(final Withdrawal withdrawal, final WorldUpdater worldUpdater);

  class ProhibitedWithdrawalsProcessor implements WithdrawalsProcessor {
    @Override
    public void processWithdrawal(final Withdrawal withdrawal, final WorldUpdater worldUpdater) {
      throw new UnsupportedOperationException("Withdrawals are not allowed on this chain");
    }
  }

  class AllowedWithdrawalsProcessor implements WithdrawalsProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(AllowedWithdrawalsProcessor.class);

    @Override
    public void processWithdrawal(final Withdrawal withdrawal, final WorldUpdater worldUpdater) {
      try {
        final EvmAccount account = worldUpdater.getOrCreate(withdrawal.getAddress());
        account.getMutable().setBalance(account.getBalance().add(withdrawal.getAmount().getAsWei()));
        worldUpdater.commit();
      } catch (Exception e) {
        final String message =
            String.format(
                "failed to process withdrawal for address: %s of %s wei",
                withdrawal.getAddress(), withdrawal.getAmount());
        LOG.error(message, e);
      }
    }
  }
}
