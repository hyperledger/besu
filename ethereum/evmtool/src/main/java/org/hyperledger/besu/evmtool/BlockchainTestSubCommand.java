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
package org.hyperledger.besu.evmtool;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hyperledger.besu.evmtool.BlockchainTestSubCommand.COMMAND_NAME;

import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockImporter;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.mainnet.BlockImportResult;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.referencetests.BlockchainReferenceTestCaseSpec;
import org.hyperledger.besu.ethereum.referencetests.ReferenceTestProtocolSchedules;
import org.hyperledger.besu.ethereum.rlp.RLPException;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.EvmSpecVersion;
import org.hyperledger.besu.evm.account.AccountState;
import org.hyperledger.besu.evm.internal.EvmConfiguration.WorldUpdaterMode;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.tuweni.bytes.Bytes32;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * This class, BlockchainTestSubCommand, is a command-line interface (CLI) command that executes an
 * Ethereum State Test. It implements the Runnable interface, meaning it can be used in a thread of
 * execution.
 *
 * <p>The class is annotated with @CommandLine.Command, which is a PicoCLI annotation that
 * designates this class as a command-line command. The annotation parameters define the command's
 * name, description, whether it includes standard help options, and the version provider.
 *
 * <p>The command's functionality is defined in the run() method, which is overridden from the
 * Runnable interface.
 */
@Command(
    name = COMMAND_NAME,
    description = "Execute an Ethereum Blockchain Test.",
    mixinStandardHelpOptions = true,
    versionProvider = VersionProvider.class)
public class BlockchainTestSubCommand implements Runnable {
  /**
   * The name of the command for the BlockchainTestSubCommand. This constant is used as the name
   * parameter in the @CommandLine.Command annotation. It defines the command name that users should
   * enter on the command line to invoke this command.
   */
  public static final String COMMAND_NAME = "block-test";

  @Option(
      names = {"--test-name"},
      description = "Limit execution to one named test.")
  private String testName = null;

  @ParentCommand private final EvmToolCommand parentCommand;

  // picocli does it magically
  @Parameters private final List<Path> blockchainTestFiles = new ArrayList<>();

  /**
   * Default constructor for the BlockchainTestSubCommand class. This constructor doesn't take any
   * arguments and initializes the parentCommand to null. PicoCLI requires this constructor.
   */
  @SuppressWarnings("unused")
  public BlockchainTestSubCommand() {
    // PicoCLI requires this
    this(null);
  }

  BlockchainTestSubCommand(final EvmToolCommand parentCommand) {
    this.parentCommand = parentCommand;
  }

  @Override
  public void run() {
    // presume ethereum mainnet for reference and state tests
    SignatureAlgorithmFactory.setDefaultInstance();
    final ObjectMapper blockchainTestMapper = JsonUtils.createObjectMapper();

    final JavaType javaType =
        blockchainTestMapper
            .getTypeFactory()
            .constructParametricType(
                Map.class, String.class, BlockchainReferenceTestCaseSpec.class);
    try {
      if (blockchainTestFiles.isEmpty()) {
        // if no state tests were specified, use standard input to get filenames
        final BufferedReader in =
            new BufferedReader(new InputStreamReader(parentCommand.in, UTF_8));
        while (true) {
          final String fileName = in.readLine();
          if (fileName == null) {
            // Reached end-of-file. Stop the loop.
            break;
          }
          final File file = new File(fileName);
          if (file.isFile()) {
            final Map<String, BlockchainReferenceTestCaseSpec> blockchainTests =
                blockchainTestMapper.readValue(file, javaType);
            executeBlockchainTest(blockchainTests);
          } else {
            parentCommand.out.println("File not found: " + fileName);
          }
        }
      } else {
        for (final Path blockchainTestFile : blockchainTestFiles) {
          final Map<String, BlockchainReferenceTestCaseSpec> blockchainTests;
          if ("stdin".equals(blockchainTestFile.toString())) {
            blockchainTests = blockchainTestMapper.readValue(parentCommand.in, javaType);
          } else {
            blockchainTests = blockchainTestMapper.readValue(blockchainTestFile.toFile(), javaType);
          }
          executeBlockchainTest(blockchainTests);
        }
      }
    } catch (final JsonProcessingException jpe) {
      parentCommand.out.println("File content error: " + jpe);
    } catch (final IOException e) {
      System.err.println("Unable to read state file");
      e.printStackTrace(System.err);
    }
  }

  private void executeBlockchainTest(
      final Map<String, BlockchainReferenceTestCaseSpec> blockchainTests) {
    blockchainTests.forEach(this::traceTestSpecs);
  }

  private void traceTestSpecs(final String test, final BlockchainReferenceTestCaseSpec spec) {
    if (testName != null && !testName.equals(test)) {
      parentCommand.out.println("Skipping test: " + test);
      return;
    }
    parentCommand.out.println("Considering " + test);

    final BlockHeader genesisBlockHeader = spec.getGenesisBlockHeader();
    final MutableWorldState worldState =
        spec.getWorldStateArchive()
            .getMutable(genesisBlockHeader.getStateRoot(), genesisBlockHeader.getHash())
            .orElseThrow();

    final ProtocolSchedule schedule =
        ReferenceTestProtocolSchedules.getInstance().getByName(spec.getNetwork());

    final MutableBlockchain blockchain = spec.getBlockchain();
    final ProtocolContext context = spec.getProtocolContext();

    for (final BlockchainReferenceTestCaseSpec.CandidateBlock candidateBlock :
        spec.getCandidateBlocks()) {
      if (!candidateBlock.isExecutable()) {
        return;
      }

      try {
        final Block block = candidateBlock.getBlock();

        final ProtocolSpec protocolSpec = schedule.getByBlockHeader(block.getHeader());
        final BlockImporter blockImporter = protocolSpec.getBlockImporter();

        verifyJournaledEVMAccountCompatability(worldState, protocolSpec);

        final HeaderValidationMode validationMode =
            "NoProof".equalsIgnoreCase(spec.getSealEngine())
                ? HeaderValidationMode.LIGHT
                : HeaderValidationMode.FULL;
        final BlockImportResult importResult =
            blockImporter.importBlock(context, block, validationMode, validationMode);

        if (importResult.isImported() != candidateBlock.isValid()) {
          parentCommand.out.printf(
              "Block %d (%s) %s%n",
              block.getHeader().getNumber(),
              block.getHash(),
              importResult.isImported() ? "Failed to be rejected" : "Failed to import");
        } else {
          parentCommand.out.printf(
              "Block %d (%s) %s%n",
              block.getHeader().getNumber(),
              block.getHash(),
              importResult.isImported() ? "Imported" : "Rejected (correctly)");
        }
      } catch (final RLPException e) {
        if (candidateBlock.isValid()) {
          parentCommand.out.printf(
              "Block %d (%s) should have imported but had an RLP exception %s%n",
              candidateBlock.getBlock().getHeader().getNumber(),
              candidateBlock.getBlock().getHash(),
              e.getMessage());
        }
      }
    }
    if (!blockchain.getChainHeadHash().equals(spec.getLastBlockHash())) {
      parentCommand.out.printf(
          "Chain header mismatch, have %s want %s - %s%n",
          blockchain.getChainHeadHash(), spec.getLastBlockHash(), test);
    } else {
      parentCommand.out.println("Chain import successful - " + test);
    }
  }

  void verifyJournaledEVMAccountCompatability(
      final MutableWorldState worldState, final ProtocolSpec protocolSpec) {
    EVM evm = protocolSpec.getEvm();
    if (evm.getEvmConfiguration().worldUpdaterMode() == WorldUpdaterMode.JOURNALED) {
      if (worldState
          .streamAccounts(Bytes32.ZERO, Integer.MAX_VALUE)
          .anyMatch(AccountState::isEmpty)) {
        parentCommand.out.println("Journaled account configured and empty account detected");
      }

      if (EvmSpecVersion.SPURIOUS_DRAGON.compareTo(evm.getEvmVersion()) > 0) {
        parentCommand.out.println(
            "Journaled account configured and fork prior to the merge specified");
      }
    }
  }
}
