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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.hyperledger.besu.evm.tracing.TraceFrame;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FourByteTracerResultConverterTest {

  // Helper methods for creating mocks

  /**
   * Creates a mock TransactionTrace with a contract creation transaction (to skip initial
   * transaction processing in most tests)
   */
  private TransactionTrace createMockTraceWithContractCreation() {
    TransactionTrace mockTrace = mock(TransactionTrace.class);
    Transaction mockTx = mock(Transaction.class);
    when(mockTx.isContractCreation()).thenReturn(true);
    when(mockTrace.getTransaction()).thenReturn(mockTx);
    return mockTrace;
  }

  /**
   * Creates a mock TransactionTrace with a regular contract call transaction
   *
   * @param to The recipient address
   * @param payload The transaction input data
   */
  private TransactionTrace createMockTraceWithContractCall(final Address to, final Bytes payload) {
    TransactionTrace mockTrace = mock(TransactionTrace.class);
    Transaction mockTx = mock(Transaction.class);
    when(mockTx.isContractCreation()).thenReturn(false);
    when(mockTx.getTo()).thenReturn(Optional.of(to));
    when(mockTx.getPayload()).thenReturn(payload);
    when(mockTrace.getTransaction()).thenReturn(mockTx);
    return mockTrace;
  }

  private TraceFrame createCallFrame(final String opcode, final int depth) {
    TraceFrame frame = mock(TraceFrame.class);
    when(frame.getOpcode()).thenReturn(opcode);
    when(frame.getDepth()).thenReturn(depth);
    when(frame.isPrecompile()).thenReturn(false);
    return frame;
  }

  private TraceFrame createNextFrame(final int depth, final String inputDataHex) {
    TraceFrame frame = mock(TraceFrame.class);
    when(frame.getDepth()).thenReturn(depth);
    when(frame.getInputData()).thenReturn(Bytes.fromHexString(inputDataHex));
    return frame;
  }

  private TraceFrame createInstructionFrame(final String opcode, final int depth) {
    TraceFrame frame = mock(TraceFrame.class);
    when(frame.getOpcode()).thenReturn(opcode);
    when(frame.getDepth()).thenReturn(depth);
    return frame;
  }

  /**
   * Creates a mock ProtocolSpec with a PrecompileContractRegistry that returns null for all
   * addresses (i.e., no precompiles). Tests that need to verify precompile behavior should mock
   * specific addresses.
   */
  private ProtocolSpec createMockProtocolSpec() {
    ProtocolSpec mockSpec = mock(ProtocolSpec.class);
    PrecompileContractRegistry mockRegistry = mock(PrecompileContractRegistry.class);
    when(mockSpec.getPrecompileContractRegistry()).thenReturn(mockRegistry);
    when(mockRegistry.get(org.mockito.ArgumentMatchers.any(Address.class))).thenReturn(null);
    return mockSpec;
  }

  @Test
  @DisplayName("should return empty result when trace frames are null")
  void shouldReturnEmptyResultWhenTraceFramesAreNull() {
    // Given
    TransactionTrace mockTrace = createMockTraceWithContractCreation();
    when(mockTrace.getTraceFrames()).thenReturn(null);
    ProtocolSpec mockSpec = createMockProtocolSpec();

    // When
    FourByteTracerResult result = FourByteTracerResultConverter.convert(mockTrace, mockSpec);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getSelectorCounts()).isEmpty();
  }

  @Test
  @DisplayName("should return empty result when trace frames are empty")
  void shouldReturnEmptyResultWhenTraceFramesAreEmpty() {
    // Given
    TransactionTrace mockTrace = createMockTraceWithContractCreation();
    when(mockTrace.getTraceFrames()).thenReturn(List.of());
    ProtocolSpec mockSpec = createMockProtocolSpec();

    // When
    FourByteTracerResult result = FourByteTracerResultConverter.convert(mockTrace, mockSpec);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getSelectorCounts()).isEmpty();
  }

  @Test
  @DisplayName("should count function selector from CALL operation that enters")
  void shouldCountFunctionSelectorFromCallOperationThatEnters() {
    // Given
    TransactionTrace mockTrace = createMockTraceWithContractCreation();
    TraceFrame callFrame = mock(TraceFrame.class);
    TraceFrame nextFrame = mock(TraceFrame.class);

    // Setup CALL frame
    when(callFrame.getOpcode()).thenReturn("CALL");
    when(callFrame.getDepth()).thenReturn(1);
    when(callFrame.isPrecompile()).thenReturn(false);

    // Setup next frame (call entered)
    when(nextFrame.getDepth()).thenReturn(2);
    // Function selector: 0x12345678, plus 96 bytes of data = 100 bytes total
    Bytes inputData = Bytes.fromHexString("0x" + "12345678" + "00".repeat(96));
    when(nextFrame.getInputData()).thenReturn(inputData);

    when(mockTrace.getTraceFrames()).thenReturn(List.of(callFrame, nextFrame));
    ProtocolSpec mockSpec = createMockProtocolSpec();

    // When
    FourByteTracerResult result = FourByteTracerResultConverter.convert(mockTrace, mockSpec);

    // Then
    assertThat(result).isNotNull();
    Map<String, Integer> counts = result.getSelectorCounts();
    assertThat(counts).hasSize(1);
    assertThat(counts).containsEntry("0x12345678-96", 1);
  }

  @Test
  @DisplayName("should skip CALL operation that does not enter")
  void shouldSkipCallOperationThatDoesNotEnter() {
    // Given
    TransactionTrace mockTrace = createMockTraceWithContractCreation();
    TraceFrame callFrame = mock(TraceFrame.class);
    TraceFrame nextFrame = mock(TraceFrame.class);

    // Setup CALL frame
    when(callFrame.getOpcode()).thenReturn("CALL");
    when(callFrame.getDepth()).thenReturn(1);
    when(callFrame.isPrecompile()).thenReturn(false);

    // Setup next frame (call did NOT enter - same depth)
    when(nextFrame.getDepth()).thenReturn(1);

    when(mockTrace.getTraceFrames()).thenReturn(List.of(callFrame, nextFrame));
    ProtocolSpec mockSpec = createMockProtocolSpec();

    // When
    FourByteTracerResult result = FourByteTracerResultConverter.convert(mockTrace, mockSpec);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getSelectorCounts()).isEmpty();
  }

  @Test
  @DisplayName("should skip precompiled contracts")
  void shouldSkipPrecompiledContracts() {
    // Given
    TransactionTrace mockTrace = createMockTraceWithContractCreation();
    TraceFrame callFrame = mock(TraceFrame.class);
    TraceFrame nextFrame = mock(TraceFrame.class);

    // Setup CALL frame for precompile
    when(callFrame.getOpcode()).thenReturn("CALL");
    when(callFrame.getDepth()).thenReturn(1);
    when(callFrame.isPrecompile()).thenReturn(true); // Precompile

    // Setup next frame
    when(nextFrame.getDepth()).thenReturn(2);
    when(nextFrame.getInputData()).thenReturn(Bytes.fromHexString("0x12345678"));

    when(mockTrace.getTraceFrames()).thenReturn(List.of(callFrame, nextFrame));
    ProtocolSpec mockSpec = createMockProtocolSpec();

    // When
    FourByteTracerResult result = FourByteTracerResultConverter.convert(mockTrace, mockSpec);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getSelectorCounts()).isEmpty();
  }

  @Test
  @DisplayName("should skip calls with less than 4 bytes of input")
  void shouldSkipCallsWithLessThanFourBytesOfInput() {
    // Given
    TransactionTrace mockTrace = createMockTraceWithContractCreation();
    TraceFrame callFrame = mock(TraceFrame.class);
    TraceFrame nextFrame = mock(TraceFrame.class);

    // Setup CALL frame
    when(callFrame.getOpcode()).thenReturn("CALL");
    when(callFrame.getDepth()).thenReturn(1);
    when(callFrame.isPrecompile()).thenReturn(false);

    // Setup next frame with only 2 bytes of input
    when(nextFrame.getDepth()).thenReturn(2);
    when(nextFrame.getInputData()).thenReturn(Bytes.fromHexString("0x1234"));

    when(mockTrace.getTraceFrames()).thenReturn(List.of(callFrame, nextFrame));
    ProtocolSpec mockSpec = createMockProtocolSpec();

    // When
    FourByteTracerResult result = FourByteTracerResultConverter.convert(mockTrace, mockSpec);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getSelectorCounts()).isEmpty();
  }

  @Test
  @DisplayName("should skip CREATE operations")
  void shouldSkipCreateOperations() {
    // Given
    TransactionTrace mockTrace = createMockTraceWithContractCreation();
    TraceFrame createFrame = mock(TraceFrame.class);
    TraceFrame nextFrame = mock(TraceFrame.class);

    // Setup CREATE frame
    when(createFrame.getOpcode()).thenReturn("CREATE");
    when(createFrame.getDepth()).thenReturn(1);

    // Setup next frame
    when(nextFrame.getDepth()).thenReturn(2);
    when(nextFrame.getInputData()).thenReturn(Bytes.fromHexString("0x12345678"));

    when(mockTrace.getTraceFrames()).thenReturn(List.of(createFrame, nextFrame));
    ProtocolSpec mockSpec = createMockProtocolSpec();

    // When
    FourByteTracerResult result = FourByteTracerResultConverter.convert(mockTrace, mockSpec);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getSelectorCounts()).isEmpty();
  }

  @Test
  @DisplayName("should count multiple calls with same selector")
  void shouldCountMultipleCallsWithSameSelector() {
    // Given
    TransactionTrace mockTrace = createMockTraceWithContractCreation();

    // First CALL
    TraceFrame callFrame1 = mock(TraceFrame.class);
    when(callFrame1.getOpcode()).thenReturn("CALL");
    when(callFrame1.getDepth()).thenReturn(1);
    when(callFrame1.isPrecompile()).thenReturn(false);

    TraceFrame nextFrame1 = mock(TraceFrame.class);
    when(nextFrame1.getDepth()).thenReturn(2);
    when(nextFrame1.getInputData()).thenReturn(Bytes.fromHexString("0x12345678" + "00".repeat(32)));

    // Some other frame to separate the calls
    TraceFrame otherFrame = mock(TraceFrame.class);
    when(otherFrame.getOpcode()).thenReturn("ADD");
    when(otherFrame.getDepth()).thenReturn(1);

    // Second CALL with same selector
    TraceFrame callFrame2 = mock(TraceFrame.class);
    when(callFrame2.getOpcode()).thenReturn("DELEGATECALL");
    when(callFrame2.getDepth()).thenReturn(1);
    when(callFrame2.isPrecompile()).thenReturn(false);

    TraceFrame nextFrame2 = mock(TraceFrame.class);
    when(nextFrame2.getDepth()).thenReturn(2);
    when(nextFrame2.getInputData()).thenReturn(Bytes.fromHexString("0x12345678" + "00".repeat(32)));

    when(mockTrace.getTraceFrames())
        .thenReturn(List.of(callFrame1, nextFrame1, otherFrame, callFrame2, nextFrame2));
    ProtocolSpec mockSpec = createMockProtocolSpec();

    // When
    FourByteTracerResult result = FourByteTracerResultConverter.convert(mockTrace, mockSpec);

    // Then
    assertThat(result).isNotNull();
    Map<String, Integer> counts = result.getSelectorCounts();
    assertThat(counts).hasSize(1);
    assertThat(counts).containsEntry("0x12345678-32", 2);
  }

  @Test
  @DisplayName("should count different selectors separately")
  void shouldCountDifferentSelectorsSeparately() {
    // Given
    TransactionTrace mockTrace = createMockTraceWithContractCreation();

    // First CALL
    TraceFrame callFrame1 = createCallFrame("CALL", 1);
    TraceFrame nextFrame1 = createNextFrame(2, "0x12345678" + "00".repeat(32));

    // Second CALL with different selector
    TraceFrame callFrame2 = createCallFrame("STATICCALL", 1);
    TraceFrame nextFrame2 = createNextFrame(2, "0xabcdef01" + "00".repeat(64));

    when(mockTrace.getTraceFrames())
        .thenReturn(List.of(callFrame1, nextFrame1, callFrame2, nextFrame2));
    ProtocolSpec mockSpec = createMockProtocolSpec();

    // When
    FourByteTracerResult result = FourByteTracerResultConverter.convert(mockTrace, mockSpec);

    // Then
    assertThat(result).isNotNull();
    Map<String, Integer> counts = result.getSelectorCounts();
    assertThat(counts).hasSize(2);
    assertThat(counts).containsEntry("0x12345678-32", 1);
    assertThat(counts).containsEntry("0xabcdef01-64", 1);
  }

  @Test
  @DisplayName("should handle all CALL-type operations")
  void shouldHandleAllCallTypeOperations() {
    // Given
    TransactionTrace mockTrace = createMockTraceWithContractCreation();

    TraceFrame callFrame = createCallFrame("CALL", 1);
    TraceFrame callNextFrame = createNextFrame(2, "0x11111111" + "00".repeat(10));

    TraceFrame callcodeFrame = createCallFrame("CALLCODE", 1);
    TraceFrame callcodeNextFrame = createNextFrame(2, "0x22222222" + "00".repeat(20));

    TraceFrame delegatecallFrame = createCallFrame("DELEGATECALL", 1);
    TraceFrame delegatecallNextFrame = createNextFrame(2, "0x33333333" + "00".repeat(30));

    TraceFrame staticcallFrame = createCallFrame("STATICCALL", 1);
    TraceFrame staticcallNextFrame = createNextFrame(2, "0x44444444" + "00".repeat(40));

    when(mockTrace.getTraceFrames())
        .thenReturn(
            Arrays.asList(
                callFrame,
                callNextFrame,
                callcodeFrame,
                callcodeNextFrame,
                delegatecallFrame,
                delegatecallNextFrame,
                staticcallFrame,
                staticcallNextFrame));
    ProtocolSpec mockSpec = createMockProtocolSpec();

    // When
    FourByteTracerResult result = FourByteTracerResultConverter.convert(mockTrace, mockSpec);

    // Then
    assertThat(result).isNotNull();
    Map<String, Integer> counts = result.getSelectorCounts();
    assertThat(counts).hasSize(4);
    assertThat(counts).containsEntry("0x11111111-10", 1);
    assertThat(counts).containsEntry("0x22222222-20", 1);
    assertThat(counts).containsEntry("0x33333333-30", 1);
    assertThat(counts).containsEntry("0x44444444-40", 1);
  }

  @Test
  @DisplayName("should handle exactly 4 bytes of input")
  void shouldHandleExactlyFourBytesOfInput() {
    // Given
    TransactionTrace mockTrace = createMockTraceWithContractCreation();
    TraceFrame callFrame = createCallFrame("CALL", 1);
    TraceFrame nextFrame = createNextFrame(2, "0x12345678");

    when(mockTrace.getTraceFrames()).thenReturn(List.of(callFrame, nextFrame));
    ProtocolSpec mockSpec = createMockProtocolSpec();

    // When
    FourByteTracerResult result = FourByteTracerResultConverter.convert(mockTrace, mockSpec);

    // Then
    assertThat(result).isNotNull();
    Map<String, Integer> counts = result.getSelectorCounts();
    assertThat(counts).hasSize(1);
    assertThat(counts).containsEntry("0x12345678-0", 1);
  }

  @Test
  @DisplayName("should handle nested sub-calls at different depths")
  void shouldHandleNestedSubCalls() {
    // Given: method-A (depth 1) calls method-B (depth 2) which calls method-C (depth 3)
    TransactionTrace mockTrace = createMockTraceWithContractCreation();

    // Initial call to method-A
    TraceFrame callA = createCallFrame("CALL", 0);
    TraceFrame insideA = createNextFrame(1, "0xAAAAAAAA" + "00".repeat(32));
    TraceFrame instructionInA = createInstructionFrame("ADD", 1);

    // method-A calls method-B
    TraceFrame callB = createCallFrame("CALL", 1);
    TraceFrame insideB = createNextFrame(2, "0xBBBBBBBB" + "00".repeat(64));
    TraceFrame instructionInB = createInstructionFrame("MUL", 2);

    // method-B calls method-C
    TraceFrame callC = createCallFrame("CALL", 2);
    TraceFrame insideC = createNextFrame(3, "0xCCCCCCCC" + "00".repeat(96));
    TraceFrame instructionInC = createInstructionFrame("SSTORE", 3);

    when(mockTrace.getTraceFrames())
        .thenReturn(
            List.of(
                callA,
                insideA,
                instructionInA, // method-A
                callB,
                insideB,
                instructionInB, // method-B
                callC,
                insideC,
                instructionInC)); // method-C
    ProtocolSpec mockSpec = createMockProtocolSpec();

    // When
    FourByteTracerResult result = FourByteTracerResultConverter.convert(mockTrace, mockSpec);

    // Then
    assertThat(result).isNotNull();
    Map<String, Integer> counts = result.getSelectorCounts();
    assertThat(counts).hasSize(3);
    assertThat(counts).containsEntry("0xaaaaaaaa-32", 1); // method-A called once
    assertThat(counts).containsEntry("0xbbbbbbbb-64", 1); // method-B called once
    assertThat(counts).containsEntry("0xcccccccc-96", 1); // method-C called once
  }

  @Test
  @DisplayName("should count multiple occurrences of same selector in nested calls")
  void shouldCountMultipleOccurrencesInNestedCalls() {
    // Given: method-A calls method-B, which calls method-C twice
    TransactionTrace mockTrace = createMockTraceWithContractCreation();

    // Initial call to method-A
    TraceFrame callA = createCallFrame("CALL", 0);
    TraceFrame insideA = createNextFrame(1, "0xAAAAAAAA" + "00".repeat(32));

    // method-A calls method-B
    TraceFrame callB = createCallFrame("CALL", 1);
    TraceFrame insideB = createNextFrame(2, "0xBBBBBBBB" + "00".repeat(64));
    TraceFrame instructionInB1 = createInstructionFrame("MUL", 2);

    // method-B calls method-C (FIRST TIME)
    TraceFrame callC1 = createCallFrame("CALL", 2);
    TraceFrame insideC1 = createNextFrame(3, "0xCCCCCCCC" + "00".repeat(96));
    TraceFrame instructionInC1 = createInstructionFrame("SSTORE", 3);

    // Back in method-B, more instructions
    TraceFrame instructionInB2 = createInstructionFrame("SUB", 2);

    // method-B calls method-C (SECOND TIME)
    TraceFrame callC2 = createCallFrame("CALL", 2);
    TraceFrame insideC2 = createNextFrame(3, "0xCCCCCCCC" + "00".repeat(96));
    TraceFrame instructionInC2 = createInstructionFrame("SLOAD", 3);

    when(mockTrace.getTraceFrames())
        .thenReturn(
            List.of(
                callA,
                insideA,
                callB,
                insideB,
                instructionInB1, // method-B
                callC1,
                insideC1,
                instructionInC1, // method-C (first call)
                instructionInB2, // back in method-B
                callC2,
                insideC2,
                instructionInC2)); // method-C (second call)
    ProtocolSpec mockSpec = createMockProtocolSpec();

    // When
    FourByteTracerResult result = FourByteTracerResultConverter.convert(mockTrace, mockSpec);

    // Then
    assertThat(result).isNotNull();
    Map<String, Integer> counts = result.getSelectorCounts();
    assertThat(counts).hasSize(3);
    assertThat(counts).containsEntry("0xaaaaaaaa-32", 1); // method-A called once
    assertThat(counts).containsEntry("0xbbbbbbbb-64", 1); // method-B called once
    assertThat(counts).containsEntry("0xcccccccc-96", 2); // method-C called TWICE
  }

  @Test
  @DisplayName("should capture function selector from initial transaction")
  void shouldCaptureFunctionSelectorFromInitialTransaction() {
    // Given: A transaction calling a contract function (no internal calls)
    Address contractAddress = Address.fromHexString("0x1234567890123456789012345678901234567890");
    Bytes inputData = Bytes.fromHexString("0x55241077" + "00".repeat(32)); // setValue(uint256)

    TransactionTrace mockTrace = createMockTraceWithContractCall(contractAddress, inputData);
    when(mockTrace.getTraceFrames()).thenReturn(List.of()); // No internal calls
    ProtocolSpec mockSpec = createMockProtocolSpec();

    // When
    FourByteTracerResult result = FourByteTracerResultConverter.convert(mockTrace, mockSpec);

    // Then
    assertThat(result).isNotNull();
    Map<String, Integer> counts = result.getSelectorCounts();
    assertThat(counts).hasSize(1);
    assertThat(counts).containsEntry("0x55241077-32", 1);
  }

  @Test
  @DisplayName("should capture both initial transaction and internal calls")
  void shouldCaptureBothInitialTransactionAndInternalCalls() {
    // Given: Initial transaction calls contract, which then makes an internal call
    Address contractAddress = Address.fromHexString("0x1234567890123456789012345678901234567890");
    Bytes initialInput = Bytes.fromHexString("0xaaaaaaaa" + "00".repeat(32));

    TransactionTrace mockTrace = createMockTraceWithContractCall(contractAddress, initialInput);

    // Internal CALL
    TraceFrame callFrame = createCallFrame("CALL", 1);
    TraceFrame nextFrame = createNextFrame(2, "0xbbbbbbbb" + "00".repeat(64));

    when(mockTrace.getTraceFrames()).thenReturn(List.of(callFrame, nextFrame));
    ProtocolSpec mockSpec = createMockProtocolSpec();

    // When
    FourByteTracerResult result = FourByteTracerResultConverter.convert(mockTrace, mockSpec);

    // Then
    assertThat(result).isNotNull();
    Map<String, Integer> counts = result.getSelectorCounts();
    assertThat(counts).hasSize(2);
    assertThat(counts).containsEntry("0xaaaaaaaa-32", 1); // Initial transaction
    assertThat(counts).containsEntry("0xbbbbbbbb-64", 1); // Internal call
  }

  @Test
  @DisplayName("should skip initial transaction if it's a contract creation")
  void shouldSkipInitialTransactionIfContractCreation() {
    // Given: Contract creation transaction
    TransactionTrace mockTrace = createMockTraceWithContractCreation();
    when(mockTrace.getTraceFrames()).thenReturn(List.of());
    ProtocolSpec mockSpec = createMockProtocolSpec();

    // When
    FourByteTracerResult result = FourByteTracerResultConverter.convert(mockTrace, mockSpec);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getSelectorCounts()).isEmpty();
  }

  @Test
  @DisplayName("should skip initial transaction with less than 4 bytes")
  void shouldSkipInitialTransactionWithLessThanFourBytes() {
    // Given: Transaction with only 2 bytes of input
    Address contractAddress = Address.fromHexString("0x1234567890123456789012345678901234567890");
    Bytes inputData = Bytes.fromHexString("0x1234");

    TransactionTrace mockTrace = createMockTraceWithContractCall(contractAddress, inputData);
    when(mockTrace.getTraceFrames()).thenReturn(List.of());
    ProtocolSpec mockSpec = createMockProtocolSpec();

    // When
    FourByteTracerResult result = FourByteTracerResultConverter.convert(mockTrace, mockSpec);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getSelectorCounts()).isEmpty();
  }

  @Test
  @DisplayName("should skip initial transaction to precompiled contract")
  void shouldSkipInitialTransactionToPrecompiledContract() {
    // Given: Transaction calling precompile (address 0x01)
    Address precompileAddress = Address.fromHexString("0x0000000000000000000000000000000000000001");
    Bytes inputData = Bytes.fromHexString("0x12345678" + "00".repeat(32));

    TransactionTrace mockTrace = createMockTraceWithContractCall(precompileAddress, inputData);
    when(mockTrace.getTraceFrames()).thenReturn(List.of());

    // Mock ProtocolSpec to recognize the precompile address
    ProtocolSpec mockSpec = mock(ProtocolSpec.class);
    PrecompileContractRegistry mockRegistry = mock(PrecompileContractRegistry.class);
    when(mockSpec.getPrecompileContractRegistry()).thenReturn(mockRegistry);
    // Return a non-null object for the precompile address to indicate it's a precompile
    when(mockRegistry.get(precompileAddress)).thenReturn(mock(PrecompiledContract.class));
    // Return null for all other addresses
    when(mockRegistry.get(
            org.mockito.ArgumentMatchers.argThat(addr -> !addr.equals(precompileAddress))))
        .thenReturn(null);

    // When
    FourByteTracerResult result = FourByteTracerResultConverter.convert(mockTrace, mockSpec);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getSelectorCounts()).isEmpty();
  }
}
