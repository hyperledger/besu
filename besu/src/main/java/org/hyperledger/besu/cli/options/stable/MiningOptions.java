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
package org.hyperledger.besu.cli.options.stable;

import static java.util.Arrays.asList;
import static org.hyperledger.besu.ethereum.core.MiningParameters.Dynamic.DEFAULT_EXTRA_DATA;
import static org.hyperledger.besu.ethereum.core.MiningParameters.Dynamic.DEFAULT_MIN_BLOCK_OCCUPANCY_RATIO;
import static org.hyperledger.besu.ethereum.core.MiningParameters.Dynamic.DEFAULT_MIN_TRANSACTION_GAS_PRICE;

import org.hyperledger.besu.cli.options.CLIOptions;
import org.hyperledger.besu.cli.util.CommandLineUtils;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.ImmutableMiningParameters;
import org.hyperledger.besu.ethereum.core.MiningParameters;

import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import picocli.CommandLine;
import picocli.CommandLine.Option;

/** The Mining CLI options. */
public class MiningOptions implements CLIOptions<MiningParameters> {

  @Option(
      names = {"--miner-enabled"},
      description = "Set if node will perform mining (default: ${DEFAULT-VALUE})")
  private final Boolean isMiningEnabled = false;

  @Option(
      names = {"--miner-stratum-enabled"},
      description = "Set if node will perform Stratum mining (default: ${DEFAULT-VALUE})")
  private final Boolean iStratumMiningEnabled = false;

  @SuppressWarnings({"FieldCanBeFinal", "FieldMayBeFinal"}) // PicoCLI requires non-final Strings.
  @Option(
      names = {"--miner-stratum-host"},
      description = "Host for Stratum network mining service (default: ${DEFAULT-VALUE})")
  private String stratumNetworkInterface = "0.0.0.0";

  @Option(
      names = {"--miner-stratum-port"},
      description = "Stratum port binding (default: ${DEFAULT-VALUE})")
  private final Integer stratumPort = 8008;

  @Option(
      names = {"--miner-coinbase"},
      description =
          "Account to which mining rewards are paid. You must specify a valid coinbase if "
              + "mining is enabled using --miner-enabled option",
      arity = "1")
  private final Address coinbase = null;

  @Option(
      names = {"--miner-extra-data"},
      description =
          "A hex string representing the (32) bytes to be included in the extra data "
              + "field of a mined block (default: ${DEFAULT-VALUE})",
      arity = "1")
  private final Bytes extraData = DEFAULT_EXTRA_DATA;

  @Option(
      names = {"--min-block-occupancy-ratio"},
      description = "Minimum occupancy ratio for a mined block (default: ${DEFAULT-VALUE})",
      arity = "1")
  private final Double minBlockOccupancyRatio = DEFAULT_MIN_BLOCK_OCCUPANCY_RATIO;

  @Option(
      names = {"--min-gas-price"},
      description =
          "Minimum price (in Wei) offered by a transaction for it to be included in a mined "
              + "block (default: ${DEFAULT-VALUE})",
      arity = "1")
  private final Wei minTransactionGasPrice = DEFAULT_MIN_TRANSACTION_GAS_PRICE;

  @Option(
      names = {"--target-gas-limit"},
      description =
          "Sets target gas limit per block. If set, each block's gas limit will approach this setting over time if the current gas limit is different.")
  private final Long targetGasLimit = null;

  private MiningOptions() {}

  /**
   * Create mining options.
   *
   * @return the mining options
   */
  public static MiningOptions create() {
    return new MiningOptions();
  }

  /**
   * Validate that there are no inconsistencies in the specified options. For example that the
   * options are valid for the selected implementation.
   *
   * @param commandLine the full commandLine to check all the options specified by the user
   * @param logger the logger
   * @param isMergeEnabled is the Merge enabled?
   * @param isEthHash is EthHash?
   */
  public void validate(
      final CommandLine commandLine,
      final Logger logger,
      final boolean isMergeEnabled,
      final boolean isEthHash) {
    if (Boolean.TRUE.equals(isMiningEnabled) && coinbase == null) {
      throw new CommandLine.ParameterException(
          commandLine,
          "Unable to mine without a valid coinbase. Either disable mining (remove --miner-enabled) "
              + "or specify the beneficiary of mining (via --miner-coinbase <Address>)");
    }
    if (Boolean.FALSE.equals(isMiningEnabled) && Boolean.TRUE.equals(iStratumMiningEnabled)) {
      throw new CommandLine.ParameterException(
          commandLine,
          "Unable to mine with Stratum if mining is disabled. Either disable Stratum mining (remove --miner-stratum-enabled) "
              + "or specify mining is enabled (--miner-enabled)");
    }

    // Check that block producer options work
    if (!isMergeEnabled && isEthHash) {
      CommandLineUtils.checkOptionDependencies(
          logger,
          commandLine,
          "--miner-enabled",
          !isMiningEnabled,
          asList(
              "--miner-coinbase",
              "--min-gas-price",
              "--min-block-occupancy-ratio",
              "--miner-extra-data"));

      // Check that mining options are able to work
      CommandLineUtils.checkOptionDependencies(
          logger,
          commandLine,
          "--miner-enabled",
          !isMiningEnabled,
          asList(
              "--miner-stratum-enabled",
              "--Xminer-remote-sealers-limit",
              "--Xminer-remote-sealers-hashrate-ttl"));
    }
  }

  @Override
  public MiningParameters toDomainObject() {
    final var miningParametersBuilder =
        ImmutableMiningParameters.builder()
            .isMiningEnabled(isMiningEnabled)
            .isStratumMiningEnabled(iStratumMiningEnabled)
            .stratumNetworkInterface(stratumNetworkInterface)
            .stratumPort(stratumPort);

    if (coinbase != null) {
      miningParametersBuilder.coinbase(coinbase);
    }

    final var dynamicParameters =
        miningParametersBuilder
            .build()
            .getDynamic()
            .setExtraData(extraData)
            .setMinBlockOccupancyRatio(minBlockOccupancyRatio)
            .setMinTransactionGasPrice(minTransactionGasPrice);

    if (targetGasLimit != null) {
      dynamicParameters.setTargetGasLimit(targetGasLimit);
    }
    return dynamicParameters.toParameters();
  }

  @Override
  public List<String> getCLIOptions() {
    return null;
  }
}
