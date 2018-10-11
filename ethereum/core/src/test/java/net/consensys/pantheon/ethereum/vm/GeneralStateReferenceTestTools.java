package net.consensys.pantheon.ethereum.vm;

import static org.junit.Assert.assertEquals;

import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.core.Hash;
import net.consensys.pantheon.ethereum.core.LogSeries;
import net.consensys.pantheon.ethereum.core.MutableWorldState;
import net.consensys.pantheon.ethereum.core.Transaction;
import net.consensys.pantheon.ethereum.core.WorldState;
import net.consensys.pantheon.ethereum.core.WorldUpdater;
import net.consensys.pantheon.ethereum.mainnet.TransactionProcessor;
import net.consensys.pantheon.ethereum.rlp.RLP;
import net.consensys.pantheon.ethereum.worldstate.DebuggableMutableWorldState;
import net.consensys.pantheon.testutil.JsonTestParameters;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class GeneralStateReferenceTestTools {
  private static final ReferenceTestProtocolSchedules REFERENCE_TEST_PROTOCOL_SCHEDULES =
      ReferenceTestProtocolSchedules.create();

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
            "test.ethereum.state.eips", "Frontier,Homestead,EIP150,EIP158,Byzantium");
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
                  if (!EIPS_TO_RUN.contains(eip)) {
                    continue;
                  }
                  final List<GeneralStateTestCaseEipSpec> eipSpecs = entry.getValue();
                  if (eipSpecs.size() == 1) {
                    collector.add(prefix + eip, eipSpecs.get(0));
                  } else {
                    for (int i = 0; i < eipSpecs.size(); i++) {
                      collector.add(prefix + eip + '[' + i + ']', eipSpecs.get(i));
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
    params.blacklist("static_Call1MB1024Calldepth-Byzantium");
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

    if (!result.isInvalid()) {
      worldStateUpdater.commit();
    }

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
}
