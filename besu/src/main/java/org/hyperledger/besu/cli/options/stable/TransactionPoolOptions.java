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

import static org.hyperledger.besu.cli.DefaultCommandValues.MANDATORY_DOUBLE_FORMAT_HELP;
import static org.hyperledger.besu.cli.DefaultCommandValues.MANDATORY_INTEGER_FORMAT_HELP;
import static org.hyperledger.besu.cli.DefaultCommandValues.MANDATORY_LONG_FORMAT_HELP;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration.Implementation.LAYERED;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration.Implementation.LEGACY;

import org.hyperledger.besu.cli.converter.FractionConverter;
import org.hyperledger.besu.cli.converter.PercentageConverter;
import org.hyperledger.besu.cli.options.CLIOptions;
import org.hyperledger.besu.cli.util.CommandLineUtils;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.eth.transactions.ImmutableTransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.util.number.Fraction;
import org.hyperledger.besu.util.number.Percentage;

import java.io.File;
import java.util.List;

import picocli.CommandLine;

/** The Transaction pool Cli stable options. */
public class TransactionPoolOptions implements CLIOptions<TransactionPoolConfiguration> {
  private static final String TX_POOL_IMPLEMENTATION = "--tx-pool";
  private static final String TX_POOL_DISABLE_LOCALS = "--tx-pool-disable-locals";
  private static final String TX_POOL_ENABLE_SAVE_RESTORE = "--tx-pool-enable-save-restore";
  private static final String TX_POOL_SAVE_FILE = "--tx-pool-save-file";
  private static final String TX_POOL_PRICE_BUMP = "--tx-pool-price-bump";
  private static final String RPC_TX_FEECAP = "--rpc-tx-feecap";
  private static final String STRICT_TX_REPLAY_PROTECTION_ENABLED_FLAG =
      "--strict-tx-replay-protection-enabled";

  @CommandLine.Option(
      names = {TX_POOL_IMPLEMENTATION},
      paramLabel = "<Enum>",
      description = "The Transaction Pool implementation to use(default: ${DEFAULT-VALUE})",
      arity = "0..1")
  private TransactionPoolConfiguration.Implementation txPoolImplementation = LAYERED;

  @CommandLine.Option(
      names = {TX_POOL_DISABLE_LOCALS},
      paramLabel = "<Boolean>",
      description =
          "Set to true if transactions sent via RPC should have the same checks and not be prioritized over remote ones (default: ${DEFAULT-VALUE})",
      fallbackValue = "true",
      arity = "0..1")
  private Boolean disableLocalTxs = TransactionPoolConfiguration.DEFAULT_DISABLE_LOCAL_TXS;

  @CommandLine.Option(
      names = {TX_POOL_ENABLE_SAVE_RESTORE},
      paramLabel = "<Boolean>",
      description =
          "Set to true to enable saving the txpool content to file on shutdown and reloading it on startup (default: ${DEFAULT-VALUE})",
      fallbackValue = "true",
      arity = "0..1")
  private Boolean saveRestoreEnabled = TransactionPoolConfiguration.DEFAULT_ENABLE_SAVE_RESTORE;

  @CommandLine.Option(
      names = {TX_POOL_SAVE_FILE},
      paramLabel = "<STRING>",
      description =
          "If saving the txpool content is enabled, define a custom path for the save file (default: ${DEFAULT-VALUE} in the data-dir)",
      arity = "1")
  private File saveFile = TransactionPoolConfiguration.DEFAULT_SAVE_FILE;

  @CommandLine.Option(
      names = {TX_POOL_PRICE_BUMP},
      paramLabel = "<Percentage>",
      converter = PercentageConverter.class,
      description =
          "Price bump percentage to replace an already existing transaction  (default: ${DEFAULT-VALUE})",
      arity = "1")
  private Percentage priceBump = TransactionPoolConfiguration.DEFAULT_PRICE_BUMP;

  @CommandLine.Option(
      names = {RPC_TX_FEECAP},
      description =
          "Maximum transaction fees (in Wei) accepted for transaction submitted through RPC (default: ${DEFAULT-VALUE})",
      arity = "1")
  private Wei txFeeCap = TransactionPoolConfiguration.DEFAULT_RPC_TX_FEE_CAP;

  @CommandLine.Option(
      names = {STRICT_TX_REPLAY_PROTECTION_ENABLED_FLAG},
      paramLabel = "<Boolean>",
      description =
          "Require transactions submitted via JSON-RPC to use replay protection in accordance with EIP-155 (default: ${DEFAULT-VALUE})",
      fallbackValue = "true",
      arity = "0..1")
  private Boolean strictTxReplayProtectionEnabled = false;

  @CommandLine.ArgGroup(
      validate = false,
      heading = "@|bold Tx Pool Layered Implementation Options|@%n")
  private final Layered layeredOptions = new Layered();

  static class Layered {
    private static final String TX_POOL_LAYER_MAX_CAPACITY = "--tx-pool-layer-max-capacity";
    private static final String TX_POOL_MAX_PRIORITIZED = "--tx-pool-max-prioritized";
    private static final String TX_POOL_MAX_FUTURE_BY_SENDER = "--tx-pool-max-future-by-sender";

    @CommandLine.Option(
        names = {TX_POOL_LAYER_MAX_CAPACITY},
        paramLabel = MANDATORY_LONG_FORMAT_HELP,
        description =
            "Max amount of memory space, in bytes, that any layer within the transaction pool could occupy (default: ${DEFAULT-VALUE})",
        arity = "1")
    Long txPoolLayerMaxCapacity =
        TransactionPoolConfiguration.DEFAULT_PENDING_TRANSACTIONS_LAYER_MAX_CAPACITY_BYTES;

    @CommandLine.Option(
        names = {TX_POOL_MAX_PRIORITIZED},
        paramLabel = MANDATORY_INTEGER_FORMAT_HELP,
        description =
            "Max number of pending transactions that are prioritized and thus kept sorted (default: ${DEFAULT-VALUE})",
        arity = "1")
    Integer txPoolMaxPrioritized =
        TransactionPoolConfiguration.DEFAULT_MAX_PRIORITIZED_TRANSACTIONS;

    @CommandLine.Option(
        names = {TX_POOL_MAX_FUTURE_BY_SENDER},
        paramLabel = MANDATORY_INTEGER_FORMAT_HELP,
        description =
            "Max number of future pending transactions allowed for a single sender (default: ${DEFAULT-VALUE})",
        arity = "1")
    Integer txPoolMaxFutureBySender = TransactionPoolConfiguration.DEFAULT_MAX_FUTURE_BY_SENDER;
  }

  @CommandLine.ArgGroup(
      validate = false,
      heading = "@|bold Tx Pool Legacy Implementation Options|@%n")
  private final Legacy legacyOptions = new Legacy();

  static class Legacy {
    private static final String TX_POOL_RETENTION_HOURS = "--tx-pool-retention-hours";
    private static final String TX_POOL_LIMIT_BY_ACCOUNT_PERCENTAGE =
        "--tx-pool-limit-by-account-percentage";
    private static final String TX_POOL_MAX_SIZE = "--tx-pool-max-size";

    @CommandLine.Option(
        names = {TX_POOL_RETENTION_HOURS},
        paramLabel = MANDATORY_INTEGER_FORMAT_HELP,
        description =
            "Maximum retention period of pending transactions in hours (default: ${DEFAULT-VALUE})",
        arity = "1")
    Integer pendingTxRetentionPeriod = TransactionPoolConfiguration.DEFAULT_TX_RETENTION_HOURS;

    @CommandLine.Option(
        names = {TX_POOL_LIMIT_BY_ACCOUNT_PERCENTAGE},
        paramLabel = MANDATORY_DOUBLE_FORMAT_HELP,
        converter = FractionConverter.class,
        description =
            "Maximum portion of the transaction pool which a single account may occupy with future transactions (default: ${DEFAULT-VALUE})",
        arity = "1")
    Fraction txPoolLimitByAccountPercentage =
        TransactionPoolConfiguration.DEFAULT_LIMIT_TX_POOL_BY_ACCOUNT_PERCENTAGE;

    @CommandLine.Option(
        names = {TX_POOL_MAX_SIZE},
        paramLabel = MANDATORY_INTEGER_FORMAT_HELP,
        description =
            "Maximum number of pending transactions that will be kept in the transaction pool (default: ${DEFAULT-VALUE})",
        arity = "1")
    Integer txPoolMaxSize = TransactionPoolConfiguration.DEFAULT_MAX_PENDING_TRANSACTIONS;
  }

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
    options.txPoolImplementation = config.getTxPoolImplementation();
    options.saveRestoreEnabled = config.getEnableSaveRestore();
    options.disableLocalTxs = config.getDisableLocalTransactions();
    options.priceBump = config.getPriceBump();
    options.txFeeCap = config.getTxFeeCap();
    options.saveFile = config.getSaveFile();
    options.strictTxReplayProtectionEnabled = config.getStrictTransactionReplayProtectionEnabled();
    options.layeredOptions.txPoolLayerMaxCapacity =
        config.getPendingTransactionsLayerMaxCapacityBytes();
    options.layeredOptions.txPoolMaxPrioritized = config.getMaxPrioritizedTransactions();
    options.layeredOptions.txPoolMaxFutureBySender = config.getMaxFutureBySender();
    options.legacyOptions.txPoolLimitByAccountPercentage =
        config.getTxPoolLimitByAccountPercentage();
    options.legacyOptions.txPoolMaxSize = config.getTxPoolMaxSize();
    options.legacyOptions.pendingTxRetentionPeriod = config.getPendingTxRetentionPeriod();

    return options;
  }

  /**
   * Validate that there are no inconsistencies in the specified options. For example that the
   * options are valid for the selected implementation.
   *
   * @param commandLine the full commandLine to check all the options specified by the user
   */
  public void validate(final CommandLine commandLine) {
    CommandLineUtils.failIfOptionDoesntMeetRequirement(
        commandLine,
        "Could not use legacy transaction pool options with layered implementation",
        !txPoolImplementation.equals(LAYERED),
        CommandLineUtils.getCLIOptionNames(Legacy.class));

    CommandLineUtils.failIfOptionDoesntMeetRequirement(
        commandLine,
        "Could not use layered transaction pool options with legacy implementation",
        !txPoolImplementation.equals(LEGACY),
        CommandLineUtils.getCLIOptionNames(Layered.class));
  }

  @Override
  public TransactionPoolConfiguration toDomainObject() {
    return ImmutableTransactionPoolConfiguration.builder()
        .txPoolImplementation(txPoolImplementation)
        .enableSaveRestore(saveRestoreEnabled)
        .disableLocalTransactions(disableLocalTxs)
        .priceBump(priceBump)
        .txFeeCap(txFeeCap)
        .saveFile(saveFile)
        .strictTransactionReplayProtectionEnabled(strictTxReplayProtectionEnabled)
        .pendingTransactionsLayerMaxCapacityBytes(layeredOptions.txPoolLayerMaxCapacity)
        .maxPrioritizedTransactions(layeredOptions.txPoolMaxPrioritized)
        .maxFutureBySender(layeredOptions.txPoolMaxFutureBySender)
        .txPoolLimitByAccountPercentage(legacyOptions.txPoolLimitByAccountPercentage)
        .txPoolMaxSize(legacyOptions.txPoolMaxSize)
        .pendingTxRetentionPeriod(legacyOptions.pendingTxRetentionPeriod)
        .build();
  }

  @Override
  public List<String> getCLIOptions() {
    return CommandLineUtils.getCLIOptions(this, new TransactionPoolOptions());
  }
}
