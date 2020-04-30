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
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.vm.BlockHashLookup;
import org.hyperledger.besu.ethereum.vm.Code;
import org.hyperledger.besu.ethereum.vm.EVM;
import org.hyperledger.besu.ethereum.vm.ExceptionalHaltReason;
import org.hyperledger.besu.ethereum.vm.MessageFrame;
import org.hyperledger.besu.ethereum.vm.ehalt.ExceptionalHaltException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;

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

  void parse(
      final CommandLine.AbstractParseResultHandler<List<Object>> resultHandler,
      final CommandLine.DefaultExceptionHandler<List<Object>> exceptionHandler,
      final String[] args) {

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
              .dataStoreModule(new InMemoryDataStoreModule())
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
      do {
        final boolean lastLoop = repeat == 0;
        final MessageFrame messageFrame =
            MessageFrame.builder()
                .type(MessageFrame.Type.MESSAGE_CALL)
                .messageFrameStack(new ArrayDeque<>())
                .blockchain(component.getBlockchain())
                .worldState(component.getWorldUpdater())
                .initialGas(gas)
                .contract(Address.ZERO)
                .address(receiver)
                .originator(sender)
                .gasPrice(gasPriceGWei)
                .inputData(callData)
                .sender(Address.ZERO)
                .value(ethValue)
                .apparentValue(ethValue)
                .code(new Code(codeHexString))
                .blockHeader(blockHeader)
                .depth(0)
                .completer(c -> {})
                .miningBeneficiary(blockHeader.getCoinbase())
                .blockHashLookup(new BlockHashLookup(blockHeader, component.getBlockchain()))
                .contractAccountVersion(Account.DEFAULT_VERSION)
                .build();

        messageFrame.setState(MessageFrame.State.CODE_EXECUTING);
        final EVM evm = component.getEvmAtBlock().apply(0);

        final Stopwatch stopwatch = Stopwatch.createStarted();
        evm.runToHalt(
            messageFrame,
            (frame, currentGasCost, executeOperation) -> {
              if (showJsonResults && lastLoop) {
                System.out.println(createEvmTraceOperation(messageFrame));
              }
              executeOperation.execute();
            });
        stopwatch.stop();

        if (lastLoop) {
          System.out.println(
              new JsonObject()
                  .put(
                      "gasUser",
                      gas.minus(messageFrame.getRemainingGas()).asUInt256().toShortHexString())
                  .put("timens", stopwatch.elapsed().toNanos())
                  .put("time", stopwatch.elapsed().toNanos() / 1000));
        }
      } while (repeat-- > 0);

    } catch (final IOException | ExceptionalHaltException e) {
      LOG.fatal(e);
    }
  }

  private JsonObject createEvmTraceOperation(final MessageFrame messageFrame) {
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
    results.put("pc", messageFrame.getPC());
    results.put("op", messageFrame.getCurrentOperation().getOpcode());
    results.put("gas", messageFrame.getRemainingGas().asUInt256().toShortHexString());

    results.put(
        "gasCost",
        messageFrame.getCurrentOperation().cost(messageFrame).asUInt256().toShortHexString());
    if (!showMemory) {
      results.put(
          "memory",
          messageFrame.readMemory(UInt256.ZERO, messageFrame.memoryWordSize()).toHexString());
    }
    results.put("memSize", messageFrame.memoryByteSize());
    results.put("depth", messageFrame.getMessageStackDepth() + 1);
    results.put("stack", stack);
    results.put("error", error);
    results.put("opName", messageFrame.getCurrentOperation().getName());
    return results;
  }
}
