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

import org.hyperledger.besu.cli.config.NetworkName;
import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.Gas;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.LogsBloomFilter;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.mainnet.MainnetMessageCallProcessor;
import org.hyperledger.besu.ethereum.mainnet.PrecompileContractRegistry;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.vm.BlockHashLookup;
import org.hyperledger.besu.ethereum.vm.Code;
import org.hyperledger.besu.ethereum.vm.EVM;
import org.hyperledger.besu.ethereum.vm.ExceptionalHaltReason;
import org.hyperledger.besu.ethereum.vm.MessageFrame;
import org.hyperledger.besu.ethereum.vm.Operation;
import org.hyperledger.besu.ethereum.vm.OperationTracer;
import org.hyperledger.besu.ethereum.vm.ehalt.ExceptionalHaltException;
import org.hyperledger.besu.ethereum.vm.operations.CallOperation;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

import com.google.common.base.Stopwatch;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    description = "This command evaluates EVM transactions.",
    abbreviateSynopsis = true,
    name = "evm",
    mixinStandardHelpOptions = true,
    sortOptions = false,
    header = "Usage:",
    synopsisHeading = "%n",
    descriptionHeading = "%nDescription:%n%n",
    optionListHeading = "%nOptions:%n",
    footerHeading = "%n",
    footer = "Hyperledger Besu is licensed under the Apache License 2.0")
public class EvmToolCommand implements Runnable {

  private static final Logger LOG = LogManager.getLogger();

  @Option(
      names = {"--code"},
      paramLabel = "<code>",
      description = "code to be executed")
  private final Bytes codeHexString = Bytes.EMPTY;

  @Option(
      names = {"--gas"},
      paramLabel = "<int>")
  private final Gas gas = Gas.of(10_000_000_000L);

  @Option(
      names = {"--price"},
      paramLabel = "<int>")
  private final Wei gasPriceGWei = Wei.ZERO;

  @Option(
      names = {"--sender"},
      paramLabel = "<address>",
      description = "address of ORIGIN")
  private final Address sender = Address.fromHexString("0x00");

  @Option(
      names = {"--receiver"},
      paramLabel = "<address>",
      description = "address of ADDRESS")
  private final Address receiver = Address.fromHexString("0x00");

  @Option(
      names = {"--input"},
      paramLabel = "<code>",
      description = "CALLDATA")
  private final Bytes callData = Bytes.EMPTY;

  @Option(
      names = {"--value"},
      paramLabel = "<int>")
  private final Wei ethValue = Wei.ZERO;

  @Option(
      names = {"--json"},
      description = "output json output for each opcode")
  private final Boolean showJsonResults = false;

  @Option(
      names = {"--nomemory"},
      description = "disable showing the full memory output for each op")
  private final Boolean showMemory = true;

  @Option(
      names = {"--prestate", "--genesis"},
      description = "a chain specification, the same one that the client normally would use")
  private final File genesisFile = null;

  @Option(
      names = {"--chain"},
      description = "Name of a well know chain")
  private final NetworkName network = null;

  @Option(
      names = {"--repeat"},
      description = "Number of times to repeat before gathering timing")
  private final Integer repeat = 0;

  private final EvmToolCommandOptionsModule daggerOptions = new EvmToolCommandOptionsModule();
  private PrintStream out = System.out;

  void parse(
      final CommandLine.AbstractParseResultHandler<List<Object>> resultHandler,
      final CommandLine.DefaultExceptionHandler<List<Object>> exceptionHandler,
      final String[] args) {

    out = resultHandler.out();
    final CommandLine commandLine = new CommandLine(this);
    commandLine.addMixin("Dagger Options", daggerOptions);

    // add sub commands here

    commandLine.registerConverter(Address.class, Address::fromHexString);
    commandLine.registerConverter(Bytes.class, Bytes::fromHexString);
    commandLine.registerConverter(Gas.class, (arg) -> Gas.of(Long.parseUnsignedLong(arg)));
    commandLine.registerConverter(Wei.class, (arg) -> Wei.of(Long.parseUnsignedLong(arg)));

    commandLine.parseWithHandlers(resultHandler, exceptionHandler, args);
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
              .metricsSystemModule(new PrometheusMetricsSystemModule())
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

      Configurator.setAllLevels("", repeat == 0 ? Level.INFO : Level.OFF);
      int repeat = this.repeat;
      final ProtocolSpec<?> protocolSpec = component.getProtocolSpec().apply(0);
      final PrecompileContractRegistry precompileContractRegistry =
          protocolSpec.getPrecompileContractRegistry();
      final EVM evm = protocolSpec.getEvm();
      final Stopwatch stopwatch = Stopwatch.createUnstarted();
      long lastTime = 0;
      do {

        final boolean lastLoop = repeat == 0;
        final Deque<MessageFrame> messageFrameStack = new ArrayDeque<>();
        messageFrameStack.add(
            MessageFrame.builder()
                .type(MessageFrame.Type.MESSAGE_CALL)
                .messageFrameStack(messageFrameStack)
                .blockchain(component.getBlockchain())
                .worldState(component.getWorldUpdater())
                .initialGas(gas)
                .contract(Address.ZERO)
                .address(receiver)
                .originator(sender)
                .sender(sender)
                .gasPrice(gasPriceGWei)
                .inputData(callData)
                .value(ethValue)
                .apparentValue(ethValue)
                .code(new Code(codeHexString))
                .blockHeader(blockHeader)
                .depth(0)
                .completer(c -> {})
                .miningBeneficiary(blockHeader.getCoinbase())
                .blockHashLookup(new BlockHashLookup(blockHeader, component.getBlockchain()))
                .contractAccountVersion(Account.DEFAULT_VERSION)
                .build());

        final MainnetMessageCallProcessor mcp =
            new MainnetMessageCallProcessor(evm, precompileContractRegistry);
        stopwatch.start();
        while (!messageFrameStack.isEmpty()) {
          final MessageFrame messageFrame = messageFrameStack.peek();
          mcp.process(
              messageFrame, new EvmToolOperationTracer(lastLoop, precompileContractRegistry));
          if (lastLoop) {
            if (!messageFrame.getExceptionalHaltReasons().isEmpty()) {
              out.println(messageFrame.getExceptionalHaltReasons());
            }
            if (messageFrame.getRevertReason().isPresent()) {
              out.println(
                  new String(
                      messageFrame.getRevertReason().get().toArray(), StandardCharsets.UTF_8));
            }
          }
          if (messageFrameStack.isEmpty()) {
            stopwatch.stop();
            if (lastTime == 0) {
              lastTime = stopwatch.elapsed().toNanos();
            }
          }

          if (lastLoop && messageFrameStack.isEmpty()) {
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

            final Gas intrinsicGasCost =
                protocolSpec.getGasCalculator().transactionIntrinsicGasCost(tx);
            final Gas evmGas = gas.minus(messageFrame.getRemainingGas());
            out.println();
            out.println(
                new JsonObject()
                    .put("gasUser", evmGas.asUInt256().toShortHexString())
                    .put("timens", lastTime)
                    .put("time", lastTime / 1000)
                    .put("gasTotal", evmGas.plus(intrinsicGasCost).asUInt256().toShortHexString()));
          }
        }
        lastTime = stopwatch.elapsed().toNanos();
        stopwatch.reset();
      } while (repeat-- > 0);

    } catch (final IOException e) {
      LOG.fatal(e);
    }
  }

  private JsonObject createEvmTraceOperation(
      final MessageFrame messageFrame,
      final PrecompileContractRegistry precompileContractRegistry) {
    final JsonArray stack = new JsonArray();
    for (int i = 0; i < messageFrame.stackSize(); i++) {
      stack.add(messageFrame.getStackItem(i).toShortHexString());
    }
    final String error =
        messageFrame.getExceptionalHaltReasons().stream()
            .findFirst()
            .map(ExceptionalHaltReason::getDescription)
            .orElse(
                messageFrame
                    .getRevertReason()
                    .map(bytes -> new String(bytes.toArrayUnsafe(), StandardCharsets.UTF_8))
                    .orElse(""));

    final JsonObject results = new JsonObject();
    final Operation currentOp = messageFrame.getCurrentOperation();
    if (currentOp != null) {
      results.put("pc", messageFrame.getPC());
      results.put("op", Bytes.of(currentOp.getOpcode()).toHexString());
      results.put("opName", currentOp.getName());
      results.put("gasCost", currentOp.cost(messageFrame).asUInt256().toShortHexString());
    } else {
      final MessageFrame caller =
          messageFrame.getMessageFrameStack().toArray(new MessageFrame[0])[1];
      final CallOperation callOp = (CallOperation) caller.getCurrentOperation();

      results.put("address", messageFrame.getContractAddress().toShortHexString());
      results.put(
          "contract",
          precompileContractRegistry
              .get(messageFrame.getContractAddress(), Account.DEFAULT_VERSION)
              .getName());
      results.put(
          "gasCost",
          callOp
              .gasAvailableForChildCall(caller)
              .minus(messageFrame.getRemainingGas())
              .toHexString());
    }
    results.put("gas", messageFrame.getRemainingGas().asUInt256().toShortHexString());
    if (!showMemory) {
      results.put(
          "memory",
          messageFrame.readMemory(UInt256.ZERO, messageFrame.memoryWordSize()).toHexString());
    }
    results.put("memSize", messageFrame.memoryByteSize());
    results.put("depth", messageFrame.getMessageStackDepth() + 1);
    results.put("stack", stack);
    results.put("error", error);
    return results;
  }

  private class EvmToolOperationTracer implements OperationTracer {
    private final boolean lastLoop;
    private final PrecompileContractRegistry precompiledContractRegistries;

    EvmToolOperationTracer(
        final boolean lastLoop, final PrecompileContractRegistry precompiledContractRegistries) {
      this.lastLoop = lastLoop;
      this.precompiledContractRegistries = precompiledContractRegistries;
    }

    @Override
    public void traceExecution(
        final MessageFrame frame,
        final Optional<Gas> currentGasCost,
        final ExecuteOperation executeOperation)
        throws ExceptionalHaltException {
      if (showJsonResults && lastLoop) {
        final JsonObject op =
            EvmToolCommand.this.createEvmTraceOperation(frame, precompiledContractRegistries);
        final Stopwatch timer = Stopwatch.createStarted();
        executeOperation.execute();
        timer.stop();
        op.put("timens", timer.elapsed().toNanos());
        out.println(op);
      } else {
        executeOperation.execute();
      }
    }

    @Override
    public void tracePrecompileCall(
        final MessageFrame frame, final Gas gasRequirement, final Bytes output) {
      if (showJsonResults && lastLoop) {
        final JsonObject op =
            EvmToolCommand.this.createEvmTraceOperation(frame, precompiledContractRegistries);
        out.println(op);
      }
    }
  }
}
