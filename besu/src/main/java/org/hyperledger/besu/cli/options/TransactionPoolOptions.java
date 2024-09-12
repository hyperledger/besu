/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.cli.options;

import static org.hyperledger.besu.cli.DefaultCommandValues.MANDATORY_DOUBLE_FORMAT_HELP;
import static org.hyperledger.besu.cli.DefaultCommandValues.MANDATORY_INTEGER_FORMAT_HELP;
import static org.hyperledger.besu.cli.DefaultCommandValues.MANDATORY_LONG_FORMAT_HELP;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration.Implementation.LAYERED;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration.Implementation.LEGACY;
import static org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration.Implementation.SEQUENCED;

import org.hyperledger.besu.cli.converter.DurationMillisConverter;
import org.hyperledger.besu.cli.converter.FractionConverter;
import org.hyperledger.besu.cli.converter.PercentageConverter;
import org.hyperledger.besu.cli.util.CommandLineUtils;
import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.eth.transactions.ImmutableTransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.plugin.services.TransactionPoolValidatorService;
import org.hyperledger.besu.util.number.Fraction;
import org.hyperledger.besu.util.number.Percentage;

import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import picocli.CommandLine;

/** The Transaction pool Cli stable options. */
public class TransactionPoolOptions implements CLIOptions<TransactionPoolConfiguration> {
  private static final String TX_POOL_IMPLEMENTATION = "--tx-pool";

  /** Use TX_POOL_NO_LOCAL_PRIORITY instead */
  @Deprecated(forRemoval = true)
  private static final String TX_POOL_DISABLE_LOCALS = "--tx-pool-disable-locals";

  private static final String TX_POOL_NO_LOCAL_PRIORITY = "--tx-pool-no-local-priority";
  private static final String TX_POOL_ENABLE_SAVE_RESTORE = "--tx-pool-enable-save-restore";
  private static final String TX_POOL_SAVE_FILE = "--tx-pool-save-file";
  private static final String TX_POOL_PRICE_BUMP = "--tx-pool-price-bump";
  private static final String TX_POOL_BLOB_PRICE_BUMP = "--tx-pool-blob-price-bump";
  private static final String RPC_TX_FEECAP = "--rpc-tx-feecap";
  private static final String STRICT_TX_REPLAY_PROTECTION_ENABLED_FLAG =
      "--strict-tx-replay-protection-enabled";
  private static final String TX_POOL_PRIORITY_SENDERS = "--tx-pool-priority-senders";
  private static final String TX_POOL_MIN_GAS_PRICE = "--tx-pool-min-gas-price";

  private TransactionPoolValidatorService transactionPoolValidatorService;

  @CommandLine.Option(
      names = {TX_POOL_IMPLEMENTATION},
      paramLabel = "<Enum>",
      description = "The Transaction Pool implementation to use(default: ${DEFAULT-VALUE})",
      arity = "0..1")
  private TransactionPoolConfiguration.Implementation txPoolImplementation = LAYERED;

  @CommandLine.Option(
      names = {TX_POOL_NO_LOCAL_PRIORITY, TX_POOL_DISABLE_LOCALS},
      paramLabel = "<Boolean>",
      description =
          "Set to true if senders of transactions sent via RPC should not have priority (default: ${DEFAULT-VALUE})",
      fallbackValue = "true",
      arity = "0..1")
  private Boolean noLocalPriority = TransactionPoolConfiguration.DEFAULT_NO_LOCAL_PRIORITY;

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
      names = {TX_POOL_BLOB_PRICE_BUMP},
      paramLabel = "<Percentage>",
      converter = PercentageConverter.class,
      description =
          "Blob price bump percentage to replace an already existing transaction blob tx (default: ${DEFAULT-VALUE})",
      arity = "1")
  private Percentage blobPriceBump = TransactionPoolConfiguration.DEFAULT_BLOB_PRICE_BUMP;

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

  @CommandLine.Option(
      names = {TX_POOL_PRIORITY_SENDERS},
      split = ",",
      paramLabel = "Comma separated list of addresses",
      description =
          "Pending transactions sent exclusively by these addresses, from any source, are prioritized and only evicted after all others. If not specified, then only the senders submitting transactions via RPC have priority (default: ${DEFAULT-VALUE})",
      arity = "1..*")
  private Set<Address> prioritySenders = TransactionPoolConfiguration.DEFAULT_PRIORITY_SENDERS;

  @CommandLine.Option(
      names = {TX_POOL_MIN_GAS_PRICE},
      paramLabel = "<Wei>",
      description =
          "Transactions with gas price (in Wei) lower than this minimum will not be accepted into the txpool"
              + "(not to be confused with min-gas-price, that is applied on block creation) (default: ${DEFAULT-VALUE})",
      arity = "1")
  private Wei minGasPrice = TransactionPoolConfiguration.DEFAULT_TX_POOL_MIN_GAS_PRICE;

  @CommandLine.ArgGroup(
      validate = false,
      heading = "@|bold Tx Pool Layered Implementation Options|@%n")
  private final Layered layeredOptions = new Layered();

  static class Layered {
    private static final String TX_POOL_LAYER_MAX_CAPACITY = "--tx-pool-layer-max-capacity";
    private static final String TX_POOL_MAX_PRIORITIZED = "--tx-pool-max-prioritized";
    private static final String TX_POOL_MAX_PRIORITIZED_BY_TYPE =
        "--tx-pool-max-prioritized-by-type";
    private static final String TX_POOL_MAX_FUTURE_BY_SENDER = "--tx-pool-max-future-by-sender";
    private static final String TX_POOL_MIN_SCORE = "--tx-pool-min-score";

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
        names = {TX_POOL_MAX_PRIORITIZED_BY_TYPE},
        paramLabel = "MAP<TYPE,INTEGER>",
        split = ",",
        description =
            "Max number of pending transactions, of a specific type, that are prioritized and thus kept sorted (default: ${DEFAULT-VALUE})",
        arity = "1")
    Map<TransactionType, Integer> txPoolMaxPrioritizedByType =
        TransactionPoolConfiguration.DEFAULT_MAX_PRIORITIZED_TRANSACTIONS_BY_TYPE;

    @CommandLine.Option(
        names = {TX_POOL_MAX_FUTURE_BY_SENDER},
        paramLabel = MANDATORY_INTEGER_FORMAT_HELP,
        description =
            "Max number of future pending transactions allowed for a single sender (default: ${DEFAULT-VALUE})",
        arity = "1")
    Integer txPoolMaxFutureBySender = TransactionPoolConfiguration.DEFAULT_MAX_FUTURE_BY_SENDER;

    @CommandLine.Option(
        names = {TX_POOL_MIN_SCORE},
        paramLabel = "<Byte>",
        description =
            "Remove a pending transaction from the txpool if its score is lower than this value."
                + "Accepts values between -128 and 127 (default: ${DEFAULT-VALUE})",
        arity = "1")
    Byte minScore = TransactionPoolConfiguration.DEFAULT_TX_POOL_MIN_SCORE;
  }

  @CommandLine.ArgGroup(
      validate = false,
      heading = "@|bold Tx Pool Sequenced Implementation Options|@%n")
  private final Sequenced sequencedOptions = new Sequenced();

  static class Sequenced {
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

  @CommandLine.ArgGroup(validate = false)
  private final TransactionPoolOptions.Unstable unstableOptions =
      new TransactionPoolOptions.Unstable();

  static class Unstable {
    private static final String TX_MESSAGE_KEEP_ALIVE_SEC_FLAG =
        "--Xincoming-tx-messages-keep-alive-seconds";

    private static final String ETH65_TX_ANNOUNCED_BUFFERING_PERIOD_FLAG =
        "--Xeth65-tx-announced-buffering-period-milliseconds";

    @CommandLine.Option(
        names = {TX_MESSAGE_KEEP_ALIVE_SEC_FLAG},
        paramLabel = "<INTEGER>",
        hidden = true,
        description =
            "Keep alive of incoming transaction messages in seconds (default: ${DEFAULT-VALUE})",
        arity = "1")
    private Integer txMessageKeepAliveSeconds =
        TransactionPoolConfiguration.Unstable.DEFAULT_TX_MSG_KEEP_ALIVE;

    @CommandLine.Option(
        names = {ETH65_TX_ANNOUNCED_BUFFERING_PERIOD_FLAG},
        paramLabel = "<LONG>",
        converter = DurationMillisConverter.class,
        hidden = true,
        description =
            "The period for which the announced transactions remain in the buffer before being requested from the peers in milliseconds (default: ${DEFAULT-VALUE})",
        arity = "1")
    private Duration eth65TrxAnnouncedBufferingPeriod =
        TransactionPoolConfiguration.Unstable.ETH65_TRX_ANNOUNCED_BUFFERING_PERIOD;
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
   * Set the plugin txpool validator service
   *
   * @param transactionPoolValidatorService the plugin txpool validator service
   */
  public void setPluginTransactionValidatorService(
      final TransactionPoolValidatorService transactionPoolValidatorService) {
    this.transactionPoolValidatorService = transactionPoolValidatorService;
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
    options.noLocalPriority = config.getNoLocalPriority();
    options.priceBump = config.getPriceBump();
    options.blobPriceBump = config.getBlobPriceBump();
    options.txFeeCap = config.getTxFeeCap();
    options.saveFile = config.getSaveFile();
    options.strictTxReplayProtectionEnabled = config.getStrictTransactionReplayProtectionEnabled();
    options.prioritySenders = config.getPrioritySenders();
    options.minGasPrice = config.getMinGasPrice();
    options.layeredOptions.txPoolLayerMaxCapacity =
        config.getPendingTransactionsLayerMaxCapacityBytes();
    options.layeredOptions.txPoolMaxPrioritized = config.getMaxPrioritizedTransactions();
    options.layeredOptions.txPoolMaxPrioritizedByType =
        config.getMaxPrioritizedTransactionsByType();
    options.layeredOptions.txPoolMaxFutureBySender = config.getMaxFutureBySender();
    options.layeredOptions.minScore = config.getMinScore();
    options.sequencedOptions.txPoolLimitByAccountPercentage =
        config.getTxPoolLimitByAccountPercentage();
    options.sequencedOptions.txPoolMaxSize = config.getTxPoolMaxSize();
    options.sequencedOptions.pendingTxRetentionPeriod = config.getPendingTxRetentionPeriod();
    options.transactionPoolValidatorService = config.getTransactionPoolValidatorService();
    options.unstableOptions.txMessageKeepAliveSeconds =
        config.getUnstable().getTxMessageKeepAliveSeconds();
    options.unstableOptions.eth65TrxAnnouncedBufferingPeriod =
        config.getUnstable().getEth65TrxAnnouncedBufferingPeriod();

    return options;
  }

  /**
   * Validate that there are no inconsistencies in the specified options. For example that the
   * options are valid for the selected implementation.
   *
   * @param commandLine the full commandLine to check all the options specified by the user
   * @param genesisConfigOptions the genesis config options
   */
  public void validate(
      final CommandLine commandLine, final GenesisConfigOptions genesisConfigOptions) {
    CommandLineUtils.failIfOptionDoesntMeetRequirement(
        commandLine,
        "Could not use legacy or sequenced transaction pool options with layered implementation",
        !txPoolImplementation.equals(LAYERED),
        CommandLineUtils.getCLIOptionNames(Sequenced.class));

    CommandLineUtils.failIfOptionDoesntMeetRequirement(
        commandLine,
        "Could not use layered transaction pool options with legacy or sequenced implementation",
        !txPoolImplementation.equals(LEGACY) && !txPoolImplementation.equals(SEQUENCED),
        CommandLineUtils.getCLIOptionNames(Layered.class));

    CommandLineUtils.failIfOptionDoesntMeetRequirement(
        commandLine,
        "Price bump option is not compatible with zero base fee market",
        !genesisConfigOptions.isZeroBaseFee(),
        List.of(TX_POOL_PRICE_BUMP));
  }

  @Override
  public TransactionPoolConfiguration toDomainObject() {
    return ImmutableTransactionPoolConfiguration.builder()
        .txPoolImplementation(txPoolImplementation)
        .enableSaveRestore(saveRestoreEnabled)
        .noLocalPriority(noLocalPriority)
        .priceBump(priceBump)
        .blobPriceBump(blobPriceBump)
        .txFeeCap(txFeeCap)
        .saveFile(saveFile)
        .strictTransactionReplayProtectionEnabled(strictTxReplayProtectionEnabled)
        .prioritySenders(prioritySenders)
        .minGasPrice(minGasPrice)
        .pendingTransactionsLayerMaxCapacityBytes(layeredOptions.txPoolLayerMaxCapacity)
        .maxPrioritizedTransactions(layeredOptions.txPoolMaxPrioritized)
        .maxPrioritizedTransactionsByType(layeredOptions.txPoolMaxPrioritizedByType)
        .maxFutureBySender(layeredOptions.txPoolMaxFutureBySender)
        .minScore(layeredOptions.minScore)
        .txPoolLimitByAccountPercentage(sequencedOptions.txPoolLimitByAccountPercentage)
        .txPoolMaxSize(sequencedOptions.txPoolMaxSize)
        .pendingTxRetentionPeriod(sequencedOptions.pendingTxRetentionPeriod)
        .transactionPoolValidatorService(transactionPoolValidatorService)
        .unstable(
            ImmutableTransactionPoolConfiguration.Unstable.builder()
                .txMessageKeepAliveSeconds(unstableOptions.txMessageKeepAliveSeconds)
                .eth65TrxAnnouncedBufferingPeriod(unstableOptions.eth65TrxAnnouncedBufferingPeriod)
                .build())
        .build();
  }

  @Override
  public List<String> getCLIOptions() {
    return CommandLineUtils.getCLIOptions(this, new TransactionPoolOptions());
  }

  /**
   * Is price bump option set?
   *
   * @param commandLine the command line
   * @return true is tx-pool-price-bump is set
   */
  public boolean isPriceBumpSet(final CommandLine commandLine) {
    return CommandLineUtils.isOptionSet(commandLine, TransactionPoolOptions.TX_POOL_PRICE_BUMP);
  }

  /**
   * Is min gas price option set?
   *
   * @param commandLine the command line
   * @return true if tx-pool-min-gas-price is set
   */
  public boolean isMinGasPriceSet(final CommandLine commandLine) {
    return CommandLineUtils.isOptionSet(commandLine, TransactionPoolOptions.TX_POOL_MIN_GAS_PRICE);
  }
}
