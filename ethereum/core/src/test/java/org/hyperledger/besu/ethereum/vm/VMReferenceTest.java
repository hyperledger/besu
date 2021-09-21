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
import static org.junit.Assume.assumeTrue;

import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.worldstate.MutableWorldState;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.mainnet.MainnetProtocolSpecs;
import org.hyperledger.besu.ethereum.mainnet.MutableProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionValidator;
import org.hyperledger.besu.ethereum.referencetests.EnvironmentInformation;
import org.hyperledger.besu.ethereum.referencetests.ReferenceTestBlockchain;
import org.hyperledger.besu.ethereum.referencetests.VMReferenceTestCaseSpec;
import org.hyperledger.besu.ethereum.worldstate.DefaultMutableWorldState;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.testutil.JsonTestParameters;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Optional;
import java.util.OptionalInt;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** The VM operation testing framework entry point. */
@RunWith(Parameterized.class)
public class VMReferenceTest extends AbstractRetryingTest {

  /** The path where all of the VM test configuration files live. */
  private static final String[] TEST_CONFIG_FILE_DIR_PATHS = {
    "LegacyTests/Constantinople/VMTests/vmArithmeticTest",
    "LegacyTests/Constantinople/VMTests/vmBitwiseLogicOperation",
    "LegacyTests/Constantinople/VMTests/vmBlockInfoTest",
    "LegacyTests/Constantinople/VMTests/vmEnvironmentalInfo",
    "LegacyTests/Constantinople/VMTests/vmIOandFlowOperations",
    "LegacyTests/Constantinople/VMTests/vmLogTest",
    "LegacyTests/Constantinople/VMTests/vmPushDupSwapTest",
    "LegacyTests/Constantinople/VMTests/vmRandomTest",
    "LegacyTests/Constantinople/VMTests/vmSha3Test",
    "LegacyTests/Constantinople/VMTests/vmTests",
    "LegacyTests/Constantinople/VMTests/vmSystemOperations"
  };

  // The ignored test cases fall into two categories:
  //
  // 1. Incorrect Test Cases: The VMTests have known bugs with accessing
  // non-existent accounts. This corresponds to test cases involving
  // the BALANCE, EXTCODESIZE, EXTCODECOPY, and SELFDESTRUCT operations.
  //
  // 2. Test Cases for CALL, CALLCODE, and CALLCREATE: The VMTests do not
  // fully test these operations and the mocking does not add much value.
  // Additionally, the GeneralStateTests provide coverage of these
  // operations so the proper functionality does get tested somewhere.
  private static final String[] IGNORED_TESTS = {
    "push32AndSuicide", "suicide", "suicide0", "suicideNotExistingAccount", "suicideSendEtherToMe",
  };
  private static final Optional<BigInteger> CHAIN_ID = Optional.of(BigInteger.ONE);

  private final VMReferenceTestCaseSpec spec;

  @Parameters(name = "Name: {0}")
  public static Collection<Object[]> getTestParametersForConfig() {
    return JsonTestParameters.create(VMReferenceTestCaseSpec.class)
        .ignore(IGNORED_TESTS)
        .generate(TEST_CONFIG_FILE_DIR_PATHS);
  }

  public VMReferenceTest(
      final String name, final VMReferenceTestCaseSpec spec, final boolean runTest) {
    this.spec = spec;
    assumeTrue("Test " + name + " was ignored", runTest);
  }

  @Override
  protected void runTest() {
    final MutableWorldState worldState = new DefaultMutableWorldState(spec.getInitialWorldState());
    final EnvironmentInformation execEnv = spec.getExec();

    final ProtocolSpec protocolSpec =
        MainnetProtocolSpecs.frontierDefinition(OptionalInt.empty(), OptionalInt.empty(), false)
            .privacyParameters(PrivacyParameters.DEFAULT)
            .privateTransactionValidatorBuilder(() -> new PrivateTransactionValidator(CHAIN_ID))
            .badBlocksManager(new BadBlockManager())
            .build(new MutableProtocolSchedule(CHAIN_ID));

    final ReferenceTestBlockchain blockchain =
        new ReferenceTestBlockchain(execEnv.getBlockHeader().getNumber());
    final MessageFrame frame =
        MessageFrame.builder()
            .type(MessageFrame.Type.MESSAGE_CALL)
            .messageFrameStack(new ArrayDeque<>())
            .worldUpdater(worldState.updater())
            .initialGas(spec.getExec().getGas())
            .contract(execEnv.getAccountAddress())
            .address(execEnv.getAccountAddress())
            .originator(execEnv.getOriginAddress())
            .gasPrice(execEnv.getGasPrice())
            .inputData(execEnv.getData())
            .sender(execEnv.getCallerAddress())
            .value(execEnv.getValue())
            .apparentValue(execEnv.getValue())
            .code(execEnv.getCode())
            .blockHeader(execEnv.getBlockHeader())
            .depth(execEnv.getDepth())
            .completer(c -> {})
            .miningBeneficiary(execEnv.getBlockHeader().getCoinbase())
            .blockHashLookup(new BlockHashLookup(execEnv.getBlockHeader(), blockchain))
            .maxStackSize(MessageFrame.DEFAULT_MAX_STACK_SIZE)
            .build();

    // This is normally set inside the containing message executing the code.
    frame.setState(MessageFrame.State.CODE_EXECUTING);

    protocolSpec.getEvm().runToHalt(frame, OperationTracer.NO_TRACING);

    if (spec.isExceptionHaltExpected()) {
      assertThat(frame.getState() == MessageFrame.State.EXCEPTIONAL_HALT)
          .withFailMessage("VM should have exceptionally halted")
          .isTrue();
    } else {
      // This is normally performed when the message processor executing the VM
      // executes to completion successfully.
      frame.getWorldUpdater().commit();

      assertThat(frame.getState() == MessageFrame.State.EXCEPTIONAL_HALT)
          .withFailMessage(
              "VM should not have exceptionally halted with " + frame.getExceptionalHaltReason())
          .isFalse();
      assertThat(frame.getOutputData())
          .withFailMessage("VM output differs")
          .isEqualTo(spec.getOut());
      assertThat(worldState.rootHash())
          .withFailMessage("Final world state differs")
          .isEqualTo(spec.getFinalWorldState().rootHash());

      final Gas actualGas = frame.getRemainingGas();
      final Gas expectedGas = spec.getFinalGas();
      final Gas difference =
          (expectedGas.compareTo(actualGas) > 0)
              ? expectedGas.minus(actualGas)
              : actualGas.minus(expectedGas);
      assertThat(actualGas)
          .withFailMessage("Final gas does not match, with difference of %s", difference)
          .isEqualTo(expectedGas);
    }
  }
}
