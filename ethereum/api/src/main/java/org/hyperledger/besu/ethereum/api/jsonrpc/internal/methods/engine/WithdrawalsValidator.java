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

package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.engine;

import org.hyperledger.besu.ethereum.core.Withdrawal;
import org.hyperledger.besu.ethereum.mainnet.TimestampSchedule;

import java.util.List;
import java.util.Optional;

public class WithdrawalsValidator {

  static boolean isWithdrawalsValid(
      final TimestampSchedule timestampSchedule,
      final long timestamp,
      final Optional<List<Withdrawal>> maybeWithdrawals) {
    final List<Withdrawal> withdrawals = maybeWithdrawals.orElse(null);

    return timestampSchedule
        .getByTimestamp(timestamp)
        .map(
            protocolSpec -> protocolSpec.getWithdrawalsValidator().validateWithdrawals(withdrawals))
        // TODO Withdrawals this is a quirk of the fact timestampSchedule doesn't fallback to the
        // previous fork. This might be resolved when
        // https://github.com/hyperledger/besu/issues/4789 is played
        // and if we can combine protocolSchedule and timestampSchedule.
        .orElseGet(
            () ->
                new org.hyperledger.besu.ethereum.mainnet.WithdrawalsValidator
                        .ProhibitedWithdrawals()
                    .validateWithdrawals(withdrawals));
  }
}
