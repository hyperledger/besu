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
package org.hyperledger.besu.cli.options;

import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.util.number.PositiveNumber;

import java.util.Arrays;
import java.util.List;

import picocli.CommandLine;

public class EthProtocolOptions implements CLIOptions<EthProtocolConfiguration> {
  private static final String MAX_GET_HEADERS_FLAG = "--Xewp-max-get-headers";
  private static final String MAX_GET_BODIES_FLAG = "--Xewp-max-get-bodies";
  private static final String MAX_GET_RECEIPTS_FLAG = "--Xewp-max-get-receipts";
  private static final String MAX_GET_NODE_DATA_FLAG = "--Xewp-max-get-node-data";

  @CommandLine.Option(
      hidden = true,
      names = {MAX_GET_HEADERS_FLAG},
      paramLabel = "<INTEGER>",
      description =
          "Maximum request limit for Ethereum Wire Protocol GET_BLOCK_HEADERS. (default: ${DEFAULT-VALUE})")
  private PositiveNumber maxGetBlockHeaders =
      PositiveNumber.fromInt(EthProtocolConfiguration.DEFAULT_MAX_GET_BLOCK_HEADERS);

  @CommandLine.Option(
      hidden = true,
      names = {MAX_GET_BODIES_FLAG},
      paramLabel = "<INTEGER>",
      description =
          "Maximum request limit for Ethereum Wire Protocol GET_BLOCK_BODIES. (default: ${DEFAULT-VALUE})")
  private PositiveNumber maxGetBlockBodies =
      PositiveNumber.fromInt(EthProtocolConfiguration.DEFAULT_MAX_GET_BLOCK_BODIES);

  @CommandLine.Option(
      hidden = true,
      names = {MAX_GET_RECEIPTS_FLAG},
      paramLabel = "<INTEGER>",
      description =
          "Maximum request limit for Ethereum Wire Protocol GET_RECEIPTS. (default: ${DEFAULT-VALUE})")
  private PositiveNumber maxGetReceipts =
      PositiveNumber.fromInt(EthProtocolConfiguration.DEFAULT_MAX_GET_RECEIPTS);

  @CommandLine.Option(
      hidden = true,
      names = {MAX_GET_NODE_DATA_FLAG},
      paramLabel = "<INTEGER>",
      description =
          "Maximum request limit for Ethereum Wire Protocol GET_NODE_DATA. (default: ${DEFAULT-VALUE})")
  private PositiveNumber maxGetNodeData =
      PositiveNumber.fromInt(EthProtocolConfiguration.DEFAULT_MAX_GET_NODE_DATA);

  private EthProtocolOptions() {}

  public static EthProtocolOptions create() {
    return new EthProtocolOptions();
  }

  public static EthProtocolOptions fromConfig(final EthProtocolConfiguration config) {
    final EthProtocolOptions options = create();
    options.maxGetBlockHeaders = PositiveNumber.fromInt(config.getMaxGetBlockHeaders());
    options.maxGetBlockBodies = PositiveNumber.fromInt(config.getMaxGetBlockBodies());
    options.maxGetReceipts = PositiveNumber.fromInt(config.getMaxGetReceipts());
    options.maxGetNodeData = PositiveNumber.fromInt(config.getMaxGetNodeData());
    return options;
  }

  @Override
  public EthProtocolConfiguration toDomainObject() {
    return EthProtocolConfiguration.builder()
        .maxGetBlockHeaders(maxGetBlockHeaders)
        .maxGetBlockBodies(maxGetBlockBodies)
        .maxGetReceipts(maxGetReceipts)
        .maxGetNodeData(maxGetNodeData)
        .build();
  }

  @Override
  public List<String> getCLIOptions() {
    return Arrays.asList(
        MAX_GET_HEADERS_FLAG,
        OptionParser.format(maxGetBlockHeaders.getValue()),
        MAX_GET_BODIES_FLAG,
        OptionParser.format(maxGetBlockBodies.getValue()),
        MAX_GET_RECEIPTS_FLAG,
        OptionParser.format(maxGetReceipts.getValue()),
        MAX_GET_NODE_DATA_FLAG,
        OptionParser.format(maxGetNodeData.getValue()));
  }
}
