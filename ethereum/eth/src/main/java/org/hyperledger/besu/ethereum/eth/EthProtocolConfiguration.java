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
package org.hyperledger.besu.ethereum.eth;

import org.hyperledger.besu.util.number.ByteUnits;
import org.hyperledger.besu.util.number.PositiveNumber;

import java.util.Objects;

import com.google.common.base.MoreObjects;

public class EthProtocolConfiguration {

  public static final int DEFAULT_MAX_MESSAGE_SIZE = 10 * ByteUnits.MEGABYTE;
  public static final int DEFAULT_MAX_GET_BLOCK_HEADERS = 192;
  public static final int DEFAULT_MAX_GET_BLOCK_BODIES = 128;
  public static final int DEFAULT_MAX_GET_RECEIPTS = 256;
  public static final int DEFAULT_MAX_GET_NODE_DATA = 384;
  public static final int DEFAULT_MAX_GET_POOLED_TRANSACTIONS = 256;
  public static final boolean DEFAULT_LEGACY_ETH_64_FORK_ID_ENABLED = false;

  // Limit the size of p2p messages (in bytes)
  private final int maxMessageSize;

  // These options impose limits on the max number of elements returned when responding to
  // peers' p2p RPC requests
  private final int maxGetBlockHeaders;
  private final int maxGetBlockBodies;
  private final int maxGetReceipts;
  private final int maxGetNodeData;
  private final int maxGetPooledTransactions;
  private final boolean legacyEth64ForkIdEnabled;

  private EthProtocolConfiguration(
      final int maxMessageSize,
      final int maxGetBlockHeaders,
      final int maxGetBlockBodies,
      final int maxGetReceipts,
      final int maxGetNodeData,
      final int maxGetPooledTransactions,
      final boolean legacyEth64ForkIdEnabled) {
    this.maxMessageSize = maxMessageSize;
    this.maxGetBlockHeaders = maxGetBlockHeaders;
    this.maxGetBlockBodies = maxGetBlockBodies;
    this.maxGetReceipts = maxGetReceipts;
    this.maxGetNodeData = maxGetNodeData;
    this.maxGetPooledTransactions = maxGetPooledTransactions;
    this.legacyEth64ForkIdEnabled = legacyEth64ForkIdEnabled;
  }

  public static EthProtocolConfiguration defaultConfig() {
    return builder().build();
  }

  public static Builder builder() {
    return new Builder();
  }

  public int getMaxMessageSize() {
    return maxMessageSize;
  }

  public int getMaxGetBlockHeaders() {
    return maxGetBlockHeaders;
  }

  public int getMaxGetBlockBodies() {
    return maxGetBlockBodies;
  }

  public int getMaxGetReceipts() {
    return maxGetReceipts;
  }

  public int getMaxGetNodeData() {
    return maxGetNodeData;
  }

  public int getMaxGetPooledTransactions() {
    return maxGetPooledTransactions;
  }

  public boolean isLegacyEth64ForkIdEnabled() {
    return legacyEth64ForkIdEnabled;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final EthProtocolConfiguration that = (EthProtocolConfiguration) o;
    return maxGetBlockHeaders == that.maxGetBlockHeaders
        && maxGetBlockBodies == that.maxGetBlockBodies
        && maxGetReceipts == that.maxGetReceipts
        && maxGetNodeData == that.maxGetNodeData
        && maxGetPooledTransactions == that.maxGetPooledTransactions;
  }

  @Override
  public int hashCode() {
    return Objects.hash(maxGetBlockHeaders, maxGetBlockBodies, maxGetReceipts, maxGetNodeData);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("maxGetBlockHeaders", maxGetBlockHeaders)
        .add("maxGetBlockBodies", maxGetBlockBodies)
        .add("maxGetReceipts", maxGetReceipts)
        .add("maxGetNodeData", maxGetNodeData)
        .add("maxGetPooledTransactions", maxGetPooledTransactions)
        .toString();
  }

  public static class Builder {
    private PositiveNumber maxMessageSize =
        PositiveNumber.fromInt(EthProtocolConfiguration.DEFAULT_MAX_MESSAGE_SIZE);

    private PositiveNumber maxGetBlockHeaders =
        PositiveNumber.fromInt(EthProtocolConfiguration.DEFAULT_MAX_GET_BLOCK_HEADERS);

    private PositiveNumber maxGetBlockBodies =
        PositiveNumber.fromInt(EthProtocolConfiguration.DEFAULT_MAX_GET_BLOCK_BODIES);

    private PositiveNumber maxGetReceipts =
        PositiveNumber.fromInt(EthProtocolConfiguration.DEFAULT_MAX_GET_RECEIPTS);

    private PositiveNumber maxGetNodeData =
        PositiveNumber.fromInt(EthProtocolConfiguration.DEFAULT_MAX_GET_NODE_DATA);

    private PositiveNumber maxGetPooledTransactions =
        PositiveNumber.fromInt(EthProtocolConfiguration.DEFAULT_MAX_GET_POOLED_TRANSACTIONS);

    private boolean legacyEth64ForkIdEnabled =
        EthProtocolConfiguration.DEFAULT_LEGACY_ETH_64_FORK_ID_ENABLED;

    public Builder maxMessageSize(final PositiveNumber maxMessageSize) {
      this.maxMessageSize = maxMessageSize;
      return this;
    }

    public Builder maxMessageSize(final int maxMessageSize) {
      this.maxMessageSize = PositiveNumber.fromInt(maxMessageSize);
      return this;
    }

    public Builder maxGetBlockHeaders(final PositiveNumber maxGetBlockHeaders) {
      this.maxGetBlockHeaders = maxGetBlockHeaders;
      return this;
    }

    public Builder maxGetBlockHeaders(final int maxGetBlockHeaders) {
      this.maxGetBlockHeaders = PositiveNumber.fromInt(maxGetBlockHeaders);
      return this;
    }

    public Builder maxGetBlockBodies(final PositiveNumber maxGetBlockBodies) {
      this.maxGetBlockBodies = maxGetBlockBodies;
      return this;
    }

    public Builder maxGetBlockBodies(final int maxGetBlockBodies) {
      this.maxGetBlockBodies = PositiveNumber.fromInt(maxGetBlockBodies);
      return this;
    }

    public Builder maxGetReceipts(final PositiveNumber maxGetReceipts) {
      this.maxGetReceipts = maxGetReceipts;
      return this;
    }

    public Builder maxGetReceipts(final int maxGetReceipts) {
      this.maxGetReceipts = PositiveNumber.fromInt(maxGetReceipts);
      return this;
    }

    public Builder maxGetNodeData(final PositiveNumber maxGetNodeData) {
      this.maxGetNodeData = maxGetNodeData;
      return this;
    }

    public Builder maxGetNodeData(final int maxGetNodeData) {
      this.maxGetNodeData = PositiveNumber.fromInt(maxGetNodeData);
      return this;
    }

    public Builder maxGetPooledTransactions(final PositiveNumber maxGetPooledTransactions) {
      this.maxGetPooledTransactions = maxGetPooledTransactions;
      return this;
    }

    public Builder maxGetPooledTransactions(final int maxGetPooledTransactions) {
      this.maxGetPooledTransactions = PositiveNumber.fromInt(maxGetPooledTransactions);
      return this;
    }

    public Builder legacyEth64ForkIdEnabled(final boolean legacyEth64ForkIdEnabled) {
      this.legacyEth64ForkIdEnabled = legacyEth64ForkIdEnabled;
      return this;
    }

    public EthProtocolConfiguration build() {
      return new EthProtocolConfiguration(
          maxMessageSize.getValue(),
          maxGetBlockHeaders.getValue(),
          maxGetBlockBodies.getValue(),
          maxGetReceipts.getValue(),
          maxGetNodeData.getValue(),
          maxGetPooledTransactions.getValue(),
          legacyEth64ForkIdEnabled);
    }
  }
}
