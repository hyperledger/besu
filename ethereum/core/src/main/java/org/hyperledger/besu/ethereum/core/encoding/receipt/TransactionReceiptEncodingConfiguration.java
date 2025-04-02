/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.core.encoding.receipt;

public class TransactionReceiptEncodingConfiguration {
  public static final TransactionReceiptEncodingConfiguration NETWORK = new Builder().build();

  public static final TransactionReceiptEncodingConfiguration STORAGE_WITH_COMPACTION =
      new Builder().withRevertReason(true).withCompactedLogs(true).withBloomFilter(false).build();

  public static final TransactionReceiptEncodingConfiguration STORAGE_WITHOUT_COMPACTION =
      new Builder().withRevertReason(true).withCompactedLogs(false).withBloomFilter(true).build();

  public static final TransactionReceiptEncodingConfiguration TRIE_ROOT =
      new Builder().withOpaqueBytes(false).build();

  private final boolean withRevertReason;
  private final boolean withCompactedLogs;
  private final boolean withOpaqueBytes;
  private final boolean withBloomFilter;

  private TransactionReceiptEncodingConfiguration(
      final boolean withRevertReason,
      final boolean withCompactedLogs,
      final boolean withOpaqueBytes,
      final boolean withBloomFilter) {
    this.withRevertReason = withRevertReason;
    this.withCompactedLogs = withCompactedLogs;
    this.withOpaqueBytes = withOpaqueBytes;
    this.withBloomFilter = withBloomFilter;
  }

  public boolean isWithRevertReason() {
    return withRevertReason;
  }

  public boolean isWithCompactedLogs() {
    return withCompactedLogs;
  }

  public boolean isWithOpaqueBytes() {
    return withOpaqueBytes;
  }

  public boolean isWithBloomFilter() {
    return withBloomFilter;
  }

  @Override
  public String toString() {
    return "TransactionReceiptEncodingOptions{"
        + "withRevertReason="
        + withRevertReason
        + ", withCompactedLogs="
        + withCompactedLogs
        + ", withOpaqueBytes="
        + withOpaqueBytes
        + ", withBloomFilter="
        + withBloomFilter
        + '}';
  }

  public static class Builder {
    private boolean withRevertReason = false;
    private boolean withCompactedLogs = false;
    private boolean withOpaqueBytes = true;
    private boolean withBloomFilter = true;

    public Builder withRevertReason(final boolean withRevertReason) {
      this.withRevertReason = withRevertReason;
      return this;
    }

    public Builder withCompactedLogs(final boolean withCompactedLogs) {
      this.withCompactedLogs = withCompactedLogs;
      return this;
    }

    public Builder withOpaqueBytes(final boolean withOpaqueBytes) {
      this.withOpaqueBytes = withOpaqueBytes;
      return this;
    }

    public Builder withBloomFilter(final boolean withBloomFilter) {
      this.withBloomFilter = withBloomFilter;
      return this;
    }

    public TransactionReceiptEncodingConfiguration build() {
      return new TransactionReceiptEncodingConfiguration(
          withRevertReason, withCompactedLogs, withOpaqueBytes, withBloomFilter);
    }
  }
}
