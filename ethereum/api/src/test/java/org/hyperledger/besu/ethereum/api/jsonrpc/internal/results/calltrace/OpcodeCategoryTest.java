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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class OpcodeCategoryTest {

  // Category Classification Tests

  @ParameterizedTest
  @ValueSource(strings = {"CALL", "CALLCODE", "DELEGATECALL", "STATICCALL"})
  @DisplayName("Should classify call operations correctly")
  void shouldClassifyCallOperations(final String opcode) {
    assertThat(OpcodeCategory.of(opcode)).isEqualTo(OpcodeCategory.CALL);
  }

  @ParameterizedTest
  @ValueSource(strings = {"CREATE", "CREATE2"})
  @DisplayName("Should classify create operations correctly")
  void shouldClassifyCreateOperations(final String opcode) {
    assertThat(OpcodeCategory.of(opcode)).isEqualTo(OpcodeCategory.CREATE);
  }

  @Test
  @DisplayName("Should classify RETURN correctly")
  void shouldClassifyReturn() {
    assertThat(OpcodeCategory.of("RETURN")).isEqualTo(OpcodeCategory.RETURN);
  }

  @Test
  @DisplayName("Should classify REVERT correctly")
  void shouldClassifyRevert() {
    assertThat(OpcodeCategory.of("REVERT")).isEqualTo(OpcodeCategory.REVERT);
  }

  @Test
  @DisplayName("Should classify STOP correctly")
  void shouldClassifyStop() {
    assertThat(OpcodeCategory.of("STOP")).isEqualTo(OpcodeCategory.HALT);
  }

  @Test
  @DisplayName("Should classify SELFDESTRUCT correctly")
  void shouldClassifySelfDestruct() {
    assertThat(OpcodeCategory.of("SELFDESTRUCT")).isEqualTo(OpcodeCategory.SELFDESTRUCT);
  }

  @ParameterizedTest
  @ValueSource(strings = {"ADD", "SUB", "MUL", "PUSH1", "JUMP", "SSTORE", "UNKNOWN"})
  @DisplayName("Should classify unknown opcodes as OTHER")
  void shouldClassifyUnknownAsOther(final String opcode) {
    assertThat(OpcodeCategory.of(opcode)).isEqualTo(OpcodeCategory.OTHER);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @DisplayName("Should handle null and empty strings")
  void shouldHandleNullAndEmpty(final String opcode) {
    assertThat(OpcodeCategory.of(opcode)).isEqualTo(OpcodeCategory.OTHER);
  }

  @Test
  @DisplayName("Should be case-sensitive")
  void shouldBeCaseSensitive() {
    assertThat(OpcodeCategory.of("call")).isEqualTo(OpcodeCategory.OTHER);
    assertThat(OpcodeCategory.of("Call")).isEqualTo(OpcodeCategory.OTHER);
    assertThat(OpcodeCategory.of("CALL")).isEqualTo(OpcodeCategory.CALL);
  }

  // Static Helper Method Tests

  @ParameterizedTest
  @ValueSource(strings = {"CALL", "CALLCODE", "DELEGATECALL", "STATICCALL"})
  @DisplayName("isCallOp should return true for call operations")
  void isCallOpShouldReturnTrue(final String opcode) {
    assertThat(OpcodeCategory.isCallOp(opcode)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"CREATE", "RETURN", "REVERT", "STOP", "ADD", ""})
  @DisplayName("isCallOp should return false for non-call operations")
  void isCallOpShouldReturnFalse(final String opcode) {
    assertThat(OpcodeCategory.isCallOp(opcode)).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"CREATE", "CREATE2"})
  @DisplayName("isCreateOp should return true for create operations")
  void isCreateOpShouldReturnTrue(final String opcode) {
    assertThat(OpcodeCategory.isCreateOp(opcode)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"CALL", "RETURN", "REVERT", "STOP", "ADD", ""})
  @DisplayName("isCreateOp should return false for non-create operations")
  void isCreateOpShouldReturnFalse(final String opcode) {
    assertThat(OpcodeCategory.isCreateOp(opcode)).isFalse();
  }

  @Test
  @DisplayName("isReturnOp should work correctly")
  void isReturnOpShouldWork() {
    assertThat(OpcodeCategory.isReturnOp("RETURN")).isTrue();
    assertThat(OpcodeCategory.isReturnOp("REVERT")).isFalse();
    assertThat(OpcodeCategory.isReturnOp("STOP")).isFalse();
  }

  @Test
  @DisplayName("isRevertOp should work correctly")
  void isRevertOpShouldWork() {
    assertThat(OpcodeCategory.isRevertOp("REVERT")).isTrue();
    assertThat(OpcodeCategory.isRevertOp("RETURN")).isFalse();
    assertThat(OpcodeCategory.isRevertOp("STOP")).isFalse();
  }

  @Test
  @DisplayName("isHaltOp should work correctly")
  void isHaltOpShouldWork() {
    assertThat(OpcodeCategory.isHaltOp("STOP")).isTrue();
    assertThat(OpcodeCategory.isHaltOp("RETURN")).isFalse();
    assertThat(OpcodeCategory.isHaltOp("REVERT")).isFalse();
  }

  @Test
  @DisplayName("isSelfDestructOp should work correctly")
  void isSelfDestructOpShouldWork() {
    assertThat(OpcodeCategory.isSelfDestructOp("SELFDESTRUCT")).isTrue();
    assertThat(OpcodeCategory.isSelfDestructOp("RETURN")).isFalse();
    assertThat(OpcodeCategory.isSelfDestructOp("STOP")).isFalse();
  }

  @Test
  @DisplayName("Static methods should handle null safely")
  void staticMethodsShouldHandleNull() {
    assertThat(OpcodeCategory.isCallOp(null)).isFalse();
    assertThat(OpcodeCategory.isCreateOp(null)).isFalse();
    assertThat(OpcodeCategory.isReturnOp(null)).isFalse();
    assertThat(OpcodeCategory.isRevertOp(null)).isFalse();
    assertThat(OpcodeCategory.isHaltOp(null)).isFalse();
    assertThat(OpcodeCategory.isSelfDestructOp(null)).isFalse();
  }

  // Instance Method Tests

  @Test
  @DisplayName("isCall should return true only for CALL category")
  void isCallShouldWork() {
    assertThat(OpcodeCategory.CALL.isCall()).isTrue();
    assertThat(OpcodeCategory.CREATE.isCall()).isFalse();
    assertThat(OpcodeCategory.RETURN.isCall()).isFalse();
    assertThat(OpcodeCategory.OTHER.isCall()).isFalse();
  }

  @Test
  @DisplayName("isCreate should return true only for CREATE category")
  void isCreateShouldWork() {
    assertThat(OpcodeCategory.CREATE.isCreate()).isTrue();
    assertThat(OpcodeCategory.CALL.isCreate()).isFalse();
    assertThat(OpcodeCategory.RETURN.isCreate()).isFalse();
    assertThat(OpcodeCategory.OTHER.isCreate()).isFalse();
  }

  @ParameterizedTest
  @CsvSource({
    "RETURN, true",
    "REVERT, true",
    "HALT, true",
    "CALL, false",
    "CREATE, false",
    "SELFDESTRUCT, false",
    "OTHER, false"
  })
  @DisplayName("isTerminating should identify terminating operations")
  void isTerminatingShouldWork(final OpcodeCategory category, final boolean expected) {
    assertThat(category.isTerminating()).isEqualTo(expected);
  }

  // Edge Case Tests

  @Test
  @DisplayName("Should handle whitespace in opcode string")
  void shouldNotTrimWhitespace() {
    assertThat(OpcodeCategory.of(" CALL")).isEqualTo(OpcodeCategory.OTHER);
    assertThat(OpcodeCategory.of("CALL ")).isEqualTo(OpcodeCategory.OTHER);
    assertThat(OpcodeCategory.of(" CALL ")).isEqualTo(OpcodeCategory.OTHER);
  }

  @Test
  @DisplayName("Should handle similar but different opcodes")
  void shouldHandleSimilarOpcodes() {
    assertThat(OpcodeCategory.of("CALLER")).isEqualTo(OpcodeCategory.OTHER);
    assertThat(OpcodeCategory.of("CALLVALUE")).isEqualTo(OpcodeCategory.OTHER);
    assertThat(OpcodeCategory.of("RETURNDATASIZE")).isEqualTo(OpcodeCategory.OTHER);
    assertThat(OpcodeCategory.of("RETURNDATACOPY")).isEqualTo(OpcodeCategory.OTHER);
  }
}
