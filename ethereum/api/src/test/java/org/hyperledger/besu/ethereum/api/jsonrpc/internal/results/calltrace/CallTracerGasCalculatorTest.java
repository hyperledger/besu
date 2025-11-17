/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.calltrace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.tracing.TraceFrame;

import java.math.BigInteger;
import java.util.Optional;
import java.util.OptionalLong;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CallTracerGasCalculatorTest {

  // computeGasProvided Tests

  @Test
  @DisplayName("Should use next trace gas remaining when call entered")
  void computeGasProvided_shouldUseNextTraceGasWhenCallEntered() {
    TraceFrame frame = mock(TraceFrame.class);
    TraceFrame nextTrace = mock(TraceFrame.class);

    when(frame.getDepth()).thenReturn(1);
    when(nextTrace.getDepth()).thenReturn(2);
    when(nextTrace.getGasRemaining()).thenReturn(50000L);

    long gas = CallTracerGasCalculator.computeGasProvided(frame, nextTrace);

    assertThat(gas).isEqualTo(50000L);
  }

  @Test
  @DisplayName("Should return 0 for precompiles")
  void computeGasProvided_shouldReturnZeroForPrecompiles() {
    TraceFrame frame = mock(TraceFrame.class);

    when(frame.isPrecompile()).thenReturn(true);

    long gas = CallTracerGasCalculator.computeGasProvided(frame, null);

    assertThat(gas).isEqualTo(0L);
  }

  @Test
  @DisplayName("Should use gasAvailableForChildCall when present")
  void computeGasProvided_shouldUseGasAvailableForChildCallWhenPresent() {
    TraceFrame frame = mock(TraceFrame.class);

    when(frame.isPrecompile()).thenReturn(false);
    when(frame.getGasAvailableForChildCall()).thenReturn(OptionalLong.of(30000L));

    long gas = CallTracerGasCalculator.computeGasProvided(frame, null);

    assertThat(gas).isEqualTo(30000L);
  }

  @Test
  @DisplayName("Should calculate fallback gas using 63/64 rule")
  void computeGasProvided_shouldCalculateFallbackGas() {
    TraceFrame frame = mock(TraceFrame.class);

    when(frame.isPrecompile()).thenReturn(false);
    when(frame.getGasAvailableForChildCall()).thenReturn(OptionalLong.empty());
    when(frame.getGasRemainingPostExecution()).thenReturn(64000L);

    long gas = CallTracerGasCalculator.computeGasProvided(frame, null);

    // 64000 - (64000 / 64) = 64000 - 1000 = 63000
    assertThat(gas).isEqualTo(63000L);
  }

  @ParameterizedTest
  @CsvSource({
    "6400, 6300", // 6400 - 100 = 6300
    "640, 630", // 640 - 10 = 630
    "64, 63", // 64 - 1 = 63
    "63, 63", // 63 - 0 = 63 (integer division: 63/64=0)
    "0, 0", // 0 - 0 = 0
  })
  @DisplayName("Should apply 63/64 rule correctly")
  void computeGasProvided_shouldApply63_64RuleCorrectly(
      final long gasAfter, final long expectedChildGas) {
    TraceFrame frame = mock(TraceFrame.class);

    when(frame.isPrecompile()).thenReturn(false);
    when(frame.getGasAvailableForChildCall()).thenReturn(OptionalLong.empty());
    when(frame.getGasRemainingPostExecution()).thenReturn(gasAfter);

    long gas = CallTracerGasCalculator.computeGasProvided(frame, null);

    assertThat(gas).isEqualTo(expectedChildGas);
  }

  @Test
  @DisplayName("Should handle negative gas remaining post execution")
  void computeGasProvided_shouldHandleNegativeGasRemaining() {
    TraceFrame frame = mock(TraceFrame.class);

    when(frame.isPrecompile()).thenReturn(false);
    when(frame.getGasAvailableForChildCall()).thenReturn(OptionalLong.empty());
    when(frame.getGasRemainingPostExecution()).thenReturn(-100L);

    long gas = CallTracerGasCalculator.computeGasProvided(frame, null);

    // Should clamp to 0
    assertThat(gas).isEqualTo(0L);
  }

  // calculatePrecompileGas Tests

  @Test
  @DisplayName("Should apply Geth-style precompile gas calculation")
  void calculatePrecompileGas_shouldApplyGethStyleCalculation() {
    TraceFrame frame = mock(TraceFrame.class);

    // Gas remaining = 10000
    // base = 10000 - 100 (warm access) = 9900
    // cap = 9900 - (9900 / 64) = 9900 - 154 = 9746
    when(frame.getGasRemaining()).thenReturn(10000L);

    long gas = CallTracerGasCalculator.calculatePrecompileGas(frame);

    assertThat(gas).isEqualTo(9746L);
  }

  @Test
  @DisplayName("Should handle gas remaining less than warm access cost")
  void calculatePrecompileGas_shouldHandleLowGasRemaining() {
    TraceFrame frame = mock(TraceFrame.class);

    when(frame.getGasRemaining()).thenReturn(50L); // Less than WARM_ACCESS_GAS (100)

    long gas = CallTracerGasCalculator.calculatePrecompileGas(frame);

    // base = 0, cap = 0
    assertThat(gas).isEqualTo(0L);
  }

  @Test
  @DisplayName("Should handle zero gas remaining")
  void calculatePrecompileGas_shouldHandleZeroGasRemaining() {
    TraceFrame frame = mock(TraceFrame.class);

    when(frame.getGasRemaining()).thenReturn(0L);

    long gas = CallTracerGasCalculator.calculatePrecompileGas(frame);

    assertThat(gas).isEqualTo(0L);
  }

  @Test
  @DisplayName("Should handle negative gas remaining")
  void calculatePrecompileGas_shouldHandleNegativeGasRemaining() {
    TraceFrame frame = mock(TraceFrame.class);

    when(frame.getGasRemaining()).thenReturn(-100L);

    long gas = CallTracerGasCalculator.calculatePrecompileGas(frame);

    assertThat(gas).isEqualTo(0L);
  }

  @ParameterizedTest
  @CsvSource({
    "100, 0", // Exactly warm access cost: base=0, cap=0
    "101, 1", // Just above warm access: base=1, cap=1-(1/64)=1-0=1
    "164, 63", // base=64, cap=64-1=63
    "1000, 886", // base=900, cap=900-14=886
  })
  @DisplayName("Should calculate precompile gas correctly for various inputs")
  void calculatePrecompileGas_shouldCalculateCorrectly(
      final long gasRemaining, final long expectedGas) {
    TraceFrame frame = mock(TraceFrame.class);
    when(frame.getGasRemaining()).thenReturn(gasRemaining);

    long gas = CallTracerGasCalculator.calculatePrecompileGas(frame);

    assertThat(gas).isEqualTo(expectedGas);
  }

  // calculatePrecompileGasUsed Tests

  @Test
  @DisplayName("Should return all gas when precompile failed")
  void calculatePrecompileGasUsed_shouldReturnAllGasWhenFailed() {
    TraceFrame frame = mock(TraceFrame.class);

    long gasUsed = CallTracerGasCalculator.calculatePrecompileGasUsed(frame, true, 5000L);

    assertThat(gasUsed).isEqualTo(5000L);
  }

  @Test
  @DisplayName("Should return precompiled gas cost when successful")
  void calculatePrecompileGasUsed_shouldReturnPrecompiledGasCost() {
    TraceFrame frame = mock(TraceFrame.class);
    when(frame.getPrecompiledGasCost()).thenReturn(OptionalLong.of(3000L));

    long gasUsed = CallTracerGasCalculator.calculatePrecompileGasUsed(frame, false, 5000L);

    assertThat(gasUsed).isEqualTo(3000L);
  }

  @Test
  @DisplayName("Should fallback to gas cost when precompiled cost not available")
  void calculatePrecompileGasUsed_shouldFallbackToGasCost() {
    TraceFrame frame = mock(TraceFrame.class);
    when(frame.getPrecompiledGasCost()).thenReturn(OptionalLong.empty());
    when(frame.getGasCost()).thenReturn(OptionalLong.of(2500L));

    long gasUsed = CallTracerGasCalculator.calculatePrecompileGasUsed(frame, false, 5000L);

    assertThat(gasUsed).isEqualTo(2500L);
  }

  @Test
  @DisplayName("Should return 0 when no cost information available")
  void calculatePrecompileGasUsed_shouldReturnZeroWhenNoCostInfo() {
    TraceFrame frame = mock(TraceFrame.class);
    when(frame.getPrecompiledGasCost()).thenReturn(OptionalLong.empty());
    when(frame.getGasCost()).thenReturn(OptionalLong.empty());

    long gasUsed = CallTracerGasCalculator.calculatePrecompileGasUsed(frame, false, 5000L);

    assertThat(gasUsed).isEqualTo(0L);
  }

  // calculateGasUsed Tests

  @Test
  @DisplayName("Should calculate gas used for successful CALL")
  void calculateGasUsed_shouldCalculateGasUsedForSuccessfulCall() {
    TraceFrame entryFrame = mock(TraceFrame.class);
    TraceFrame exitFrame = mock(TraceFrame.class);

    when(exitFrame.getDepth()).thenReturn(1);
    when(exitFrame.getGasRemaining()).thenReturn(40000L);
    when(exitFrame.getOpcode()).thenReturn("RETURN");
    when(exitFrame.getOutputData()).thenReturn(Bytes.EMPTY);
    when(exitFrame.getExceptionalHaltReason()).thenReturn(Optional.empty());

    BigInteger gasProvided = BigInteger.valueOf(50000L);

    long gasUsed =
        CallTracerGasCalculator.calculateGasUsed(gasProvided, "CALL", entryFrame, exitFrame);

    assertThat(gasUsed).isEqualTo(10000L); // 50000 - 40000
  }

  @Test
  @DisplayName("Should add code deposit cost for successful CREATE")
  void calculateGasUsed_shouldAddCodeDepositCostForCreate() {
    TraceFrame entryFrame = mock(TraceFrame.class);
    TraceFrame exitFrame = mock(TraceFrame.class);

    when(exitFrame.getDepth()).thenReturn(1);
    when(exitFrame.getGasRemaining()).thenReturn(40000L);
    when(exitFrame.getOpcode()).thenReturn("RETURN");
    Bytes code = Bytes.repeat((byte) 0xFF, 100); // 100 bytes of code
    when(exitFrame.getOutputData()).thenReturn(code);
    when(exitFrame.getExceptionalHaltReason()).thenReturn(Optional.empty());

    BigInteger gasProvided = BigInteger.valueOf(50000L);

    long gasUsed =
        CallTracerGasCalculator.calculateGasUsed(gasProvided, "CREATE", entryFrame, exitFrame);

    // 50000 - 40000 + (100 * 200) = 10000 + 20000 = 30000
    assertThat(gasUsed).isEqualTo(30000L);
  }

  @Test
  @DisplayName("Should add code deposit cost for successful CREATE2")
  void calculateGasUsed_shouldAddCodeDepositCostForCreate2() {
    TraceFrame entryFrame = mock(TraceFrame.class);
    TraceFrame exitFrame = mock(TraceFrame.class);

    when(exitFrame.getDepth()).thenReturn(1);
    when(exitFrame.getGasRemaining()).thenReturn(40000L);
    when(exitFrame.getOpcode()).thenReturn("RETURN");
    Bytes code = Bytes.repeat((byte) 0xFF, 50); // 50 bytes of code
    when(exitFrame.getOutputData()).thenReturn(code);
    when(exitFrame.getExceptionalHaltReason()).thenReturn(Optional.empty());

    BigInteger gasProvided = BigInteger.valueOf(50000L);

    long gasUsed =
        CallTracerGasCalculator.calculateGasUsed(gasProvided, "CREATE2", entryFrame, exitFrame);

    // 50000 - 40000 + (50 * 200) = 10000 + 10000 = 20000
    assertThat(gasUsed).isEqualTo(20000L);
  }

  @Test
  @DisplayName("Should not add code deposit cost for reverted CREATE")
  void calculateGasUsed_shouldNotAddCodeDepositCostForRevertedCreate() {
    TraceFrame entryFrame = mock(TraceFrame.class);
    TraceFrame exitFrame = mock(TraceFrame.class);

    when(exitFrame.getDepth()).thenReturn(1);
    when(exitFrame.getGasRemaining()).thenReturn(40000L);
    when(exitFrame.getOpcode()).thenReturn("REVERT"); // Reverted, not RETURN
    when(exitFrame.getOutputData()).thenReturn(Bytes.repeat((byte) 0xFF, 100));
    when(exitFrame.getExceptionalHaltReason()).thenReturn(Optional.empty());

    BigInteger gasProvided = BigInteger.valueOf(50000L);

    long gasUsed =
        CallTracerGasCalculator.calculateGasUsed(gasProvided, "CREATE", entryFrame, exitFrame);

    // No code deposit cost added because it reverted (REVERT opcode, not RETURN)
    assertThat(gasUsed).isEqualTo(10000L);
  }

  @Test
  @DisplayName("Should not add code deposit cost for failed CREATE with exceptional halt")
  void calculateGasUsed_shouldNotAddCodeDepositCostForFailedCreate() {
    TraceFrame entryFrame = mock(TraceFrame.class);
    TraceFrame exitFrame = mock(TraceFrame.class);

    when(exitFrame.getDepth()).thenReturn(1);
    when(exitFrame.getGasRemaining()).thenReturn(40000L);
    when(exitFrame.getOpcode()).thenReturn("RETURN");
    when(exitFrame.getOutputData()).thenReturn(Bytes.repeat((byte) 0xFF, 100));
    when(exitFrame.getExceptionalHaltReason())
        .thenReturn(Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));

    BigInteger gasProvided = BigInteger.valueOf(50000L);

    long gasUsed =
        CallTracerGasCalculator.calculateGasUsed(gasProvided, "CREATE", entryFrame, exitFrame);

    // No code deposit cost added because of exceptional halt
    assertThat(gasUsed).isEqualTo(10000L);
  }

  @Test
  @DisplayName("Should handle SELFDESTRUCT in exit frame")
  void calculateGasUsed_shouldHandleSelfDestructInExitFrame() {
    TraceFrame entryFrame = mock(TraceFrame.class);
    TraceFrame exitFrame = mock(TraceFrame.class);

    when(exitFrame.getDepth()).thenReturn(1);
    when(exitFrame.getGasRemaining()).thenReturn(40000L);
    when(exitFrame.getOpcode()).thenReturn("SELFDESTRUCT");
    when(exitFrame.getGasCost()).thenReturn(OptionalLong.of(5000L));
    when(exitFrame.getOutputData()).thenReturn(Bytes.EMPTY);
    when(exitFrame.getExceptionalHaltReason()).thenReturn(Optional.empty());

    BigInteger gasProvided = BigInteger.valueOf(50000L);

    long gasUsed =
        CallTracerGasCalculator.calculateGasUsed(gasProvided, "CALL", entryFrame, exitFrame);

    // 50000 - 40000 + 5000 = 15000
    assertThat(gasUsed).isEqualTo(15000L);
  }

  @Test
  @DisplayName("Should handle root transaction (depth 0)")
  void calculateGasUsed_shouldHandleRootTransaction() {
    TraceFrame entryFrame = mock(TraceFrame.class);
    TraceFrame exitFrame = mock(TraceFrame.class);

    when(exitFrame.getDepth()).thenReturn(0);
    when(entryFrame.getGasRemaining()).thenReturn(100000L);
    when(exitFrame.getGasRemaining()).thenReturn(20000L);
    when(exitFrame.getGasRefund()).thenReturn(5000L);

    BigInteger gasProvided = BigInteger.valueOf(100000L);

    long gasUsed =
        CallTracerGasCalculator.calculateGasUsed(gasProvided, "CALL", entryFrame, exitFrame);

    // 100000 - 20000 - 5000 = 75000
    assertThat(gasUsed).isEqualTo(75000L);
  }

  // calculateImplicitReturnGasUsed Tests

  @Test
  @DisplayName("Should return all gas for insufficient gas error")
  void calculateImplicitReturnGasUsed_shouldReturnAllGasForInsufficientGas() {
    TraceFrame lastFrame = mock(TraceFrame.class);
    when(lastFrame.getExceptionalHaltReason())
        .thenReturn(Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));

    BigInteger gasProvided = BigInteger.valueOf(50000L);

    long gasUsed = CallTracerGasCalculator.calculateImplicitReturnGasUsed(lastFrame, gasProvided);

    assertThat(gasUsed).isEqualTo(50000L);
  }

  @Test
  @DisplayName("Should handle SELFDESTRUCT opcode")
  void calculateImplicitReturnGasUsed_shouldHandleSelfDestruct() {
    TraceFrame lastFrame = mock(TraceFrame.class);
    when(lastFrame.getExceptionalHaltReason()).thenReturn(Optional.empty());
    when(lastFrame.getOpcode()).thenReturn("SELFDESTRUCT");
    when(lastFrame.getGasRemaining()).thenReturn(40000L);
    when(lastFrame.getGasCost()).thenReturn(OptionalLong.of(5000L));

    BigInteger gasProvided = BigInteger.valueOf(50000L);

    long gasUsed = CallTracerGasCalculator.calculateImplicitReturnGasUsed(lastFrame, gasProvided);

    // 50000 - 40000 + 5000 = 15000
    assertThat(gasUsed).isEqualTo(15000L);
  }

  @Test
  @DisplayName("Should calculate normal gas used")
  void calculateImplicitReturnGasUsed_shouldCalculateNormalGasUsed() {
    TraceFrame lastFrame = mock(TraceFrame.class);
    when(lastFrame.getExceptionalHaltReason()).thenReturn(Optional.empty());
    when(lastFrame.getOpcode()).thenReturn("SSTORE");
    when(lastFrame.getGasRemaining()).thenReturn(30000L);

    BigInteger gasProvided = BigInteger.valueOf(50000L);

    long gasUsed = CallTracerGasCalculator.calculateImplicitReturnGasUsed(lastFrame, gasProvided);

    // 50000 - 30000 = 20000
    assertThat(gasUsed).isEqualTo(20000L);
  }

  @Test
  @DisplayName("Should clamp to zero if gas remaining exceeds provided")
  void calculateImplicitReturnGasUsed_shouldClampToZero() {
    TraceFrame lastFrame = mock(TraceFrame.class);
    when(lastFrame.getExceptionalHaltReason()).thenReturn(Optional.empty());
    when(lastFrame.getOpcode()).thenReturn("ADD");
    when(lastFrame.getGasRemaining()).thenReturn(60000L);

    BigInteger gasProvided = BigInteger.valueOf(50000L);

    long gasUsed = CallTracerGasCalculator.calculateImplicitReturnGasUsed(lastFrame, gasProvided);

    // Should not go negative
    assertThat(gasUsed).isEqualTo(0L);
  }

  // calculateHardFailureGasUsed Tests

  @Test
  @DisplayName("Should return gas cost for insufficient gas error")
  void calculateHardFailureGasUsed_shouldReturnGasCostForInsufficientGas() {
    TraceFrame frame = mock(TraceFrame.class);
    when(frame.getExceptionalHaltReason())
        .thenReturn(Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
    when(frame.getGasCost()).thenReturn(OptionalLong.of(21000L));

    long gasUsed = CallTracerGasCalculator.calculateHardFailureGasUsed(frame);

    assertThat(gasUsed).isEqualTo(21000L);
  }

  @Test
  @DisplayName("Should return 0 for other exceptional halts")
  void calculateHardFailureGasUsed_shouldReturnZeroForOtherHalts() {
    TraceFrame frame = mock(TraceFrame.class);
    when(frame.getExceptionalHaltReason())
        .thenReturn(Optional.of(ExceptionalHaltReason.INVALID_OPERATION));

    long gasUsed = CallTracerGasCalculator.calculateHardFailureGasUsed(frame);

    assertThat(gasUsed).isEqualTo(0L);
  }

  @Test
  @DisplayName("Should return 0 when no exceptional halt")
  void calculateHardFailureGasUsed_shouldReturnZeroWhenNoHalt() {
    TraceFrame frame = mock(TraceFrame.class);
    when(frame.getExceptionalHaltReason()).thenReturn(Optional.empty());

    long gasUsed = CallTracerGasCalculator.calculateHardFailureGasUsed(frame);

    assertThat(gasUsed).isEqualTo(0L);
  }

  // isInsufficientGasError Tests

  @Test
  @DisplayName("Should return true for INSUFFICIENT_GAS")
  void isInsufficientGasError_shouldReturnTrueForInsufficientGas() {
    TraceFrame frame = mock(TraceFrame.class);
    when(frame.getExceptionalHaltReason())
        .thenReturn(Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));

    assertThat(CallTracerGasCalculator.isInsufficientGasError(frame)).isTrue();
  }

  @Test
  @DisplayName("Should return false for other exceptional halts")
  void isInsufficientGasError_shouldReturnFalseForOtherHalts() {
    TraceFrame frame = mock(TraceFrame.class);
    when(frame.getExceptionalHaltReason())
        .thenReturn(Optional.of(ExceptionalHaltReason.INVALID_OPERATION));

    assertThat(CallTracerGasCalculator.isInsufficientGasError(frame)).isFalse();
  }

  @Test
  @DisplayName("Should return false when no exceptional halt")
  void isInsufficientGasError_shouldReturnFalseWhenNoHalt() {
    TraceFrame frame = mock(TraceFrame.class);
    when(frame.getExceptionalHaltReason()).thenReturn(Optional.empty());

    assertThat(CallTracerGasCalculator.isInsufficientGasError(frame)).isFalse();
  }

  // shouldAddCodeDepositCost Tests

  @Test
  @DisplayName("Should return true for successful CREATE with output")
  void shouldAddCodeDepositCost_shouldReturnTrueForSuccessfulCreate() {
    TraceFrame exitFrame = mock(TraceFrame.class);
    when(exitFrame.getOpcode()).thenReturn("RETURN");
    when(exitFrame.getOutputData()).thenReturn(Bytes.of(1, 2, 3));
    when(exitFrame.getExceptionalHaltReason()).thenReturn(Optional.empty());

    assertThat(CallTracerGasCalculator.shouldAddCodeDepositCost("CREATE", exitFrame)).isTrue();
  }

  @Test
  @DisplayName("Should return true for successful CREATE2 with output")
  void shouldAddCodeDepositCost_shouldReturnTrueForSuccessfulCreate2() {
    TraceFrame exitFrame = mock(TraceFrame.class);
    when(exitFrame.getOpcode()).thenReturn("RETURN");
    when(exitFrame.getOutputData()).thenReturn(Bytes.of(1, 2, 3));
    when(exitFrame.getExceptionalHaltReason()).thenReturn(Optional.empty());

    assertThat(CallTracerGasCalculator.shouldAddCodeDepositCost("CREATE2", exitFrame)).isTrue();
  }

  @Test
  @DisplayName("Should return false for CALL")
  void shouldAddCodeDepositCost_shouldReturnFalseForCall() {
    TraceFrame exitFrame = mock(TraceFrame.class);
    when(exitFrame.getOpcode()).thenReturn("RETURN");
    when(exitFrame.getOutputData()).thenReturn(Bytes.of(1, 2, 3));
    when(exitFrame.getExceptionalHaltReason()).thenReturn(Optional.empty());

    assertThat(CallTracerGasCalculator.shouldAddCodeDepositCost("CALL", exitFrame)).isFalse();
  }

  @Test
  @DisplayName("Should return false when exit opcode is REVERT")
  void shouldAddCodeDepositCost_shouldReturnFalseWhenRevert() {
    TraceFrame exitFrame = mock(TraceFrame.class);
    when(exitFrame.getOpcode()).thenReturn("REVERT");
    when(exitFrame.getOutputData()).thenReturn(Bytes.of(1, 2, 3));
    when(exitFrame.getExceptionalHaltReason()).thenReturn(Optional.empty());

    assertThat(CallTracerGasCalculator.shouldAddCodeDepositCost("CREATE", exitFrame)).isFalse();
  }

  @Test
  @DisplayName("Should return false when output is null")
  void shouldAddCodeDepositCost_shouldReturnFalseWhenOutputNull() {
    TraceFrame exitFrame = mock(TraceFrame.class);
    when(exitFrame.getOpcode()).thenReturn("RETURN");
    when(exitFrame.getOutputData()).thenReturn(null);
    when(exitFrame.getExceptionalHaltReason()).thenReturn(Optional.empty());

    assertThat(CallTracerGasCalculator.shouldAddCodeDepositCost("CREATE", exitFrame)).isFalse();
  }

  @Test
  @DisplayName("Should return false when output is empty")
  void shouldAddCodeDepositCost_shouldReturnFalseWhenOutputEmpty() {
    TraceFrame exitFrame = mock(TraceFrame.class);
    when(exitFrame.getOpcode()).thenReturn("RETURN");
    when(exitFrame.getOutputData()).thenReturn(Bytes.EMPTY);
    when(exitFrame.getExceptionalHaltReason()).thenReturn(Optional.empty());

    assertThat(CallTracerGasCalculator.shouldAddCodeDepositCost("CREATE", exitFrame)).isFalse();
  }

  @Test
  @DisplayName("Should return false when exceptional halt present")
  void shouldAddCodeDepositCost_shouldReturnFalseWhenExceptionalHalt() {
    TraceFrame exitFrame = mock(TraceFrame.class);
    when(exitFrame.getOpcode()).thenReturn("RETURN");
    when(exitFrame.getOutputData()).thenReturn(Bytes.of(1, 2, 3));
    when(exitFrame.getExceptionalHaltReason())
        .thenReturn(Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));

    assertThat(CallTracerGasCalculator.shouldAddCodeDepositCost("CREATE", exitFrame)).isFalse();
  }
}
