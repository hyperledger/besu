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
import static org.junit.Assume.assumeTrue;
import static tech.pegasys.pantheon.ethereum.vm.MessageFrame.DEFAULT_MAX_STACK_SIZE;
import static tech.pegasys.pantheon.ethereum.vm.OperationTracer.NO_TRACING;

import tech.pegasys.pantheon.ethereum.core.Gas;
import tech.pegasys.pantheon.ethereum.core.MutableWorldState;
import tech.pegasys.pantheon.ethereum.core.PrivacyParameters;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetProtocolSpecs;
import tech.pegasys.pantheon.ethereum.mainnet.MutableProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSpec;
import tech.pegasys.pantheon.ethereum.privacy.PrivateTransactionValidator;
import tech.pegasys.pantheon.ethereum.vm.ehalt.ExceptionalHaltException;
import tech.pegasys.pantheon.ethereum.worldstate.DefaultMutableWorldState;
import tech.pegasys.pantheon.testutil.JsonTestParameters;

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
    "VMTests/vmArithmeticTest",
    "VMTests/vmBitwiseLogicOperation",
    "VMTests/vmBlockInfoTest",
    "VMTests/vmEnvironmentalInfo",
    "VMTests/vmIOandFlowOperations",
    "VMTests/vmLogTest",
    "VMTests/vmSha3Test",
    "VMTests/vmSystemOperations"
  };

  // The blacklisted test cases fall into two categories:
  //
  // 1. Incorrect Test Cases: The VMTests have known bugs with accessing
  // non-existent accounts. This corresponds to test cases involving
  // the BALANCE, EXTCODESIZE, EXTCODECOPY, and SELFDESTRUCT operations.
  //
  // 2. Test Cases for CALL, CALLCODE, and CALLCREATE: The VMTests do not
  // fully test these operations and the mocking does not add much value.
  // Additionally, the GeneralStateTests provide coverage of these
  // operations so the proper functionality does get tested somewhere.
  private static final String[] BLACKLISTED_TESTS = {
    "balance0",
    "balanceAddressInputTooBig",
    "balanceCaller3",
    "balanceAddressInputTooBigRightMyAddress",
    "ExtCodeSizeAddressInputTooBigRightMyAddress",
    "env1",
    "extcodecopy0AddressTooBigRight",
    "PostToNameRegistrator0",
    "CallToReturn1",
    "CallRecursiveBomb0",
    "createNameRegistratorValueTooHigh",
    "suicideNotExistingAccount",
    "callstatelessToReturn1",
    "CallRecursiveBomb1",
    "ABAcallsSuicide1",
    "suicideSendEtherToMe",
    "suicide0",
    "CallToNameRegistrator0",
    "callstatelessToNameRegistrator0",
    "PostToReturn1",
    "callcodeToReturn1",
    "ABAcalls0",
    "CallRecursiveBomb2",
    "CallRecursiveBomb3",
    "ABAcallsSuicide0",
    "callcodeToNameRegistrator0",
    "CallToPrecompiledContract",
    "createNameRegistrator"
  };
  private static final Optional<BigInteger> CHAIN_ID = Optional.of(BigInteger.ONE);
  private final String name;

  private final VMReferenceTestCaseSpec spec;

  @Parameters(name = "Name: {0}")
  public static Collection<Object[]> getTestParametersForConfig() throws Exception {
    return JsonTestParameters.create(VMReferenceTestCaseSpec.class)
        .blacklist(BLACKLISTED_TESTS)
        .generate(TEST_CONFIG_FILE_DIR_PATHS);
  }

  public VMReferenceTest(
      final String name, final VMReferenceTestCaseSpec spec, final boolean runTest) {
    this.name = name;
    this.spec = spec;
    assumeTrue("Test was blacklisted", runTest);
  }

  @Override
  protected void runTest() {
    final MutableWorldState worldState = new DefaultMutableWorldState(spec.getInitialWorldState());
    final EnvironmentInformation execEnv = spec.getExec();

    final ProtocolSpec<Void> protocolSpec =
        MainnetProtocolSpecs.frontierDefinition(OptionalInt.empty(), OptionalInt.empty())
            .privacyParameters(PrivacyParameters.DEFAULT)
            .privateTransactionValidatorBuilder(() -> new PrivateTransactionValidator(CHAIN_ID))
            .build(new MutableProtocolSchedule<>(CHAIN_ID));

    final TestBlockchain blockchain = new TestBlockchain(execEnv.getBlockHeader().getNumber());
    final MessageFrame frame =
        MessageFrame.builder()
            .type(MessageFrame.Type.MESSAGE_CALL)
            .messageFrameStack(new ArrayDeque<>())
            .blockchain(blockchain)
            .worldState(worldState.updater())
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
            .contractAccountVersion(execEnv.getVersion())
            .completer(c -> {})
            .miningBeneficiary(execEnv.getBlockHeader().getCoinbase())
            .blockHashLookup(new BlockHashLookup(execEnv.getBlockHeader(), blockchain))
            .maxStackSize(DEFAULT_MAX_STACK_SIZE)
            .build();

    // This is normally set inside the containing message executing the code.
    frame.setState(MessageFrame.State.CODE_EXECUTING);

    try {
      protocolSpec.getEvm().runToHalt(frame, NO_TRACING);
    } catch (final ExceptionalHaltException ehe) {
      if (!spec.isExceptionHaltExpected())
        System.err.println(
            String.format(
                "Test %s incurred in an exceptional halt exception for reasons: %s.",
                name, ehe.getReasons()));
    }

    if (spec.isExceptionHaltExpected()) {
      assertThat(frame.getState() == MessageFrame.State.EXCEPTIONAL_HALT)
          .withFailMessage("VM should have exceptionally halted")
          .isTrue();
    } else {
      // This is normally performed when the message processor executing the VM
      // executes to completion successfuly.
      frame.getWorldState().commit();

      assertThat(frame.getState() == MessageFrame.State.EXCEPTIONAL_HALT)
          .withFailMessage("VM should not have exceptionally halted")
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
