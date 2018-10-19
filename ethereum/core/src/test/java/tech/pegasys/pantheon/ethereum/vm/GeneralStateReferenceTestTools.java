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

import static org.junit.Assert.assertEquals;

import tech.pegasys.pantheon.ethereum.core.Account;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.LogSeries;
import tech.pegasys.pantheon.ethereum.core.MutableWorldState;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.core.WorldState;
import tech.pegasys.pantheon.ethereum.core.WorldUpdater;
import tech.pegasys.pantheon.ethereum.mainnet.TransactionProcessor;
import tech.pegasys.pantheon.ethereum.rlp.RLP;
import tech.pegasys.pantheon.ethereum.worldstate.DebuggableMutableWorldState;
import tech.pegasys.pantheon.testutil.JsonTestParameters;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class GeneralStateReferenceTestTools {
  private static final ReferenceTestProtocolSchedules REFERENCE_TEST_PROTOCOL_SCHEDULES =
      ReferenceTestProtocolSchedules.create();
  private static final List<String> SPECS_PRIOR_TO_DELETING_EMPTY_ACCOUNTS =
      Arrays.asList("Frontier", "Homestead", "EIP-150");

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
            "Frontier,Homestead,EIP150,EIP158,Byzantium,Constantinople");
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
    params.blacklist("RevertPrecompiledTouch-(EIP158|Byzantium)");
    // Gas integer value is too large to construct a valid transaction.
    params.blacklist("OverflowGasRequire");
    // Consumes a huge amount of memory
    params.blacklist("static_Call1MB1024Calldepth-(Byzantium|Constantinople)");

    // Needs investigation (tests pass in other clients)
    params.blacklist("NotEnoughCashContractCreation-Frontier");
    params.blacklist("NotEnoughCashContractCreation-Homestead");
    params.blacklist("NotEnoughCashContractCreation-EIP150");
    params.blacklist("OutOfGasContractCreation-EIP150\\[0\\]");
    params.blacklist("OutOfGasContractCreation-EIP150\\[2\\]");
    params.blacklist("OutOfGasContractCreation-Homestead\\[0\\]");
    params.blacklist("OutOfGasContractCreation-Homestead\\[2\\]");
    params.blacklist("OutOfGasPrefundedContractCreation-EIP150");
    params.blacklist("OutOfGasPrefundedContractCreation-Homestead");
    params.blacklist("201503110226PYTHON_DUP6-EIP150");
    params.blacklist("201503110226PYTHON_DUP6-Frontier");
    params.blacklist("201503110226PYTHON_DUP6-Homestead");
    params.blacklist("RevertOpcodeWithBigOutputInInit-EIP150\\[2\\]");
    params.blacklist("RevertOpcodeWithBigOutputInInit-EIP150\\[3\\]");
    params.blacklist("RevertOpcodeWithBigOutputInInit-Homestead\\[2\\]");
    params.blacklist("RevertOpcodeWithBigOutputInInit-Homestead\\[3\\]");
    params.blacklist("RevertOpcodeInInit-EIP150\\[2\\]");
    params.blacklist("RevertOpcodeInInit-EIP150\\[3\\]");
    params.blacklist("RevertOpcodeInInit-Homestead\\[2\\]");
    params.blacklist("RevertOpcodeInInit-Homestead\\[3\\]");
    params.blacklist("suicideCoinbase-Frontier");
    params.blacklist("suicideCoinbase-Homestead");
    params.blacklist("TransactionNonceCheck-EIP150");
    params.blacklist("TransactionNonceCheck-Frontier");
    params.blacklist("TransactionNonceCheck-Homestead");
    params.blacklist("EmptyTransaction-EIP150");
    params.blacklist("EmptyTransaction-Frontier");
    params.blacklist("EmptyTransaction-Homestead");
    params.blacklist("RefundOverflow-EIP150");
    params.blacklist("RefundOverflow-Frontier");
    params.blacklist("RefundOverflow-Homestead");
    params.blacklist("TransactionToItselfNotEnoughFounds-EIP150");
    params.blacklist("TransactionToItselfNotEnoughFounds-Frontier");
    params.blacklist("TransactionToItselfNotEnoughFounds-Homestead");
    params.blacklist("TransactionNonceCheck2-EIP150");
    params.blacklist("TransactionNonceCheck2-Frontier");
    params.blacklist("TransactionNonceCheck2-Homestead");
    params.blacklist("CreateTransactionReverted-EIP150");
    params.blacklist("CreateTransactionReverted-Frontier");
    params.blacklist("CreateTransactionReverted-Homestead");
    params.blacklist("RefundOverflow2-EIP150");
    params.blacklist("RefundOverflow2-Frontier");
    params.blacklist("RefundOverflow2-Homestead");
    params.blacklist("SuicidesMixingCoinbase-Frontier\\[0\\]");
    params.blacklist("SuicidesMixingCoinbase-Frontier\\[1\\]");
    params.blacklist("SuicidesMixingCoinbase-Homestead\\[0\\]");
    params.blacklist("SuicidesMixingCoinbase-Homestead\\[1\\]");
    params.blacklist("createNameRegistratorPerTxsNotEnoughGasBefore-EIP150");
    params.blacklist("createNameRegistratorPerTxsNotEnoughGasBefore-Homestead");
    params.blacklist("createNameRegistratorPerTxsNotEnoughGasAfter-EIP150");
    params.blacklist("createNameRegistratorPerTxsNotEnoughGasAfter-Homestead");
    params.blacklist("createNameRegistratorPerTxsNotEnoughGasAt-EIP150");
    params.blacklist("createNameRegistratorPerTxsNotEnoughGasAt-Homestead");
    params.blacklist("UserTransactionGasLimitIsTooLowWhenZeroCost-EIP150");
    params.blacklist("UserTransactionGasLimitIsTooLowWhenZeroCost-Frontier");
    params.blacklist("UserTransactionGasLimitIsTooLowWhenZeroCost-Homestead");

    // Constantinople failures to investigate
    params.blacklist("RevertInCreateInInitCreate2-Constantinople");
    params.blacklist("RevertInCreateInInit-Constantinople");
  }

  public static Collection<Object[]> generateTestParametersForConfig(final String[] filePath) {
    return params.generate(filePath);
  }

  public static void executeTest(final GeneralStateTestCaseEipSpec spec) {
    final BlockHeader blockHeader = spec.blockHeader();
    final WorldState initialWorldState = spec.initialWorldState();
    final Transaction transaction = spec.transaction();

    final MutableWorldState worldState = new DebuggableMutableWorldState(initialWorldState);
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
            new BlockHashLookup(blockHeader, blockchain));

    if (result.isInvalid()) {
      final Account coinbase = worldStateUpdater.getOrCreate(spec.blockHeader().getCoinbase());
      if (coinbase != null && coinbase.isEmpty() && shouldClearEmptyAccounts(spec.eip())) {
        worldStateUpdater.deleteAccount(coinbase.getAddress());
      }
    }
    worldStateUpdater.commit();

    // Check the world state root hash.
    final Hash expectedRootHash = spec.expectedRootHash();
    assertEquals(
        "Unexpected world state root hash; computed state: " + worldState,
        expectedRootHash,
        worldState.rootHash());

    // Check the logs.
    final Hash expectedLogsHash = spec.expectedLogsHash();
    final LogSeries logs = result.getLogs();
    assertEquals(
        "Unmatched logs hash. Generated logs: " + logs,
        expectedLogsHash,
        Hash.hash(RLP.encode(logs::writeTo)));
  }

  private static boolean shouldClearEmptyAccounts(final String eip) {
    return !SPECS_PRIOR_TO_DELETING_EMPTY_ACCOUNTS.contains(eip);
  }
}
