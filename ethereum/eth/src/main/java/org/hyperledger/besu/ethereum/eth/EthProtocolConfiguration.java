/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.ethereum.eth;

import org.hyperledger.besu.util.number.PositiveNumber;

import java.util.Objects;

import com.google.common.base.MoreObjects;

public class EthProtocolConfiguration {

  public static final int DEFAULT_MAX_GET_BLOCK_HEADERS = 192;
  public static final int DEFAULT_MAX_GET_BLOCK_BODIES = 128;
  public static final int DEFAULT_MAX_GET_RECEIPTS = 256;
  public static final int DEFAULT_MAX_GET_NODE_DATA = 384;

  private final int maxGetBlockHeaders;
  private final int maxGetBlockBodies;
  private final int maxGetReceipts;
  private final int maxGetNodeData;

  public EthProtocolConfiguration(
      final int maxGetBlockHeaders,
      final int maxGetBlockBodies,
      final int maxGetReceipts,
      final int maxGetNodeData) {
    this.maxGetBlockHeaders = maxGetBlockHeaders;
    this.maxGetBlockBodies = maxGetBlockBodies;
    this.maxGetReceipts = maxGetReceipts;
    this.maxGetNodeData = maxGetNodeData;
  }

  public static EthProtocolConfiguration defaultConfig() {
    return new EthProtocolConfiguration(
        DEFAULT_MAX_GET_BLOCK_HEADERS,
        DEFAULT_MAX_GET_BLOCK_BODIES,
        DEFAULT_MAX_GET_RECEIPTS,
        DEFAULT_MAX_GET_NODE_DATA);
  }

  public static Builder builder() {
    return new Builder();
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
        && maxGetNodeData == that.maxGetNodeData;
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
        .toString();
  }

  public static class Builder {
    private PositiveNumber maxGetBlockHeaders =
        PositiveNumber.fromInt(EthProtocolConfiguration.DEFAULT_MAX_GET_BLOCK_HEADERS);

    private PositiveNumber maxGetBlockBodies =
        PositiveNumber.fromInt(EthProtocolConfiguration.DEFAULT_MAX_GET_BLOCK_BODIES);

    private PositiveNumber maxGetReceipts =
        PositiveNumber.fromInt(EthProtocolConfiguration.DEFAULT_MAX_GET_RECEIPTS);

    private PositiveNumber maxGetNodeData =
        PositiveNumber.fromInt(EthProtocolConfiguration.DEFAULT_MAX_GET_NODE_DATA);

    public Builder maxGetBlockHeaders(final PositiveNumber maxGetBlockHeaders) {
      this.maxGetBlockHeaders = maxGetBlockHeaders;
      return this;
    }

    public Builder maxGetBlockBodies(final PositiveNumber maxGetBlockBodies) {
      this.maxGetBlockBodies = maxGetBlockBodies;
      return this;
    }

    public Builder maxGetReceipts(final PositiveNumber maxGetReceipts) {
      this.maxGetReceipts = maxGetReceipts;
      return this;
    }

    public Builder maxGetNodeData(final PositiveNumber maxGetNodeData) {
      this.maxGetNodeData = maxGetNodeData;
      return this;
    }

    public EthProtocolConfiguration build() {
      return new EthProtocolConfiguration(
          maxGetBlockHeaders.getValue(),
          maxGetBlockBodies.getValue(),
          maxGetReceipts.getValue(),
          maxGetNodeData.getValue());
    }
  }
}
