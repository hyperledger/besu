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
package tech.pegasys.pantheon.ethereum.eth;

import tech.pegasys.pantheon.util.number.PositiveNumber;

import picocli.CommandLine;

public class EthereumWireProtocolConfiguration {

  public static final int DEFAULT_MAX_GET_BLOCK_HEADERS = 192;
  public static final int DEFAULT_MAX_GET_BLOCK_BODIES = 128;
  public static final int DEFAULT_MAX_GET_RECEIPTS = 256;
  public static final int DEFAULT_MAX_GET_NODE_DATA = 384;

  private final int maxGetBlockHeaders;
  private final int maxGetBlockBodies;
  private final int maxGetReceipts;
  private final int maxGetNodeData;

  public EthereumWireProtocolConfiguration(
      final int maxGetBlockHeaders,
      final int maxGetBlockBodies,
      final int maxGetReceipts,
      final int maxGetNodeData) {
    this.maxGetBlockHeaders = maxGetBlockHeaders;
    this.maxGetBlockBodies = maxGetBlockBodies;
    this.maxGetReceipts = maxGetReceipts;
    this.maxGetNodeData = maxGetNodeData;
  }

  public static EthereumWireProtocolConfiguration defaultConfig() {
    return new EthereumWireProtocolConfiguration(
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
  public boolean equals(final Object obj) {
    if (!(obj instanceof EthereumWireProtocolConfiguration)) {
      return false;
    }
    EthereumWireProtocolConfiguration other = ((EthereumWireProtocolConfiguration) obj);
    return maxGetBlockHeaders == other.maxGetBlockHeaders
        && maxGetBlockBodies == other.maxGetBlockBodies
        && maxGetReceipts == other.maxGetReceipts
        && maxGetNodeData == other.maxGetNodeData;
  }

  @Override
  public int hashCode() {
    return super.hashCode();
  }

  @Override
  public String toString() {
    return String.format(
        "maxGetBlockHeaders=%s\tmaxGetBlockBodies=%s\tmaxGetReceipts=%s\tmaxGetReceipts=%s",
        maxGetBlockHeaders, maxGetBlockBodies, maxGetReceipts, maxGetNodeData);
  }

  public static class Builder {
    @CommandLine.Option(
        hidden = true,
        names = {"--Xewp-max-get-headers"},
        paramLabel = "<INTEGER>",
        description =
            "Maximum request limit for Ethereum Wire Protocol GET_BLOCK_HEADERS. (default: ${DEFAULT-VALUE})")
    private PositiveNumber maxGetBlockHeaders =
        PositiveNumber.fromInt(EthereumWireProtocolConfiguration.DEFAULT_MAX_GET_BLOCK_HEADERS);

    @CommandLine.Option(
        hidden = true,
        names = {"--Xewp-max-get-bodies"},
        paramLabel = "<INTEGER>",
        description =
            "Maximum request limit for Ethereum Wire Protocol GET_BLOCK_BODIES. (default: ${DEFAULT-VALUE})")
    private PositiveNumber maxGetBlockBodies =
        PositiveNumber.fromInt(EthereumWireProtocolConfiguration.DEFAULT_MAX_GET_BLOCK_BODIES);

    @CommandLine.Option(
        hidden = true,
        names = {"--Xewp-max-get-receipts"},
        paramLabel = "<INTEGER>",
        description =
            "Maximum request limit for Ethereum Wire Protocol GET_RECEIPTS. (default: ${DEFAULT-VALUE})")
    private PositiveNumber maxGetReceipts =
        PositiveNumber.fromInt(EthereumWireProtocolConfiguration.DEFAULT_MAX_GET_RECEIPTS);

    @CommandLine.Option(
        hidden = true,
        names = {"--Xewp-max-get-node-data"},
        paramLabel = "<INTEGER>",
        description =
            "Maximum request limit for Ethereum Wire Protocol GET_NODE_DATA. (default: ${DEFAULT-VALUE})")
    private PositiveNumber maxGetNodeData =
        PositiveNumber.fromInt(EthereumWireProtocolConfiguration.DEFAULT_MAX_GET_NODE_DATA);

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

    public EthereumWireProtocolConfiguration build() {
      return new EthereumWireProtocolConfiguration(
          maxGetBlockHeaders.getValue(),
          maxGetBlockBodies.getValue(),
          maxGetReceipts.getValue(),
          maxGetNodeData.getValue());
    }
  }
}
