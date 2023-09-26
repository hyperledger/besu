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
 *
 */
package org.hyperledger.besu.evmtool;

import static java.nio.charset.StandardCharsets.UTF_8;
import static picocli.CommandLine.ScopeType.INHERIT;

import org.hyperledger.besu.cli.config.NetworkName;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.vm.CachingBlockHashLookup;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.EvmSpecVersion;
import org.hyperledger.besu.evm.account.AccountStorageEntry;
import org.hyperledger.besu.evm.code.CodeInvalid;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.log.LogsBloomFilter;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evm.tracing.StandardJsonTracer;
import org.hyperledger.besu.evm.worldstate.WorldState;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.metrics.MetricsSystemModule;
import org.hyperledger.besu.util.LogConfigurator;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.common.base.Joiner;
import com.google.common.base.Stopwatch;
import io.vertx.core.json.JsonObject;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    description = "This command evaluates EVM transactions.",
    abbreviateSynopsis = true,
    name = "evm",
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider.class,
    sortOptions = false,
    header = "Usage:",
    synopsisHeading = "%n",
    descriptionHeading = "%nDescription:%n%n",
    optionListHeading = "%nOptions:%n",
    footerHeading = "%n",
    footer = "Hyperledger Besu is licensed under the Apache License 2.0",
    subcommands = {
      BenchmarkSubCommand.class,
      B11rSubCommand.class,
      CodeValidateSubCommand.class,
      StateTestSubCommand.class,
      T8nSubCommand.class,
      T8nServerSubCommand.class
    })
public class EvmToolCommand implements Runnable {

  @Option(
      names = {"--code"},
      paramLabel = "<code>",
      description = "Byte stream of code to be executed.")
  void setBytes(final String optionValue) {
    codeBytes = Bytes.fromHexString(optionValue.replace(" ", ""));
  }

  private Bytes codeBytes = Bytes.EMPTY;

  @Option(
      names = {"--gas"},
      description = "Amount of gas for this invocation.",
      paramLabel = "<int>")
  private final Long gas = 10_000_000_000L;

  @Option(
      names = {"--price"},
      description = "Price of gas (in GWei) for this invocation",
      paramLabel = "<int>")
  private final Wei gasPriceGWei = Wei.ZERO;

  @Option(
      names = {"--sender"},
      paramLabel = "<address>",
      description = "Calling address for this invocation.")
  private final Address sender = Address.fromHexString("0x00");

  @Option(
      names = {"--receiver"},
      paramLabel = "<address>",
      description = "Receiving address for this invocation.")
  private final Address receiver = Address.fromHexString("0x00");

  @Option(
      names = {"--input"},
      paramLabel = "<code>",
      description = "The CALLDATA for this invocation")
  private final Bytes callData = Bytes.EMPTY;

  @Option(
      names = {"--value"},
      description = "The amount of ether attached to this invocation",
      paramLabel = "<int>")
  private final Wei ethValue = Wei.ZERO;

  @Option(
      names = {"--json", "--trace"},
      description = "Trace each opcode as a json object.",
      scope = INHERIT)
  final Boolean showJsonResults = false;

  @Option(
      names = {"--json-alloc"},
      description = "Output the final allocations after a run.",
      scope = INHERIT)
  final Boolean showJsonAlloc = false;

  @Option(
      names = {"--memory", "--trace.memory"},
      description =
          "Show the full memory output in tracing for each op. Default is not to show memory.",
      scope = INHERIT,
      negatable = true)
  final Boolean showMemory = false;

  @Option(
      names = {"--trace.nostack"},
      description = "Show the operand stack in tracing for each op. Default is to show stack.",
      scope = INHERIT,
      negatable = true)
  final Boolean hideStack = false;

  @Option(
      names = {"--trace.returndata"},
      description =
          "Show the return data in tracing for each op when present. Default is to show return data.",
      scope = INHERIT,
      negatable = true)
  final Boolean showReturnData = false;

  @Option(
      names = {"--notime"},
      description = "Don't include time data in summary output.",
      scope = INHERIT,
      negatable = true)
  final Boolean noTime = false;

  @Option(
      names = {"--prestate", "--genesis"},
      description = "The genesis file containing account data for this invocation.")
  private final File genesisFile = null;

  @Option(
      names = {"--chain"},
      description = "Name of a well known network that will be used for this invocation.")
  private final NetworkName network = null;

  @Option(
      names = {"--repeat"},
      description = "Number of times to repeat for benchmarking.")
  private final Integer repeat = 0;

  @Option(
      names = {"-v", "--version"},
      versionHelp = true,
      description = "display version info")
  boolean versionInfoRequested;

  static final Joiner STORAGE_JOINER = Joiner.on(",\n");
  private final EvmToolCommandOptionsModule daggerOptions = new EvmToolCommandOptionsModule();
  PrintWriter out;
  InputStream in;

  public EvmToolCommand() {
    this(
        new ByteArrayInputStream(new byte[0]),
        new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out, UTF_8)), true));
  }

  public EvmToolCommand(final InputStream in, final PrintWriter out) {
    this.in = in;
    this.out = out;
  }

  void execute(final String... args) {
    execute(System.in, new PrintWriter(System.out, true, UTF_8), args);
  }

  void execute(final InputStream input, final PrintWriter output, final String[] args) {
    final CommandLine commandLine = new CommandLine(this).setOut(output);
    out = output;
    in = input;

    // don't require exact case to match enum values
    commandLine.setCaseInsensitiveEnumValuesAllowed(true);

    // add dagger-injected options
    commandLine.addMixin("Dagger Options", daggerOptions);

    // add sub commands here
    commandLine.registerConverter(Address.class, Address::fromHexString);
    commandLine.registerConverter(Bytes.class, Bytes::fromHexString);
    commandLine.registerConverter(Wei.class, arg -> Wei.of(Long.parseUnsignedLong(arg)));

    // change negation regexp so --nomemory works.  See
    // https://picocli.info/#_customizing_negatable_options
    commandLine.setNegatableOptionTransformer(
        new CommandLine.RegexTransformer.Builder()
            .addPattern("^--no(\\w(-|\\w)*)$", "--$1", "--[no]$1")
            .addPattern("^--trace.no(\\w(-|\\w)*)$", "--trace.$1", "--trace.[no]$1")
            .addPattern("^--(\\w(-|\\w)*)$", "--no$1", "--[no]$1")
            .addPattern("^--trace.(\\w(-|\\w)*)$", "--trace.no$1", "--trace.[no]$1")
            .build());

    // Enumerate forks to support execution-spec-tests
    addForkHelp(commandLine.getSubcommands().get("t8n"));
    addForkHelp(commandLine.getSubcommands().get("t8n-server"));

    commandLine.setExecutionStrategy(new CommandLine.RunLast());
    commandLine.execute(args);
  }

  private static void addForkHelp(final CommandLine subCommandLine) {
    subCommandLine
        .getHelpSectionMap()
        .put("forks_header", help -> help.createHeading("%nKnown Forks:%n"));
    subCommandLine
        .getHelpSectionMap()
        .put(
            "forks",
            help ->
                help.createTextTable(
                        Arrays.stream(EvmSpecVersion.values())
                            .collect(
                                Collectors.toMap(
                                    EvmSpecVersion::getName,
                                    EvmSpecVersion::getDescription,
                                    (a, b) -> b,
                                    LinkedHashMap::new)))
                    .toString());
    List<String> keys = new ArrayList<>(subCommandLine.getHelpSectionKeys());
    int index = keys.indexOf(CommandLine.Model.UsageMessageSpec.SECTION_KEY_FOOTER_HEADING);
    keys.add(index, "forks_header");
    keys.add(index + 1, "forks");

    subCommandLine.setHelpSectionKeys(keys);
  }

  @Override
  public void run() {
    LogConfigurator.setLevel("", "OFF");
    try {
      final EvmToolComponent component =
          DaggerEvmToolComponent.builder()
              .dataStoreModule(new DataStoreModule())
              .genesisFileModule(
                  network == null
                      ? genesisFile == null
                          ? GenesisFileModule.createGenesisModule(NetworkName.DEV)
                          : GenesisFileModule.createGenesisModule(genesisFile)
                      : GenesisFileModule.createGenesisModule(network))
              .evmToolCommandOptionsModule(daggerOptions)
              .metricsSystemModule(new MetricsSystemModule())
              .build();

      final BlockHeader blockHeader =
          BlockHeaderBuilder.create()
              .parentHash(Hash.EMPTY)
              .coinbase(Address.ZERO)
              .difficulty(Difficulty.ONE)
              .number(1)
              .gasLimit(5000)
              .timestamp(Instant.now().toEpochMilli())
              .ommersHash(Hash.EMPTY_LIST_HASH)
              .stateRoot(Hash.EMPTY_TRIE_HASH)
              .transactionsRoot(Hash.EMPTY)
              .receiptsRoot(Hash.EMPTY)
              .logsBloom(LogsBloomFilter.empty())
              .gasUsed(0)
              .extraData(Bytes.EMPTY)
              .mixHash(Hash.EMPTY)
              .nonce(0)
              .blockHeaderFunctions(new MainnetBlockHeaderFunctions())
              .buildBlockHeader();

      int remainingIters = this.repeat;
      final ProtocolSpec protocolSpec =
          component.getProtocolSpec().apply(BlockHeaderBuilder.createDefault().buildBlockHeader());
      final Transaction tx =
          new Transaction(
              0,
              Wei.ZERO,
              Long.MAX_VALUE,
              Optional.ofNullable(receiver),
              Wei.ZERO,
              null,
              callData,
              sender,
              Optional.empty(),
              Optional.empty());

      final long intrinsicGasCost =
          protocolSpec
              .getGasCalculator()
              .transactionIntrinsicGasCost(tx.getPayload(), tx.isContractCreation());
      final long accessListCost =
          tx.getAccessList()
              .map(list -> protocolSpec.getGasCalculator().accessListGasCost(list))
              .orElse(0L);
      long txGas = gas - intrinsicGasCost - accessListCost;

      final EVM evm = protocolSpec.getEvm();
      Code code = evm.getCode(Hash.hash(codeBytes), codeBytes);
      if (!code.isValid()) {
        out.println(((CodeInvalid) code).getInvalidReason());
        return;
      }
      final Stopwatch stopwatch = Stopwatch.createUnstarted();
      long lastTime = 0;
      do {
        final boolean lastLoop = remainingIters == 0;

        final OperationTracer tracer = // You should have picked Mercy.
            lastLoop && showJsonResults
                ? new StandardJsonTracer(out, showMemory, !hideStack, showReturnData)
                : OperationTracer.NO_TRACING;

        WorldUpdater updater = component.getWorldUpdater();
        updater.getOrCreate(sender);
        updater.getOrCreate(receiver);

        MessageFrame initialMessageFrame =
            MessageFrame.builder()
                .type(MessageFrame.Type.MESSAGE_CALL)
                .worldUpdater(updater)
                .initialGas(txGas)
                .contract(Address.ZERO)
                .address(receiver)
                .originator(sender)
                .sender(sender)
                .gasPrice(gasPriceGWei)
                .inputData(callData)
                .value(ethValue)
                .apparentValue(ethValue)
                .code(code)
                .blockValues(blockHeader)
                .completer(c -> {})
                .miningBeneficiary(blockHeader.getCoinbase())
                .blockHashLookup(new CachingBlockHashLookup(blockHeader, component.getBlockchain()))
                .build();
        Deque<MessageFrame> messageFrameStack = initialMessageFrame.getMessageFrameStack();

        stopwatch.start();
        while (!messageFrameStack.isEmpty()) {
          final MessageFrame messageFrame = messageFrameStack.peek();
          protocolSpec.getTransactionProcessor().process(messageFrame, tracer);
          if (messageFrameStack.isEmpty()) {
            stopwatch.stop();
            if (lastTime == 0) {
              lastTime = stopwatch.elapsed().toNanos();
            }
            if (lastLoop) {
              if (messageFrame.getExceptionalHaltReason().isPresent()) {
                out.println(messageFrame.getExceptionalHaltReason().get());
              }
              if (messageFrame.getRevertReason().isPresent()) {
                out.println(new String(messageFrame.getRevertReason().get().toArray(), UTF_8));
              }
            }
          }

          if (lastLoop && messageFrameStack.isEmpty()) {
            final long evmGas = txGas - messageFrame.getRemainingGas();
            final JsonObject resultLine = new JsonObject();
            resultLine.put("gasUser", "0x" + Long.toHexString(evmGas));
            if (!noTime) {
              resultLine.put("timens", lastTime).put("time", lastTime / 1000);
            }
            resultLine
                .put("gasTotal", "0x" + Long.toHexString(evmGas))
                .put("output", messageFrame.getOutputData().toHexString());
            out.println();
            out.println(resultLine);
          }
        }
        lastTime = stopwatch.elapsed().toNanos();
        stopwatch.reset();
        if (showJsonAlloc && lastLoop) {
          updater.commit();
          WorldState worldState = component.getWorldState();
          dumpWorldState(worldState, out);
        }
      } while (remainingIters-- > 0);

    } catch (final IOException e) {
      System.err.println("Unable to create Genesis module");
      e.printStackTrace(System.out);
    }
  }

  public static void dumpWorldState(final WorldState worldState, final PrintWriter out) {
    out.println("{");
    worldState
        .streamAccounts(Bytes32.ZERO, Integer.MAX_VALUE)
        .sorted(Comparator.comparing(o -> o.getAddress().get().toHexString()))
        .forEach(
            account -> {
              out.println(
                  " \"" + account.getAddress().map(Address::toHexString).orElse("-") + "\": {");
              if (account.getCode() != null && !account.getCode().isEmpty()) {
                out.println("  \"code\": \"" + account.getCode().toHexString() + "\",");
              }
              NavigableMap<Bytes32, AccountStorageEntry> storageEntries =
                  account.storageEntriesFrom(Bytes32.ZERO, Integer.MAX_VALUE);
              if (!storageEntries.isEmpty()) {
                out.println("  \"storage\": {");
                out.println(
                    STORAGE_JOINER.join(
                        storageEntries.values().stream()
                            .map(
                                accountStorageEntry ->
                                    "   \""
                                        + accountStorageEntry
                                            .getKey()
                                            .map(UInt256::toHexString)
                                            .orElse("-")
                                        + "\": \""
                                        + accountStorageEntry.getValue().toHexString()
                                        + "\"")
                            .toList()));
                out.println("  },");
              }
              out.print("  \"balance\": \"" + account.getBalance().toShortHexString() + "\"");
              if (account.getNonce() > 0) {
                out.println(",");
                out.println(
                    "  \"nonce\": \""
                        + Bytes.ofUnsignedLong(account.getNonce()).toShortHexString()
                        + "\"");
              } else {
                out.println();
              }
              out.println(" },");
            });
    out.println("}");
    out.flush();
  }
}
