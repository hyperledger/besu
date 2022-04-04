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
package org.hyperledger.besu.ethereum.mainnet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.COMPLETED_SUCCESS;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.EXCEPTIONAL_HALT;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.core.MessageFrameTestFixture;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.contractvalidation.MaxCodeSizeRule;
import org.hyperledger.besu.evm.contractvalidation.PrefixCodeRule;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;
import org.hyperledger.besu.evm.tracing.OperationTracer;

import java.util.Collections;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MainnetContractCreationProcessorTest {

  @Mock GasCalculator gasCalculator;
  @Mock EVM evm;

  private ContractCreationProcessor processor;

  @Test
  public void shouldThrowAnExceptionWhenCodeContractFormatInvalid() {
    processor =
        new ContractCreationProcessor(
            gasCalculator,
            evm,
            true,
            Collections.singletonList(PrefixCodeRule.of()),
            1,
            Collections.emptyList());
    final Bytes contractCode = Bytes.fromHexString("EF01010101010101");
    final MessageFrame messageFrame = new MessageFrameTestFixture().build();
    messageFrame.setOutputData(contractCode);
    messageFrame.setGasRemaining(100L);

    when(gasCalculator.codeDepositGasCost(contractCode.size())).thenReturn(10L);
    processor.codeSuccess(messageFrame, OperationTracer.NO_TRACING);
    assertThat(messageFrame.getState()).isEqualTo(EXCEPTIONAL_HALT);
    assertThat(messageFrame.getExceptionalHaltReason())
        .contains(ExceptionalHaltReason.INVALID_CODE);
  }

  @Test
  public void shouldNotThrowAnExceptionWhenCodeContractIsValid() {
    processor =
        new ContractCreationProcessor(
            gasCalculator,
            evm,
            true,
            Collections.singletonList(PrefixCodeRule.of()),
            1,
            Collections.emptyList());
    final Bytes contractCode = Bytes.fromHexString("0101010101010101");
    final MessageFrame messageFrame = new MessageFrameTestFixture().build();
    messageFrame.setOutputData(contractCode);
    messageFrame.setGasRemaining(100L);

    when(gasCalculator.codeDepositGasCost(contractCode.size())).thenReturn(10L);
    processor.codeSuccess(messageFrame, OperationTracer.NO_TRACING);
    assertThat(messageFrame.getState()).isEqualTo(COMPLETED_SUCCESS);
  }

  @Test
  public void shouldNotThrowAnExceptionWhenPrefixCodeRuleNotAdded() {
    processor =
        new ContractCreationProcessor(
            gasCalculator, evm, true, Collections.emptyList(), 1, Collections.emptyList());
    final Bytes contractCode = Bytes.fromHexString("0F01010101010101");
    final MessageFrame messageFrame = new MessageFrameTestFixture().build();
    messageFrame.setOutputData(contractCode);
    messageFrame.setGasRemaining(100L);

    when(gasCalculator.codeDepositGasCost(contractCode.size())).thenReturn(10L);
    processor.codeSuccess(messageFrame, OperationTracer.NO_TRACING);
    assertThat(messageFrame.getState()).isEqualTo(COMPLETED_SUCCESS);
  }

  @Test
  public void shouldThrowAnExceptionWhenCodeContractTooLarge() {
    processor =
        new ContractCreationProcessor(
            gasCalculator,
            evm,
            true,
            Collections.singletonList(MaxCodeSizeRule.of(24 * 1024)),
            1,
            Collections.emptyList());
    final Bytes contractCode = Bytes.fromHexString("00".repeat(24 * 1024 + 1));
    final MessageFrame messageFrame = new MessageFrameTestFixture().build();
    messageFrame.setOutputData(contractCode);
    messageFrame.setGasRemaining(100L);

    when(gasCalculator.codeDepositGasCost(contractCode.size())).thenReturn(10L);
    processor.codeSuccess(messageFrame, OperationTracer.NO_TRACING);
    assertThat(messageFrame.getState()).isEqualTo(EXCEPTIONAL_HALT);
    assertThat(messageFrame.getExceptionalHaltReason())
        .contains(ExceptionalHaltReason.CODE_TOO_LARGE);
  }

  @Test
  public void shouldNotThrowAnExceptionWhenCodeContractTooLarge() {
    processor =
        new ContractCreationProcessor(
            gasCalculator,
            evm,
            true,
            Collections.singletonList(MaxCodeSizeRule.of(24 * 1024)),
            1,
            Collections.emptyList());
    final Bytes contractCode = Bytes.fromHexString("00".repeat(24 * 1024));
    final MessageFrame messageFrame = new MessageFrameTestFixture().build();
    messageFrame.setOutputData(contractCode);
    messageFrame.setGasRemaining(100L);

    when(gasCalculator.codeDepositGasCost(contractCode.size())).thenReturn(10L);
    processor.codeSuccess(messageFrame, OperationTracer.NO_TRACING);
    assertThat(messageFrame.getState()).isEqualTo(COMPLETED_SUCCESS);
  }

  @Test
  public void shouldNotThrowAnExceptionWhenCodeSizeRuleNotAdded() {
    processor =
        new ContractCreationProcessor(
            gasCalculator, evm, true, Collections.emptyList(), 1, Collections.emptyList());
    final Bytes contractCode = Bytes.fromHexString("00".repeat(24 * 1024 + 1));
    final MessageFrame messageFrame = new MessageFrameTestFixture().build();
    messageFrame.setOutputData(contractCode);
    messageFrame.setGasRemaining(100L);

    when(gasCalculator.codeDepositGasCost(contractCode.size())).thenReturn(10L);
    processor.codeSuccess(messageFrame, OperationTracer.NO_TRACING);
    assertThat(messageFrame.getState()).isEqualTo(COMPLETED_SUCCESS);
  }
}
