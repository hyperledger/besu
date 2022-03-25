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
package org.hyperledger.besu.ethereum.eth.transactions;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.util.number.Percentage;

import java.time.Duration;

import org.immutables.value.Value;

@Value.Immutable
@Value.Style(allParameters = true)
public interface TransactionPoolConfiguration {
  int DEFAULT_TX_MSG_KEEP_ALIVE = 60;
  int MAX_PENDING_TRANSACTIONS = 4096;
  int MAX_PENDING_TRANSACTIONS_HASHES = 4096;
  int DEFAULT_TX_RETENTION_HOURS = 13;
  boolean DEFAULT_STRICT_TX_REPLAY_PROTECTION_ENABLED = false;
  Percentage DEFAULT_PRICE_BUMP = Percentage.fromInt(10);
  Wei DEFAULT_RPC_TX_FEE_CAP = Wei.fromEth(1);
  Duration ETH65_TRX_ANNOUNCED_BUFFERING_PERIOD = Duration.ofMillis(500);

  TransactionPoolConfiguration DEFAULT = ImmutableTransactionPoolConfiguration.builder().build();

  @Value.Default
  default int getTxPoolMaxSize() {
    return MAX_PENDING_TRANSACTIONS;
  }

  @Value.Default
  default int getPendingTxRetentionPeriod() {
    return DEFAULT_TX_RETENTION_HOURS;
  }

  @Value.Default
  default int getTxMessageKeepAliveSeconds() {
    return DEFAULT_TX_MSG_KEEP_ALIVE;
  }

  @Value.Default
  default Percentage getPriceBump() {
    return DEFAULT_PRICE_BUMP;
  }

  @Value.Default
  default Duration getEth65TrxAnnouncedBufferingPeriod() {
    return ETH65_TRX_ANNOUNCED_BUFFERING_PERIOD;
  }

  @Value.Default
  default Wei getTxFeeCap() {
    return DEFAULT_RPC_TX_FEE_CAP;
  }

  @Value.Default
  default Boolean getStrictTransactionReplayProtectionEnabled() {
    return DEFAULT_STRICT_TX_REPLAY_PROTECTION_ENABLED;
  }
}
