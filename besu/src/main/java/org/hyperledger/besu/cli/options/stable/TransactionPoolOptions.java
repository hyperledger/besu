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
package org.hyperledger.besu.cli.options.stable;

import static org.hyperledger.besu.cli.DefaultCommandValues.MANDATORY_DOUBLE_FORMAT_HELP;
import static org.hyperledger.besu.cli.DefaultCommandValues.MANDATORY_INTEGER_FORMAT_HELP;
import static org.hyperledger.besu.cli.DefaultCommandValues.MANDATORY_LONG_FORMAT_HELP;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration.Implementation.LAYERED;

import org.hyperledger.besu.cli.converter.FractionConverter;
import org.hyperledger.besu.cli.converter.PercentageConverter;
import org.hyperledger.besu.cli.options.CLIOptions;
import org.hyperledger.besu.cli.options.OptionParser;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.eth.transactions.ImmutableTransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.util.number.Percentage;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

public class TransactionPoolOptions implements CLIOptions<TransactionPoolConfiguration> {
  private static final Logger LOG = LoggerFactory.getLogger(TransactionPoolOptions.class);
  private static final String TX_POOL_IMPLEMENTATION = "--tx-pool";
  private static final String STRICT_TX_REPLAY_PROTECTION_ENABLED_FLAG =
      "--strict-tx-replay-protection-enabled";
  private static final String LAYERED_TX_POOL_LAYER_MAX_CAPACITY =
      "--layered-tx-pool-layer-max-capacity";
  private static final String LAYERED_TX_POOL_MAX_PRIORITIZED = "--layered-tx-pool-max-prioritized";
  private static final String LAYERED_TX_POOL_MAX_FUTURE_BY_SENDER =
      "--layered-tx-pool-max-future-by-sender";

  @CommandLine.Option(
      names = {"--tx-pool-disable-locals"},
      paramLabel = "<Boolean>",
      description =
          "Set to true if transactions sent via RPC should have the same checks and not be prioritized over remote ones (default: ${DEFAULT-VALUE})",
      fallbackValue = "true",
      arity = "0..1")
  private Boolean disableLocalTxs = TransactionPoolConfiguration.DEFAULT_DISABLE_LOCAL_TXS;

  @CommandLine.Option(
      names = {"--tx-pool-enable-save-restore"},
      paramLabel = "<Boolean>",
      description =
          "Set to true to enable saving the txpool content to file on shutdown and reloading it on startup (default: ${DEFAULT-VALUE})",
      fallbackValue = "true",
      arity = "0..1")
  private Boolean saveRestoreEnabled = TransactionPoolConfiguration.DEFAULT_ENABLE_SAVE_RESTORE;

  @CommandLine.Option(
      names = {"--tx-pool-save-file"},
      paramLabel = "<STRING>",
      description =
          "If saving the txpool content is enabled, define a custom path for the save file (default: ${DEFAULT-VALUE} in the data-dir)",
      arity = "1")
  private File saveFile = TransactionPoolConfiguration.DEFAULT_SAVE_FILE;

  @CommandLine.Option(
      names = {"--tx-pool-limit-by-account-percentage"},
      paramLabel = MANDATORY_DOUBLE_FORMAT_HELP,
      converter = FractionConverter.class,
      description =
          "Maximum portion of the transaction pool which a single account may occupy with future transactions (default: ${DEFAULT-VALUE})",
      arity = "1")
  private Float txPoolLimitByAccountPercentage =
      TransactionPoolConfiguration.DEFAULT_LIMIT_TX_POOL_BY_ACCOUNT_PERCENTAGE;

  @CommandLine.Option(
      names = {"--tx-pool-max-size"},
      paramLabel = MANDATORY_INTEGER_FORMAT_HELP,
      description =
          "Maximum number of pending transactions that will be kept in the transaction pool (default: ${DEFAULT-VALUE})",
      arity = "1")
  private final Integer txPoolMaxSize =
      TransactionPoolConfiguration.DEFAULT_MAX_PENDING_TRANSACTIONS;

  @CommandLine.Option(
      names = {"--tx-pool-retention-hours"},
      paramLabel = MANDATORY_INTEGER_FORMAT_HELP,
      description =
          "Maximum retention period of pending transactions in hours (default: ${DEFAULT-VALUE})",
      arity = "1")
  private final Integer pendingTxRetentionPeriod =
      TransactionPoolConfiguration.DEFAULT_TX_RETENTION_HOURS;

  @CommandLine.Option(
      names = {"--tx-pool-price-bump"},
      paramLabel = MANDATORY_INTEGER_FORMAT_HELP,
      converter = PercentageConverter.class,
      description =
          "Price bump percentage to replace an already existing transaction  (default: ${DEFAULT-VALUE})",
      arity = "1")
  private final Integer priceBump = TransactionPoolConfiguration.DEFAULT_PRICE_BUMP.getValue();

  @CommandLine.Option(
      names = {STRICT_TX_REPLAY_PROTECTION_ENABLED_FLAG},
      paramLabel = "<Boolean>",
      description =
          "Require transactions submitted via JSON-RPC to use replay protection in accordance with EIP-155 (default: ${DEFAULT-VALUE})",
      fallbackValue = "true",
      arity = "0..1")
  private Boolean strictTxReplayProtectionEnabled = false;

  @CommandLine.Option(
      names = {TX_POOL_IMPLEMENTATION},
      paramLabel = "<Enum>",
      description = "The Transaction Pool implementation to use(default: ${DEFAULT-VALUE})",
      arity = "0..1")
  private TransactionPoolConfiguration.Implementation txPoolImplementation = LAYERED;

  @CommandLine.Option(
      names = {LAYERED_TX_POOL_LAYER_MAX_CAPACITY},
      paramLabel = MANDATORY_LONG_FORMAT_HELP,
      hidden = true,
      description =
          "Max amount of memory space, in bytes, that any layer within the transaction pool could occupy (default: ${DEFAULT-VALUE})",
      arity = "1")
  private long layeredTxPoolLayerMaxCapacity =
      TransactionPoolConfiguration.DEFAULT_PENDING_TRANSACTIONS_LAYER_MAX_CAPACITY_BYTES;

  @CommandLine.Option(
      names = {LAYERED_TX_POOL_MAX_PRIORITIZED},
      paramLabel = MANDATORY_INTEGER_FORMAT_HELP,
      hidden = true,
      description =
          "Max number of pending transactions that are prioritized and thus kept sorted (default: ${DEFAULT-VALUE})",
      arity = "1")
  private int layeredTxPoolMaxPrioritized =
      TransactionPoolConfiguration.DEFAULT_MAX_PRIORITIZED_TRANSACTIONS;

  @CommandLine.Option(
      names = {LAYERED_TX_POOL_MAX_FUTURE_BY_SENDER},
      paramLabel = MANDATORY_INTEGER_FORMAT_HELP,
      hidden = true,
      description =
          "Max number of future pending transactions allowed for a single sender (default: ${DEFAULT-VALUE})",
      arity = "1")
  private int layeredTxPoolMaxFutureBySender =
      TransactionPoolConfiguration.DEFAULT_MAX_FUTURE_BY_SENDER;

  @CommandLine.Option(
      names = {"--rpc-tx-feecap"},
      description =
          "Maximum transaction fees (in Wei) accepted for transaction submitted through RPC (default: ${DEFAULT-VALUE})",
      arity = "1")
  private final Wei txFeeCap = TransactionPoolConfiguration.DEFAULT_RPC_TX_FEE_CAP;

  private TransactionPoolOptions() {}

  /**
   * Create transaction pool options.
   *
   * @return the transaction pool options
   */
  public static TransactionPoolOptions create() {
    return new TransactionPoolOptions();
  }

  /**
   * Create Transaction Pool Options from Transaction Pool Configuration.
   *
   * @param config the Transaction Pool Configuration
   * @return the transaction pool options
   */
  public static TransactionPoolOptions fromConfig(final TransactionPoolConfiguration config) {
    final TransactionPoolOptions options = TransactionPoolOptions.create();
    options.strictTxReplayProtectionEnabled = config.getStrictTransactionReplayProtectionEnabled();
    options.txPoolImplementation = config.getTxPoolImplementation();
    options.layeredTxPoolLayerMaxCapacity = config.getPendingTransactionsLayerMaxCapacityBytes();
    options.layeredTxPoolMaxPrioritized = config.getMaxPrioritizedTransactions();
    options.layeredTxPoolMaxFutureBySender = config.getMaxFutureBySender();
    return options;
  }

  @Override
  public TransactionPoolConfiguration toDomainObject() {
    if (txPoolImplementation.equals(LAYERED)) {
      LOG.warn(
          "Layered transaction pool enabled, ignoring settings for "
              + "--tx-pool-max-size and --tx-pool-limit-by-account-percentage");
    }

    return ImmutableTransactionPoolConfiguration.builder()
        .enableSaveRestore(saveRestoreEnabled)
        .disableLocalTransactions(disableLocalTxs)
        .txPoolLimitByAccountPercentage(txPoolLimitByAccountPercentage)
        .txPoolMaxSize(txPoolMaxSize)
        .pendingTxRetentionPeriod(pendingTxRetentionPeriod)
        .priceBump(Percentage.fromInt(priceBump))
        .txFeeCap(txFeeCap)
        .saveFile(saveFile)
        .strictTransactionReplayProtectionEnabled(strictTxReplayProtectionEnabled)
        .txPoolImplementation(txPoolImplementation)
        .pendingTransactionsLayerMaxCapacityBytes(layeredTxPoolLayerMaxCapacity)
        .maxPrioritizedTransactions(layeredTxPoolMaxPrioritized)
        .maxFutureBySender(layeredTxPoolMaxFutureBySender)
        .build();
  }

  @Override
  public List<String> getCLIOptions() {
    return Arrays.asList(
        STRICT_TX_REPLAY_PROTECTION_ENABLED_FLAG + "=" + strictTxReplayProtectionEnabled,
        TX_POOL_IMPLEMENTATION + "=" + txPoolImplementation,
        LAYERED_TX_POOL_LAYER_MAX_CAPACITY,
        OptionParser.format(layeredTxPoolLayerMaxCapacity),
        LAYERED_TX_POOL_MAX_PRIORITIZED,
        OptionParser.format(layeredTxPoolMaxPrioritized),
        LAYERED_TX_POOL_MAX_FUTURE_BY_SENDER,
        OptionParser.format(layeredTxPoolMaxFutureBySender));
  }
}
