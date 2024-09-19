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
 */
package org.hyperledger.besu.ethereum.vm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockImporter;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.mainnet.BlockImportResult;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.referencetests.BlockchainReferenceTestCase;
import org.hyperledger.besu.ethereum.referencetests.ReferenceTestProtocolSchedules;
import org.hyperledger.besu.ethereum.rlp.RLPException;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.EvmSpecVersion;
import org.hyperledger.besu.evm.account.AccountState;
import org.hyperledger.besu.evm.internal.EvmConfiguration.WorldUpdaterMode;
import org.hyperledger.besu.testutil.JsonTestParameters;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.tuweni.bytes.Bytes32;
import org.assertj.core.api.Assertions;

public class BlockchainReferenceTestTools {
  private static final ReferenceTestProtocolSchedules REFERENCE_TEST_PROTOCOL_SCHEDULES =
      ReferenceTestProtocolSchedules.create();

  private static final List<String> NETWORKS_TO_RUN;

  static {
    final String networks =
        System.getProperty(
            "test.ethereum.blockchain.eips",
            "FrontierToHomesteadAt5,HomesteadToEIP150At5,HomesteadToDaoAt5,EIP158ToByzantiumAt5,CancunToPragueAtTime15k"
                + "Frontier,Homestead,EIP150,EIP158,Byzantium,Constantinople,ConstantinopleFix,Istanbul,Berlin,"
                + "London,Merge,Paris,Shanghai,Cancun,Prague,Osaka,Amsterdam,Bogota,Polis,Bangkok,Verkle");
    NETWORKS_TO_RUN = Arrays.asList(networks.split(","));
  }

  private BlockchainReferenceTestTools() {
    // utility class
  }

  public static Collection<Object[]> generateTestParametersForConfig(final String[] filePath, final Class<? extends BlockchainReferenceTestCase> mappedType) {
    var params = JsonTestParameters.create(mappedType)
      .generator(
        (testName, fullPath, spec, collector) -> {
          final String eip = spec.getNetwork();
          collector.add(
            testName + "[" + eip + "]", fullPath, spec, NETWORKS_TO_RUN.contains(eip));
        })
      // Consumes a huge amount of memory
      .ignore("static_Call1MB1024Calldepth_d1g0v0_\\w+")
      .ignore("ShanghaiLove_")

      // Absurd amount of gas, doesn't run in parallel
      .ignore("randomStatetest94_\\w+")

      // Don't do time-consuming tests
      .ignore("CALLBlake2f_MaxRounds")
      .ignore("loopMul_")

      // Inconclusive fork choice rule, since in merge CL should be choosing forks and setting the
      // chain head.
      // Perfectly valid test pre-merge.
      .ignore(
        "UncleFromSideChain_(Merge|Paris|Shanghai|Cancun|Prague|Osaka|Amsterdam|Bogota|Polis|Bangkok)")

      // EOF tests don't have Prague stuff like deopsits right now
      .ignore("/stEOF/")

      // None of the Prague tests have withdrawls and deposits handling
      .ignore("\\[Prague\\]");

    if (NETWORKS_TO_RUN.isEmpty()) {
      params.ignoreAll();
    }

    return params.generate(filePath);
  }

  @SuppressWarnings("java:S5960") // this is actually test code
  public static void executeTest(final BlockchainReferenceTestCase testCase) {
    final BlockHeader genesisBlockHeader = testCase.getGenesisBlockHeader();
    final MutableWorldState worldState =
        testCase.getWorldStateArchive()
            .getMutable(genesisBlockHeader.getStateRoot(), genesisBlockHeader.getHash())
            .orElseThrow();

    final ProtocolSchedule schedule =
        REFERENCE_TEST_PROTOCOL_SCHEDULES.getByName(testCase.getNetwork());

    final MutableBlockchain blockchain = testCase.getBlockchain();
    final ProtocolContext context = testCase.getProtocolContext();

    for (final Block candidateBlock :
        testCase.getBlocks()) {
      if (!testCase.isExecutable(candidateBlock)) {
        return;
      }

      try {
        final ProtocolSpec protocolSpec = schedule.getByBlockHeader(candidateBlock.getHeader());
        final BlockImporter blockImporter = protocolSpec.getBlockImporter();

        verifyJournaledEVMAccountCompatability(worldState, protocolSpec);

        final HeaderValidationMode validationMode =
            "NoProof".equalsIgnoreCase(testCase.getSealEngine())
                ? HeaderValidationMode.LIGHT
                : HeaderValidationMode.FULL;
        final BlockImportResult importResult =
            blockImporter.importBlock(context, candidateBlock, validationMode, validationMode);

        assertThat(importResult.isImported()).isEqualTo(testCase.isValid(candidateBlock));
      } catch (final RLPException e) {
        assertThat(testCase.isValid(candidateBlock)).isFalse();
      }
    }

    Assertions.assertThat(blockchain.getChainHeadHash()).isEqualTo(testCase.getLastBlockHash());
  }

  static void verifyJournaledEVMAccountCompatability(
      final MutableWorldState worldState, final ProtocolSpec protocolSpec) {
    EVM evm = protocolSpec.getEvm();
    if (evm.getEvmConfiguration().worldUpdaterMode() == WorldUpdaterMode.JOURNALED) {
      assumeThat(
              worldState
                  .streamAccounts(Bytes32.ZERO, Integer.MAX_VALUE)
                  .anyMatch(AccountState::isEmpty))
          .withFailMessage("Journaled account configured and empty account detected")
          .isFalse();
      assumeThat(EvmSpecVersion.SPURIOUS_DRAGON.compareTo(evm.getEvmVersion()) > 0)
          .withFailMessage("Journaled account configured and fork prior to the merge specified")
          .isFalse();
    }
  }
}
