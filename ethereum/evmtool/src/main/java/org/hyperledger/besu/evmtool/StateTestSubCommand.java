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

import static org.hyperledger.besu.ethereum.referencetests.ReferenceTestProtocolSchedules.shouldClearEmptyAccounts;
import static org.hyperledger.besu.evmtool.StateTestSubCommand.COMMAND_NAME;

import org.hyperledger.besu.ethereum.core.Account;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Log;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.WorldState;
import org.hyperledger.besu.ethereum.core.WorldUpdater;
import org.hyperledger.besu.ethereum.mainnet.TransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.referencetests.GeneralStateTestCaseEipSpec;
import org.hyperledger.besu.ethereum.referencetests.GeneralStateTestCaseSpec;
import org.hyperledger.besu.ethereum.referencetests.ReferenceTestBlockchain;
import org.hyperledger.besu.ethereum.referencetests.ReferenceTestProtocolSchedules;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.vm.BlockHashLookup;
import org.hyperledger.besu.ethereum.vm.OperationTracer;
import org.hyperledger.besu.ethereum.worldstate.DefaultMutableWorldState;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Stopwatch;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.tuweni.units.bigints.UInt256;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

@Command(
    name = COMMAND_NAME,
    description = "Execute an Ethereum State Test.",
    mixinStandardHelpOptions = true)
public class StateTestSubCommand implements Runnable {
  private static final Logger LOG = LogManager.getLogger();

  public static final String COMMAND_NAME = "state-test";

  @ParentCommand private EvmToolCommand parentCommand;

  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection") // picocli does it magically
  @Parameters
  private final List<File> stateTestFiles = new ArrayList<>();

  private final ObjectMapper objectMapper = new ObjectMapper();

  private static final ReferenceTestProtocolSchedules REFERENCE_TEST_PROTOCOL_SCHEDULES;

  static {
    Configurator.setLevel(
        "org.hyperledger.besu.ethereum.mainnet.ProtocolScheduleBuilder", Level.OFF);
    REFERENCE_TEST_PROTOCOL_SCHEDULES = ReferenceTestProtocolSchedules.create();
    Configurator.setLevel("org.hyperledger.besu.ethereum.mainnet.ProtocolScheduleBuilder", null);
  }

  @Override
  public void run() {
    final ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.disable(Feature.AUTO_CLOSE_SOURCE);
    final JavaType javaType =
        objectMapper
            .getTypeFactory()
            .constructParametricType(Map.class, String.class, GeneralStateTestCaseSpec.class);
    try {
      if (stateTestFiles.isEmpty()) {
        // if no state tests were specified use standard input
        while (true) {
          final Map<String, GeneralStateTestCaseSpec> generalStateTests =
              objectMapper.readValue(System.in, javaType);
          if (generalStateTests == null || generalStateTests.isEmpty()) {
            break;
          }
          executeStateTest(generalStateTests);
        }
      } else {
        for (final File stateTestFile : stateTestFiles) {
          final Map<String, GeneralStateTestCaseSpec> generalStateTests =
              objectMapper.readValue(stateTestFile, javaType);
          executeStateTest(generalStateTests);
        }
      }
    } catch (final IOException e) {
      LOG.fatal(e);
    }
  }

  private void executeStateTest(final Map<String, GeneralStateTestCaseSpec> generalStateTests) {
    for (final var generalStateTestEntry : generalStateTests.entrySet()) {
      generalStateTestEntry
          .getValue()
          .finalStateSpecs()
          .forEach((fork, specs) -> traceTestSpecs(generalStateTestEntry.getKey(), specs));
    }
  }

  private void traceTestSpecs(final String test, final List<GeneralStateTestCaseEipSpec> specs) {
    final OperationTracer tracer = // You should have picked Mercy.
        parentCommand.showJsonResults
            ? new EVMToolTracer(System.out, !parentCommand.noMemory)
            : OperationTracer.NO_TRACING;

    for (final GeneralStateTestCaseEipSpec spec : specs) {

      final BlockHeader blockHeader = spec.getBlockHeader();
      final WorldState initialWorldState = spec.getInitialWorldState();
      final Transaction transaction = spec.getTransaction();

      final MutableWorldState worldState = new DefaultMutableWorldState(initialWorldState);
      // Several of the GeneralStateTests check if the transaction could potentially
      // consume more gas than is left for the block it's attempted to be included in.
      // This check is performed within the `BlockImporter` rather than inside the
      // `TransactionProcessor`, so these tests are skipped.
      if (transaction.getGasLimit() > blockHeader.getGasLimit() - blockHeader.getGasUsed()) {
        return;
      }

      final TransactionProcessor processor = transactionProcessor(spec.getFork());
      final WorldUpdater worldStateUpdater = worldState.updater();
      final ReferenceTestBlockchain blockchain =
          new ReferenceTestBlockchain(blockHeader.getNumber());
      final Stopwatch timer = Stopwatch.createStarted();
      final TransactionProcessor.Result result =
          processor.processTransaction(
              blockchain,
              worldStateUpdater,
              blockHeader,
              transaction,
              blockHeader.getCoinbase(),
              new BlockHashLookup(blockHeader, blockchain),
              false,
              TransactionValidationParams.processingBlock(),
              tracer);
      timer.stop();
      final Account coinbase = worldStateUpdater.getOrCreate(spec.getBlockHeader().getCoinbase());
      if (coinbase != null && coinbase.isEmpty() && shouldClearEmptyAccounts(spec.getFork())) {
        worldStateUpdater.deleteAccount(coinbase.getAddress());
      }
      worldStateUpdater.commit();

      final ObjectNode summaryLine = objectMapper.createObjectNode();
      summaryLine.put("output", result.getOutput().toUnprefixedHexString());
      summaryLine.put(
          "gasUsed",
          EVMToolTracer.shortNumber(
              UInt256.valueOf(transaction.getGasLimit() - result.getGasRemaining())));
      summaryLine.put("time", timer.elapsed(TimeUnit.NANOSECONDS));

      // Check the world state root hash.
      summaryLine.put("test", test);
      summaryLine.put("fork", spec.getFork());
      summaryLine.put("d", spec.getDataIndex());
      summaryLine.put("g", spec.getGasIndex());
      summaryLine.put("v", spec.getValueIndex());
      summaryLine.put("postHash", worldState.rootHash().toHexString());
      final List<Log> logs = result.getLogs();
      final Hash actualLogsHash = Hash.hash(RLP.encode(out -> out.writeList(logs, Log::writeTo)));
      summaryLine.put("postLogsHash", actualLogsHash.toHexString());
      summaryLine.put(
          "pass",
          worldState.rootHash().equals(spec.getExpectedRootHash())
              && actualLogsHash.equals(spec.getExpectedLogsHash()));

      System.out.println(summaryLine);
    }
  }

  private static TransactionProcessor transactionProcessor(final String name) {
    return REFERENCE_TEST_PROTOCOL_SCHEDULES
        .getByName(name)
        .getByBlockNumber(0)
        .getTransactionProcessor();
  }
}
