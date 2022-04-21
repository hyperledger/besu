/*
 * Copyright contributors to Hyperledger Besu
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
package org.hyperledger.besu.evm.toy;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.MainnetEVMs;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.precompile.MainnetPrecompiledContracts;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;
import org.hyperledger.besu.evm.processor.MessageCallProcessor;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evm.tracing.StandardJsonTracer;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.io.PrintStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import com.google.common.base.Stopwatch;
import org.apache.tuweni.bytes.Bytes;
import picocli.CommandLine;
import picocli.CommandLine.ScopeType;

@CommandLine.Command(
    description = "This Toy evaluates EVM transactions.",
    abbreviateSynopsis = true,
    name = "evmtoy",
    mixinStandardHelpOptions = true,
    sortOptions = false,
    header = "Usage:",
    synopsisHeading = "%n",
    descriptionHeading = "%nDescription:%n%n",
    optionListHeading = "%nOptions:%n",
    footerHeading = "%n",
    footer = "Hyperledger Besu is licensed under the Apache License 2.0")
public class EvmToyCommand implements Runnable {

  @CommandLine.Option(
      names = {"--code"},
      paramLabel = "<code>",
      description = "Byte stream of code to be executed.")
  private final Bytes codeBytes = Bytes.EMPTY;

  @CommandLine.Option(
      names = {"--gas"},
      description = "Amount of gas for this invocation.",
      paramLabel = "<int>")
  private final Long gas = 10_000_000_000L;

  @CommandLine.Option(
      names = {"--price"},
      description = "Price of gas (in GWei) for this invocation",
      paramLabel = "<int>")
  private final Wei gasPriceGWei = Wei.ZERO;

  @CommandLine.Option(
      names = {"--sender"},
      paramLabel = "<address>",
      description = "Calling address for this invocation.")
  private final Address sender = Address.fromHexString("0x00");

  @CommandLine.Option(
      names = {"--receiver"},
      paramLabel = "<address>",
      description = "Receiving address for this invocation.")
  private final Address receiver = Address.fromHexString("0x00");

  @CommandLine.Option(
      names = {"--input"},
      paramLabel = "<code>",
      description = "The CALLDATA for this invocation")
  private final Bytes callData = Bytes.EMPTY;

  @CommandLine.Option(
      names = {"--value"},
      description = "The amount of ether attached to this invocation",
      paramLabel = "<int>")
  private final Wei ethValue = Wei.ZERO;

  @CommandLine.Option(
      names = {"--json"},
      description = "Trace each opcode as a json object.",
      scope = ScopeType.INHERIT)
  final Boolean showJsonResults = false;

  @CommandLine.Option(
      names = {"--nomemory"},
      description = "Disable showing the full memory output for each op.",
      scope = ScopeType.INHERIT)
  final Boolean noMemory = false;

  @CommandLine.Option(
      names = {"--repeat"},
      description = "Number of times to repeat for benchmarking.")
  private final Integer repeat = 0;

  private PrintStream out = System.out;

  void parse(
      final CommandLine.AbstractParseResultHandler<List<Object>> resultHandler,
      final CommandLine.DefaultExceptionHandler<List<Object>> exceptionHandler,
      final String[] args) {

    out = resultHandler.out();
    final CommandLine commandLine = new CommandLine(this);

    // add sub commands here
    commandLine.registerConverter(Address.class, Address::fromHexString);
    commandLine.registerConverter(Bytes.class, Bytes::fromHexString);
    commandLine.registerConverter(Wei.class, (arg) -> Wei.of(Long.parseUnsignedLong(arg)));

    commandLine.parseWithHandlers(resultHandler, exceptionHandler, args);
  }

  @Override
  public void run() {
    final WorldUpdater worldUpdater = new ToyWorld();
    worldUpdater.getOrCreate(sender).getMutable().setBalance(Wei.of(BigInteger.TWO.pow(20)));
    worldUpdater.getOrCreate(receiver).getMutable().setCode(codeBytes);

    int repeat = this.repeat;
    final EVM evm = MainnetEVMs.berlin(EvmConfiguration.DEFAULT);
    final Code code = evm.getCode(Hash.hash(codeBytes), codeBytes);
    final PrecompileContractRegistry precompileContractRegistry = new PrecompileContractRegistry();
    MainnetPrecompiledContracts.populateForIstanbul(
        precompileContractRegistry, evm.getGasCalculator());
    final Stopwatch stopwatch = Stopwatch.createUnstarted();
    long lastTime = 0;
    do {
      final boolean lastLoop = repeat == 0;

      final OperationTracer tracer = // You should have picked Mercy.
          lastLoop && showJsonResults
              ? new StandardJsonTracer(System.out, !noMemory)
              : OperationTracer.NO_TRACING;

      final Deque<MessageFrame> messageFrameStack = new ArrayDeque<>();
      messageFrameStack.add(
          MessageFrame.builder()
              .type(MessageFrame.Type.MESSAGE_CALL)
              .messageFrameStack(messageFrameStack)
              .worldUpdater(worldUpdater.updater())
              .initialGas(gas)
              .contract(Address.ZERO)
              .address(receiver)
              .originator(sender)
              .sender(sender)
              .gasPrice(gasPriceGWei)
              .inputData(callData)
              .value(ethValue)
              .apparentValue(ethValue)
              .code(code)
              .blockValues(new ToyBlockValues())
              .depth(0)
              .completer(c -> {})
              .miningBeneficiary(Address.ZERO)
              .blockHashLookup(h -> null)
              .build());

      final MessageCallProcessor mcp = new MessageCallProcessor(evm, precompileContractRegistry);
      final ContractCreationProcessor ccp =
          new ContractCreationProcessor(evm.getGasCalculator(), evm, false, List.of(), 0);
      stopwatch.start();
      while (!messageFrameStack.isEmpty()) {
        final MessageFrame messageFrame = messageFrameStack.peek();
        switch (messageFrame.getType()) {
          case CONTRACT_CREATION:
            ccp.process(messageFrame, tracer);
            break;
          case MESSAGE_CALL:
            mcp.process(messageFrame, tracer);
            break;
        }
        if (lastLoop) {
          if (messageFrame.getExceptionalHaltReason().isPresent()) {
            out.println(messageFrame.getExceptionalHaltReason().get());
          }
          if (messageFrame.getRevertReason().isPresent()) {
            out.println(
                new String(messageFrame.getRevertReason().get().toArray(), StandardCharsets.UTF_8));
          }
        }
        if (messageFrameStack.isEmpty()) {
          stopwatch.stop();
          if (lastTime == 0) {
            lastTime = stopwatch.elapsed().toNanos();
          }
        }

        if (lastLoop && messageFrameStack.isEmpty()) {
          final long evmGas = gas - messageFrame.getRemainingGas();
          out.println();
          out.printf(
              "{ \"gasUser\": \"0x%s\", \"timens\": %d, \"time\": %d }%n",
              Long.toHexString(evmGas), lastTime, lastTime / 1000);
        }
      }
      lastTime = stopwatch.elapsed().toNanos();
      stopwatch.reset();
    } while (repeat-- > 0);
  }
}
