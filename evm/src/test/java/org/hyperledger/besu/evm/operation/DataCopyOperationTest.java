/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.evm.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.MainnetEVMs;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.PragueGasCalculator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.testutils.TestMessageFrameBuilder;

import java.util.Arrays;
import java.util.Collection;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class DataCopyOperationTest {

  static EVM evm = MainnetEVMs.osaka(EvmConfiguration.DEFAULT);

  static Collection<Object[]> datacopyTestVector() {
    return Arrays.asList(
        new Object[][] {
          {
            "Copy after, no overlap",
            Bytes.fromHexString("0123456789abcdef000000000000000000000000000000000000000000000000"),
            32,
            0,
            8,
            Bytes.fromHexString(
                "00000000000000000000000000000000000000000000000000000000000000000123456789abcdef"),
            12L
          },
          {
            "copy past data limit",
            Bytes.EMPTY,
            0,
            24,
            16,
            Bytes.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000000"),
            9L
          },
          {
            "copy from initialized + uninitialized memory",
            Bytes.fromHexString(
                "0x0000000000000000000000000000000000000000000000000123456789abcdef"),
            64,
            24,
            16,
            Bytes.fromHexString(
                "0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000123456789abcdef000000000000000000000000000000000000000000000000"),
            15L
          },
          {
            "overlapping src < dst",
            Bytes.fromHexString(
                "0x0123456789abcdef000000000000000000000000000000000000000000000000"),
            4,
            0,
            8,
            Bytes.fromHexString(
                "0x000000000123456789abcdef0000000000000000000000000000000000000000"),
            9L
          },
          {
            "overlapping src > dst",
            Bytes.fromHexString(
                "0x00112233445566778899aabbccddeeff00000000000000000000000000000000"),
            0,
            4,
            8,
            Bytes.fromHexString(
                "0x445566778899aabb000000000000000000000000000000000000000000000000"),
            9L
          },
          {
            "overlapping src == dst",
            Bytes.fromHexString(
                "0x00112233445566778899aabbccddeeff00000000000000000000000000000000"),
            4,
            4,
            8,
            Bytes.fromHexString(
                "0x00000000445566778899aabb0000000000000000000000000000000000000000"),
            9L
          },
          {"large dst offset", Bytes.EMPTY, 0x8000000000000010L, 4, 8, Bytes.EMPTY, 8796294610953L},
          {
            "large src offset",
            Bytes.EMPTY,
            4,
            0x8000000000000010L,
            8,
            Bytes.fromHexString(
                "0x0000000000000000000000000000000000000000000000000000000000000000"),
            9L
          },
          {"large len", Bytes.EMPTY, 4, 4, 0x8000000000000010L, Bytes.EMPTY, 8796093284361L}
        });
  }

  @SuppressWarnings("unused")
  @ParameterizedTest(name = "{0}")
  @MethodSource("datacopyTestVector")
  void testMCopy(
      final String name,
      final Bytes data,
      final long dst,
      final long src,
      final long len,
      final Bytes expected,
      final long gasCost) {
    DataCopyOperation subject = new DataCopyOperation(new PragueGasCalculator());
    String eofCode =
        "0xef0001010004020001001d04%04x000080000367%016x67%016x67%016xd300%s"
            .formatted(data.size(), dst, src, len, data.toUnprefixedHexString());
    Code code = evm.getCodeUncached(Bytes.fromHexString(eofCode));
    assumeTrue(code.isValid());

    MessageFrame frame =
        new TestMessageFrameBuilder()
            .pushStackItem(Bytes.ofUnsignedLong(len))
            .pushStackItem(Bytes.ofUnsignedLong(src))
            .pushStackItem(Bytes.ofUnsignedLong(dst))
            .initialGas(10_000_000)
            .code(code)
            .build();

    Operation.OperationResult result = subject.execute(frame, evm);

    assertThat(frame.memoryWordSize()).isEqualTo((expected.size() + 31) / 32);
    assertThat(frame.readMemory(0, expected.size())).isEqualTo(expected);
    assertThat(result.getGasCost()).isEqualTo(gasCost);
  }

  @Test
  void legacyCallFails() {
    DataCopyOperation subject = new DataCopyOperation(new PragueGasCalculator());
    Code code = evm.getCodeUncached(Bytes.fromHexString("0x600460046004d3"));
    assumeTrue(code.isValid());

    MessageFrame frame =
        new TestMessageFrameBuilder()
            .pushStackItem(Bytes.ofUnsignedLong(4))
            .pushStackItem(Bytes.ofUnsignedLong(4))
            .pushStackItem(Bytes.ofUnsignedLong(4))
            .initialGas(10_000_000)
            .code(code)
            .pc(6)
            .build();

    Operation.OperationResult result = subject.execute(frame, evm);
    assertThat(result.getGasCost()).isZero();
    assertThat(result.getHaltReason()).isEqualTo(ExceptionalHaltReason.INVALID_OPERATION);
  }
}
