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

import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockImporter;
import tech.pegasys.pantheon.ethereum.core.MutableWorldState;
import tech.pegasys.pantheon.ethereum.mainnet.HeaderValidationMode;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSpec;
import tech.pegasys.pantheon.ethereum.rlp.RLPException;
import tech.pegasys.pantheon.testutil.JsonTestParameters;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class BlockchainReferenceTestTools {
  private static final ReferenceTestProtocolSchedules REFERENCE_TEST_PROTOCOL_SCHEDULES =
      ReferenceTestProtocolSchedules.create();

  private static final List<String> NETWORKS_TO_RUN;

  static {
    final String networks =
        System.getProperty(
            "test.ethereum.blockchain.eips",
            "FrontierToHomesteadAt5,HomesteadToEIP150At5,HomesteadToDaoAt5,EIP158ToByzantiumAt5,"
                + "Frontier,Homestead,EIP150,EIP158,Byzantium,Constantinople,ConstantinopleFix,Istanbul");
    NETWORKS_TO_RUN = Arrays.asList(networks.split(","));
  }

  private static final JsonTestParameters<?, ?> params =
      JsonTestParameters.create(BlockchainReferenceTestCaseSpec.class)
          .generator(
              (testName, spec, collector) -> {
                final String eip = spec.getNetwork();
                collector.add(testName + "[" + eip + "]", spec, NETWORKS_TO_RUN.contains(eip));
              });

  static {
    if (NETWORKS_TO_RUN.isEmpty()) {
      params.blacklistAll();
    }

    // Known bad test.
    params.blacklist(
        "RevertPrecompiledTouch(_storage)?_d(0|3)g0v0_(EIP158|Byzantium|Constantinople|ConstantinopleFix)");

    // Consumes a huge amount of memory
    params.blacklist(
        "static_Call1MB1024Calldepth_d1g0v0_(Byzantium|Constantinople|ConstantinopleFix)");
  }

  public static Collection<Object[]> generateTestParametersForConfig(final String[] filePath) {
    return params.generate(filePath);
  }

  public static void executeTest(final BlockchainReferenceTestCaseSpec spec) {
    final MutableWorldState worldState =
        spec.getWorldStateArchive().getMutable(spec.getGenesisBlockHeader().getStateRoot()).get();
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
        final HeaderValidationMode validationMode =
            "NoProof".equalsIgnoreCase(spec.getSealEngine())
                ? HeaderValidationMode.LIGHT
                : HeaderValidationMode.FULL;
        final boolean imported =
            blockImporter.importBlock(context, block, validationMode, validationMode);

        assertThat(imported).isEqualTo(candidateBlock.isValid());
      } catch (final RLPException e) {
        assertThat(candidateBlock.isValid()).isFalse();
      }
    }

    assertThat(blockchain.getChainHeadHash()).isEqualTo(spec.getLastBlockHash());
  }
}
