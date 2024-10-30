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
package org.hyperledger.besu.evm.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.evm.EOFTestConstants.EOF_CREATE_CONTRACT;
import static org.hyperledger.besu.evm.EOFTestConstants.INNER_CONTRACT;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.COMPLETED_SUCCESS;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.EXCEPTIONAL_HALT;

import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.EvmSpecVersion;
import org.hyperledger.besu.evm.MainnetEVMs;
import org.hyperledger.besu.evm.contractvalidation.EOFValidationCodeRule;
import org.hyperledger.besu.evm.contractvalidation.MaxCodeSizeRule;
import org.hyperledger.besu.evm.contractvalidation.PrefixCodeRule;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.testutils.TestMessageFrameBuilder;
import org.hyperledger.besu.evm.tracing.OperationTracer;

import java.util.Collections;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@Nested
@ExtendWith(MockitoExtension.class)
class ContractCreationProcessorTest
    extends AbstractMessageProcessorTest<ContractCreationProcessor> {

  EVM evm = MainnetEVMs.osaka(EvmConfiguration.DEFAULT);

  private ContractCreationProcessor processor;

  @Test
  void shouldThrowAnExceptionWhenCodeContractFormatInvalidPreEOF() {
    processor =
        new ContractCreationProcessor(
            evm, true, Collections.singletonList(PrefixCodeRule.of()), 1, Collections.emptyList());
    final Bytes contractCode = Bytes.fromHexString("EF01010101010101");
    final MessageFrame messageFrame = new TestMessageFrameBuilder().build();
    messageFrame.setOutputData(contractCode);
    messageFrame.setGasRemaining(10600L);

    processor.codeSuccess(messageFrame, OperationTracer.NO_TRACING);
    assertThat(messageFrame.getState()).isEqualTo(EXCEPTIONAL_HALT);
    assertThat(messageFrame.getExceptionalHaltReason())
        .contains(ExceptionalHaltReason.INVALID_CODE);
  }

  @Test
  void shouldNotThrowAnExceptionWhenCodeContractIsValid() {
    processor =
        new ContractCreationProcessor(
            evm, true, Collections.singletonList(PrefixCodeRule.of()), 1, Collections.emptyList());
    final Bytes contractCode = Bytes.fromHexString("0101010101010101");
    final MessageFrame messageFrame = new TestMessageFrameBuilder().build();
    messageFrame.setOutputData(contractCode);
    messageFrame.setGasRemaining(10600L);

    processor.codeSuccess(messageFrame, OperationTracer.NO_TRACING);
    assertThat(messageFrame.getState()).isEqualTo(COMPLETED_SUCCESS);
  }

  @Test
  void shouldNotThrowAnExceptionWhenPrefixCodeRuleNotAdded() {
    processor =
        new ContractCreationProcessor(
            evm, true, Collections.emptyList(), 1, Collections.emptyList());
    final Bytes contractCode = Bytes.fromHexString("0F01010101010101");
    final MessageFrame messageFrame = new TestMessageFrameBuilder().build();
    messageFrame.setOutputData(contractCode);
    messageFrame.setGasRemaining(10600L);

    processor.codeSuccess(messageFrame, OperationTracer.NO_TRACING);
    assertThat(messageFrame.getState()).isEqualTo(COMPLETED_SUCCESS);
  }

  @Test
  void shouldThrowAnExceptionWhenCodeContractFormatInvalidPostEOF() {
    processor =
        new ContractCreationProcessor(
            evm,
            true,
            Collections.singletonList(EOFValidationCodeRule.from(evm)),
            1,
            Collections.emptyList());
    final Bytes contractCode = Bytes.fromHexString("EF00010101010101");
    final MessageFrame messageFrame = new TestMessageFrameBuilder().build();
    messageFrame.setOutputData(contractCode);
    messageFrame.setGasRemaining(10600L);

    processor.codeSuccess(messageFrame, OperationTracer.NO_TRACING);
    assertThat(messageFrame.getState()).isEqualTo(EXCEPTIONAL_HALT);
    assertThat(messageFrame.getExceptionalHaltReason())
        .contains(ExceptionalHaltReason.INVALID_CODE);
  }

  @Test
  void eofValidationShouldAllowLegacyDeployFromLegacyInit() {
    processor =
        new ContractCreationProcessor(
            evm,
            true,
            Collections.singletonList(EOFValidationCodeRule.from(evm)),
            1,
            Collections.emptyList());
    final Bytes contractCode = Bytes.fromHexString("0101010101010101");
    final MessageFrame messageFrame = new TestMessageFrameBuilder().build();
    messageFrame.setOutputData(contractCode);
    messageFrame.setGasRemaining(10600L);

    processor.codeSuccess(messageFrame, OperationTracer.NO_TRACING);
    assertThat(messageFrame.getState()).isEqualTo(COMPLETED_SUCCESS);
  }

  @Test
  void eofValidationShouldAllowEOFCode() {
    processor =
        new ContractCreationProcessor(
            evm,
            true,
            Collections.singletonList(EOFValidationCodeRule.from(evm)),
            1,
            Collections.emptyList());
    final MessageFrame messageFrame =
        new TestMessageFrameBuilder().code(evm.getCodeUncached(EOF_CREATE_CONTRACT)).build();
    messageFrame.setOutputData(INNER_CONTRACT);
    messageFrame.setGasRemaining(10600L);

    processor.codeSuccess(messageFrame, OperationTracer.NO_TRACING);
    assertThat(messageFrame.getState()).isEqualTo(COMPLETED_SUCCESS);
  }

  @Test
  void prefixValidationShouldPreventEOFCode() {
    processor =
        new ContractCreationProcessor(
            evm, true, Collections.singletonList(PrefixCodeRule.of()), 1, Collections.emptyList());
    final MessageFrame messageFrame = new TestMessageFrameBuilder().build();
    messageFrame.setOutputData(INNER_CONTRACT);
    messageFrame.setGasRemaining(10600L);

    processor.codeSuccess(messageFrame, OperationTracer.NO_TRACING);
    assertThat(messageFrame.getState()).isEqualTo(EXCEPTIONAL_HALT);
  }

  @Test
  void eofValidationShouldPreventLegacyDeployFromEOFInit() {
    processor =
        new ContractCreationProcessor(
            evm,
            true,
            Collections.singletonList(EOFValidationCodeRule.from(evm)),
            1,
            Collections.emptyList());
    final Bytes contractCode = Bytes.fromHexString("6030602001");
    final Bytes initCode = EOF_CREATE_CONTRACT;
    final MessageFrame messageFrame =
        new TestMessageFrameBuilder().code(evm.getCodeForCreation(initCode)).build();
    messageFrame.setOutputData(contractCode);
    messageFrame.setGasRemaining(10600L);

    processor.codeSuccess(messageFrame, OperationTracer.NO_TRACING);
    assertThat(messageFrame.getState()).isEqualTo(EXCEPTIONAL_HALT);
  }

  @Test
  void eofValidationPreventsEOFDeployFromLegacyInit() {
    processor =
        new ContractCreationProcessor(
            evm,
            true,
            Collections.singletonList(EOFValidationCodeRule.from(evm)),
            1,
            Collections.emptyList());
    final Bytes contractCode = EOF_CREATE_CONTRACT;
    final MessageFrame messageFrame = new TestMessageFrameBuilder().build();
    messageFrame.setOutputData(contractCode);
    messageFrame.setGasRemaining(10600L);

    processor.codeSuccess(messageFrame, OperationTracer.NO_TRACING);
    assertThat(messageFrame.getState()).isEqualTo(EXCEPTIONAL_HALT);
  }

  @Test
  void shouldThrowAnExceptionWhenCodeContractTooLarge() {
    processor =
        new ContractCreationProcessor(
            evm,
            true,
            Collections.singletonList(
                MaxCodeSizeRule.from(EvmSpecVersion.SPURIOUS_DRAGON, EvmConfiguration.DEFAULT)),
            1,
            Collections.emptyList());
    final Bytes contractCode =
        Bytes.fromHexString("00".repeat(EvmSpecVersion.SPURIOUS_DRAGON.getMaxCodeSize() + 1));
    final MessageFrame messageFrame = new TestMessageFrameBuilder().build();
    messageFrame.setOutputData(contractCode);
    messageFrame.setGasRemaining(10_000_000L);

    processor.codeSuccess(messageFrame, OperationTracer.NO_TRACING);
    assertThat(messageFrame.getState()).isEqualTo(EXCEPTIONAL_HALT);
    assertThat(messageFrame.getExceptionalHaltReason())
        .contains(ExceptionalHaltReason.CODE_TOO_LARGE);
  }

  @Test
  void shouldNotThrowAnExceptionWhenCodeContractTooLarge() {
    processor =
        new ContractCreationProcessor(
            evm,
            true,
            Collections.singletonList(
                MaxCodeSizeRule.from(EvmSpecVersion.SPURIOUS_DRAGON, EvmConfiguration.DEFAULT)),
            1,
            Collections.emptyList());
    final Bytes contractCode =
        Bytes.fromHexString("00".repeat(EvmSpecVersion.SPURIOUS_DRAGON.getMaxCodeSize()));
    final MessageFrame messageFrame = new TestMessageFrameBuilder().build();
    messageFrame.setOutputData(contractCode);
    messageFrame.setGasRemaining(5_000_000L);

    processor.codeSuccess(messageFrame, OperationTracer.NO_TRACING);
    assertThat(messageFrame.getState()).isEqualTo(COMPLETED_SUCCESS);
  }

  @Test
  void shouldNotThrowAnExceptionWhenCodeSizeRuleNotAdded() {
    processor =
        new ContractCreationProcessor(
            evm, true, Collections.emptyList(), 1, Collections.emptyList());
    final Bytes contractCode = Bytes.fromHexString("00".repeat(24 * 1024 + 1));
    final MessageFrame messageFrame = new TestMessageFrameBuilder().build();
    messageFrame.setOutputData(contractCode);
    messageFrame.setGasRemaining(5_000_000L);

    processor.codeSuccess(messageFrame, OperationTracer.NO_TRACING);
    assertThat(messageFrame.getState()).isEqualTo(COMPLETED_SUCCESS);
  }

  @Override
  protected ContractCreationProcessor getAbstractMessageProcessor() {
    return new ContractCreationProcessor(
        evm, true, Collections.emptyList(), 1, Collections.emptyList());
  }
}
