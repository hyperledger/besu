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
import org.hyperledger.besu.ethereum.vm.BlockHashLookup;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.code.CodeInvalid;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.log.LogsBloomFilter;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.processor.MessageCallProcessor;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evm.tracing.StandardJsonTracer;
import org.hyperledger.besu.evm.worldstate.WorldState;
import org.hyperledger.besu.util.Log4j2ConfiguratorUtil;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.common.base.Joiner;
import com.google.common.base.Stopwatch;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    subcommands = {StateTestSubCommand.class, CodeValidateSubCommand.class})
public class EvmToolCommand implements Runnable {

  private static final Logger LOG = LoggerFactory.getLogger(EvmToolCommand.class);

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
      names = {"--json"},
      description = "Trace each opcode as a json object.",
      scope = INHERIT)
  final Boolean showJsonResults = false;

  @Option(
      names = {"--json-alloc"},
      description = "Output the final allocations after a run.",
      scope = INHERIT)
  final Boolean showJsonAlloc = false;

  @Option(
      names = {"--nomemory"},
      description = "Disable showing the full memory output for each op.",
      scope = INHERIT)
  final Boolean noMemory = false;

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

  static final Joiner STORAGE_JOINER = Joiner.on(",\n");
  private final EvmToolCommandOptionsModule daggerOptions = new EvmToolCommandOptionsModule();
  private PrintWriter out =
      new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out, UTF_8)), true);

  void parse(final CommandLine.IExecutionStrategy resultHandler, final String[] args) {

    final CommandLine commandLine = new CommandLine(this);
    out = commandLine.getOut();
    commandLine.addMixin("Dagger Options", daggerOptions);

    // add sub commands here
    commandLine.registerConverter(Address.class, Address::fromHexString);
    commandLine.registerConverter(Bytes.class, Bytes::fromHexString);
    commandLine.registerConverter(Wei.class, arg -> Wei.of(Long.parseUnsignedLong(arg)));

    commandLine.setExecutionStrategy(resultHandler).execute(args);
  }

  @Override
  public void run() {
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

      Log4j2ConfiguratorUtil.setAllLevels("", repeat == 0 ? Level.INFO : Level.OFF);
      int remainingIters = this.repeat;
      Log4j2ConfiguratorUtil.setLevel(
          "org.hyperledger.besu.ethereum.mainnet.ProtocolScheduleBuilder", Level.OFF);
      final ProtocolSpec protocolSpec = component.getProtocolSpec().apply(0);
      Log4j2ConfiguratorUtil.setLevel(
          "org.hyperledger.besu.ethereum.mainnet.ProtocolScheduleBuilder", null);
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

      final PrecompileContractRegistry precompileContractRegistry =
          protocolSpec.getPrecompileContractRegistry();
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
                ? new StandardJsonTracer(System.out, !noMemory)
                : OperationTracer.NO_TRACING;

        var updater = component.getWorldUpdater();
        updater.getOrCreate(sender);
        updater.getOrCreate(receiver);

        final Deque<MessageFrame> messageFrameStack = new ArrayDeque<>();
        messageFrameStack.add(
            MessageFrame.builder()
                .type(MessageFrame.Type.MESSAGE_CALL)
                .messageFrameStack(messageFrameStack)
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
                .depth(0)
                .completer(c -> {})
                .miningBeneficiary(blockHeader.getCoinbase())
                .blockHashLookup(new BlockHashLookup(blockHeader, component.getBlockchain()))
                .build());

        final MessageCallProcessor mcp = new MessageCallProcessor(evm, precompileContractRegistry);
        stopwatch.start();
        while (!messageFrameStack.isEmpty()) {
          final MessageFrame messageFrame = messageFrameStack.peek();
          mcp.process(messageFrame, tracer);
          if (lastLoop) {
            if (messageFrame.getExceptionalHaltReason().isPresent()) {
              out.println(messageFrame.getExceptionalHaltReason().get());
            }
            if (messageFrame.getRevertReason().isPresent()) {
              out.println(new String(messageFrame.getRevertReason().get().toArray(), UTF_8));
            }
          }
          if (messageFrameStack.isEmpty()) {
            stopwatch.stop();
            if (lastTime == 0) {
              lastTime = stopwatch.elapsed().toNanos();
            }
          }

          if (lastLoop && messageFrameStack.isEmpty()) {
            final long evmGas = txGas - messageFrame.getRemainingGas();
            out.println();
            out.println(
                new JsonObject()
                    .put("gasUser", "0x" + Long.toHexString(evmGas))
                    .put("timens", lastTime)
                    .put("time", lastTime / 1000)
                    .put("gasTotal", "0x" + Long.toHexString(evmGas))
                    .put("output", messageFrame.getOutputData().toHexString()));
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
      LOG.error("Unable to create Genesis module", e);
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
              if (account.getCode() != null && account.getCode().size() > 0) {
                out.println("  \"code\": \"" + account.getCode().toHexString() + "\",");
              }
              var storageEntries = account.storageEntriesFrom(Bytes32.ZERO, Integer.MAX_VALUE);
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
                            .collect(Collectors.toList())));
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
