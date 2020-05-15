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

import org.hyperledger.besu.util.number.Percentage;

import java.util.Objects;

public class TransactionPoolConfiguration {
  public static final int DEFAULT_TX_MSG_KEEP_ALIVE = 60;
  public static final int MAX_PENDING_TRANSACTIONS = 4096;
  public static final int MAX_PENDING_TRANSACTIONS_HASHES = 4096;
  public static final int DEFAULT_TX_RETENTION_HOURS = 13;
  public static final Percentage DEFAULT_PRICE_BUMP = Percentage.fromInt(10);

  private final int txPoolMaxSize;
  private final int pooledTransactionHashesSize;
  private final int pendingTxRetentionPeriod;
  private final int txMessageKeepAliveSeconds;

  private final Percentage priceBump;

  public TransactionPoolConfiguration(
      final int txPoolMaxSize,
      final int pooledTransactionHashesSize,
      final int pendingTxRetentionPeriod,
      final int txMessageKeepAliveSeconds,
      final Percentage priceBump) {
    this.txPoolMaxSize = txPoolMaxSize;
    this.pooledTransactionHashesSize = pooledTransactionHashesSize;
    this.pendingTxRetentionPeriod = pendingTxRetentionPeriod;
    this.txMessageKeepAliveSeconds = txMessageKeepAliveSeconds;
    this.priceBump = priceBump;
  }

  public int getTxPoolMaxSize() {
    return txPoolMaxSize;
  }

  public int getPooledTransactionHashesSize() {
    return pooledTransactionHashesSize;
  }

  public int getPendingTxRetentionPeriod() {
    return pendingTxRetentionPeriod;
  }

  public int getTxMessageKeepAliveSeconds() {
    return txMessageKeepAliveSeconds;
  }

  public Percentage getPriceBump() {
    return priceBump;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final TransactionPoolConfiguration that = (TransactionPoolConfiguration) o;
    return txPoolMaxSize == that.txPoolMaxSize
        && Objects.equals(pendingTxRetentionPeriod, that.pendingTxRetentionPeriod)
        && Objects.equals(txMessageKeepAliveSeconds, that.txMessageKeepAliveSeconds)
        && Objects.equals(priceBump, that.priceBump);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        txPoolMaxSize, pendingTxRetentionPeriod, txMessageKeepAliveSeconds, priceBump);
  }

  @Override
  public String toString() {
    return "TransactionPoolConfiguration{"
        + "txPoolMaxSize="
        + txPoolMaxSize
        + ", pendingTxRetentionPeriod="
        + pendingTxRetentionPeriod
        + ", txMessageKeepAliveSeconds="
        + txMessageKeepAliveSeconds
        + ", priceBump="
        + priceBump
        + '}';
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private int txPoolMaxSize = MAX_PENDING_TRANSACTIONS;
    private int pendingTxRetentionPeriod = DEFAULT_TX_RETENTION_HOURS;
    private Integer txMessageKeepAliveSeconds = DEFAULT_TX_MSG_KEEP_ALIVE;
    private int pooledTransactionHashesSize = MAX_PENDING_TRANSACTIONS_HASHES;
    private Percentage priceBump = DEFAULT_PRICE_BUMP;

    public Builder txPoolMaxSize(final int txPoolMaxSize) {
      this.txPoolMaxSize = txPoolMaxSize;
      return this;
    }

    public Builder pooledTransactionHashesSize(final int pooledTransactionHashesSize) {
      this.pooledTransactionHashesSize = pooledTransactionHashesSize;
      return this;
    }

    public Builder pendingTxRetentionPeriod(final int pendingTxRetentionPeriod) {
      this.pendingTxRetentionPeriod = pendingTxRetentionPeriod;
      return this;
    }

    public Builder txMessageKeepAliveSeconds(final int txMessageKeepAliveSeconds) {
      this.txMessageKeepAliveSeconds = txMessageKeepAliveSeconds;
      return this;
    }

    public Builder priceBump(final Percentage priceBump) {
      this.priceBump = priceBump;
      return this;
    }

    public Builder priceBump(final int priceBump) {
      return priceBump(Percentage.fromInt(priceBump));
    }

    public TransactionPoolConfiguration build() {
      return new TransactionPoolConfiguration(
          txPoolMaxSize,
          pooledTransactionHashesSize,
          pendingTxRetentionPeriod,
          txMessageKeepAliveSeconds,
          priceBump);
    }
  }
}
