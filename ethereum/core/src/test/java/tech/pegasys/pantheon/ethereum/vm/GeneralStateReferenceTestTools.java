/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.vm;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.ethereum.core.Account;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.LogSeries;
import tech.pegasys.pantheon.ethereum.core.MutableWorldState;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.core.WorldState;
import tech.pegasys.pantheon.ethereum.core.WorldUpdater;
import tech.pegasys.pantheon.ethereum.mainnet.TransactionProcessor;
import tech.pegasys.pantheon.ethereum.mainnet.TransactionValidationParams;
import tech.pegasys.pantheon.ethereum.rlp.RLP;
import tech.pegasys.pantheon.ethereum.worldstate.DefaultMutableWorldState;
import tech.pegasys.pantheon.testutil.JsonTestParameters;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class GeneralStateReferenceTestTools {
  private static final ReferenceTestProtocolSchedules REFERENCE_TEST_PROTOCOL_SCHEDULES =
      ReferenceTestProtocolSchedules.create();
  private static final List<String> SPECS_PRIOR_TO_DELETING_EMPTY_ACCOUNTS =
      Arrays.asList("Frontier", "Homestead", "EIP150");

  private static TransactionProcessor transactionProcessor(final String name) {
    return REFERENCE_TEST_PROTOCOL_SCHEDULES
        .getByName(name)
        .getByBlockNumber(0)
        .getTransactionProcessor();
  }

  private static final List<String> EIPS_TO_RUN;

  static {
    final String eips =
        System.getProperty(
            "test.ethereum.state.eips",
            "Frontier,Homestead,EIP150,EIP158,Byzantium,Constantinople,ConstantinopleFix,Istanbul");
    EIPS_TO_RUN = Arrays.asList(eips.split(","));
  }

  private static final JsonTestParameters<?, ?> params =
      JsonTestParameters.create(GeneralStateTestCaseSpec.class, GeneralStateTestCaseEipSpec.class)
          .generator(
              (testName, stateSpec, collector) -> {
                final String prefix = testName + "-";
                for (final Map.Entry<String, List<GeneralStateTestCaseEipSpec>> entry :
                    stateSpec.finalStateSpecs().entrySet()) {
                  final String eip = entry.getKey();
                  final boolean runTest = EIPS_TO_RUN.contains(eip);
                  final List<GeneralStateTestCaseEipSpec> eipSpecs = entry.getValue();
                  if (eipSpecs.size() == 1) {
                    collector.add(prefix + eip, eipSpecs.get(0), runTest);
                  } else {
                    for (int i = 0; i < eipSpecs.size(); i++) {
                      collector.add(prefix + eip + '[' + i + ']', eipSpecs.get(i), runTest);
                    }
                  }
                }
              });

  static {
    if (EIPS_TO_RUN.isEmpty()) {
      params.blacklistAll();
    }
    // Known incorrect test.
    params.blacklist(
        "RevertPrecompiledTouch(_storage)?-(EIP158|Byzantium|Constantinople|ConstantinopleFix)");
    // Gas integer value is too large to construct a valid transaction.
    params.blacklist("OverflowGasRequire");
    // Consumes a huge amount of memory
    params.blacklist("static_Call1MB1024Calldepth-(Byzantium|Constantinople|ConstantinopleFix)");
  }

  public static Collection<Object[]> generateTestParametersForConfig(final String[] filePath) {
    return params.generate(filePath);
  }

  public static void executeTest(final GeneralStateTestCaseEipSpec spec) {
    final BlockHeader blockHeader = spec.blockHeader();
    final WorldState initialWorldState = spec.initialWorldState();
    final Transaction transaction = spec.transaction();

    final MutableWorldState worldState = new DefaultMutableWorldState(initialWorldState);
    // Several of the GeneralStateTests check if the transaction could potentially
    // consume more gas than is left for the block it's attempted to be included in.
    // This check is performed within the `BlockImporter` rather than inside the
    // `TransactionProcessor`, so these tests are skipped.
    if (transaction.getGasLimit() > blockHeader.getGasLimit() - blockHeader.getGasUsed()) {
      return;
    }

    final TransactionProcessor processor = transactionProcessor(spec.eip());
    final WorldUpdater worldStateUpdater = worldState.updater();
    final TestBlockchain blockchain = new TestBlockchain(blockHeader.getNumber());
    final TransactionProcessor.Result result =
        processor.processTransaction(
            blockchain,
            worldStateUpdater,
            blockHeader,
            transaction,
            blockHeader.getCoinbase(),
            new BlockHashLookup(blockHeader, blockchain),
            false,
            TransactionValidationParams.processingBlock());
    final Account coinbase = worldStateUpdater.getOrCreate(spec.blockHeader().getCoinbase());
    if (coinbase != null && coinbase.isEmpty() && shouldClearEmptyAccounts(spec.eip())) {
      worldStateUpdater.deleteAccount(coinbase.getAddress());
    }
    worldStateUpdater.commit();

    // Check the world state root hash.
    final Hash expectedRootHash = spec.expectedRootHash();
    assertThat(worldState.rootHash())
        .withFailMessage("Unexpected world state root hash; computed state: %s", worldState)
        .isEqualByComparingTo(expectedRootHash);

    // Check the logs.
    final Hash expectedLogsHash = spec.expectedLogsHash();
    final LogSeries logs = result.getLogs();
    assertThat(Hash.hash(RLP.encode(logs::writeTo)))
        .withFailMessage("Unmatched logs hash. Generated logs: %s", logs)
        .isEqualByComparingTo(expectedLogsHash);
  }

  private static boolean shouldClearEmptyAccounts(final String eip) {
    return !SPECS_PRIOR_TO_DELETING_EMPTY_ACCOUNTS.contains(eip);
  }
}
