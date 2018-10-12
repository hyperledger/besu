package net.consensys.pantheon.ethereum.vm;

import static org.assertj.core.api.Assertions.assertThat;

import net.consensys.pantheon.ethereum.ProtocolContext;
import net.consensys.pantheon.ethereum.chain.MutableBlockchain;
import net.consensys.pantheon.ethereum.core.Block;
import net.consensys.pantheon.ethereum.core.BlockHeader;
import net.consensys.pantheon.ethereum.core.BlockImporter;
import net.consensys.pantheon.ethereum.core.MutableWorldState;
import net.consensys.pantheon.ethereum.mainnet.HeaderValidationMode;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSchedule;
import net.consensys.pantheon.ethereum.mainnet.ProtocolSpec;
import net.consensys.pantheon.ethereum.rlp.RLPException;
import net.consensys.pantheon.testutil.JsonTestParameters;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.junit.Assert;

public class BlockchainReferenceTestTools {
  private static final ReferenceTestProtocolSchedules REFERENCE_TEST_PROTOCOL_SCHEDULES =
      ReferenceTestProtocolSchedules.create();

  private static final List<String> NETWORKS_TO_RUN;

  static {
    final String networks =
        System.getProperty(
            "test.ethereum.blockchain.eips",
            "FrontierToHomesteadAt5,HomesteadToEIP150At5,HomesteadToDaoAt5,EIP158ToByzantiumAt5,"
                + "Frontier,Homestead,EIP150,EIP158,Byzantium");
    NETWORKS_TO_RUN = Arrays.asList(networks.split(","));
  }

  private static final JsonTestParameters<?, ?> params =
      JsonTestParameters.create(BlockchainReferenceTestCaseSpec.class)
          .generator(
              (testName, spec, collector) -> {
                final String eip = spec.getNetwork();
                if (NETWORKS_TO_RUN.contains(eip)) {
                  collector.add(testName + "[" + eip + "]", spec);
                }
              });

  static {
    if (NETWORKS_TO_RUN.isEmpty()) {
      params.blacklistAll();
    }

    // TODO: Determine and implement cross-chain validation prevention.
    params.blacklist("ChainAtoChainB_BlockHash_(Frontier|Homestead|EIP150|EIP158|Byzantium)");
    // Known bad test.
    params.blacklist("RevertPrecompiledTouch_d0g0v0_(EIP158|Byzantium)");
    // Consumes a huge amount of memory
    params.blacklist("static_Call1MB1024Calldepth_d1g0v0_Byzantium");
  }

  public static Collection<Object[]> generateTestParametersForConfig(final String[] filePath) {
    return params.generate(filePath);
  }

  public static void executeTest(final BlockchainReferenceTestCaseSpec spec) {
    final MutableWorldState worldState =
        spec.getWorldStateArchive().getMutable(spec.getGenesisBlockHeader().getStateRoot());
    final BlockHeader genesisBlockHeader = spec.getGenesisBlockHeader();
    assertThat(worldState.rootHash()).isEqualTo(genesisBlockHeader.getStateRoot());

    final ProtocolSchedule<Void> schedule =
        REFERENCE_TEST_PROTOCOL_SCHEDULES.getByName(spec.getNetwork());

    final MutableBlockchain blockchain = spec.getBlockchain();
    final ProtocolContext<Void> context = spec.getProtocolContext();

    for (final BlockchainReferenceTestCaseSpec.CandidateBlock candidateBlock :
        spec.getCandidateBlocks()) {
      if (!candidateBlock.isExecutable()) {
        return;
      }

      try {
        final Block block = candidateBlock.getBlock();

        final ProtocolSpec<Void> protocolSpec =
            schedule.getByBlockNumber(block.getHeader().getNumber());
        final BlockImporter<Void> blockImporter = protocolSpec.getBlockImporter();
        final boolean imported =
            blockImporter.importBlock(context, block, HeaderValidationMode.FULL);

        assertThat(imported).isEqualTo(candidateBlock.isValid());
      } catch (final RLPException e) {
        Assert.assertFalse(candidateBlock.isValid());
      }
    }

    assertThat(blockchain.getChainHeadHash()).isEqualTo(spec.getLastBlockHash());
  }
}
