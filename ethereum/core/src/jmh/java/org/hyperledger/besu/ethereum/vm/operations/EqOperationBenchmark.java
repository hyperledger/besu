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
package org.hyperledger.besu.ethereum.vm.operations;

import org.hyperledger.besu.evm.UInt256;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.EqOperation;
import org.hyperledger.besu.evm.operation.Operation;

import java.util.Arrays;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.MutableBytes;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;

public class EqOperationBenchmark extends BinaryOperationBenchmark {

  // public because of jmh code generator
  public enum MODE {
    EMPTY_INPUTS,
    ONE_EMPTY_INPUT,
    BYTES1_EQUAL,
    BYTES1_NOT_EQUAL,
    BYTES1_EQUAL_WITH_ZEROS,
    BYTES1_NOT_EQUAL_WITH_ZEROS,
    BYTES16_EQUAL,
    BYTES16_NOT_EQUAL,
    BYTES16_EQUAL_WITH_ZEROS,
    BYTES16_NOT_EQUAL_WITH_ZEROS,
    BYTES32_NOT_EQUAL_MOST_SIGNIFICANT,
    BYTES32_NOT_EQUAL_LEAST_SIGNIFICANT,
    BYTES32_RANDOM,
    BYTES30_BYTES16_RANDOM,
    BYTES32_EQUAL_BYTE_REPEAT
  }

  @Param private MODE mode;

  @Setup
  @Override
  public void setUp() {
    frame = BenchmarkHelper.createMessageCallFrame();

    // Use temporary Bytes[] arrays for the complex scenario generation logic,
    // then convert to UInt256[] at the end.
    Bytes[] tmpA = new Bytes[SAMPLE_SIZE];
    Bytes[] tmpB = new Bytes[SAMPLE_SIZE];
    switch (mode) {
      case EMPTY_INPUTS:
        Arrays.fill(tmpA, Bytes.EMPTY);
        Arrays.fill(tmpB, Bytes.EMPTY);
        break;
      case ONE_EMPTY_INPUT:
        Arrays.fill(tmpA, Bytes.EMPTY);
        BenchmarkHelper.fillPool(tmpB, () -> 16);
        tmpB = Arrays.stream(tmpB).map(Bytes32::leftPad).toArray(Bytes[]::new);
        break;
      case BYTES1_EQUAL:
        BenchmarkHelper.fillPool(tmpA, () -> 1);
        tmpB =
            Arrays.stream(tmpA)
                .map(bytes -> Bytes.wrap(bytes.toArrayUnsafe()))
                .toArray(Bytes[]::new);
        break;
      case BYTES1_NOT_EQUAL:
        BenchmarkHelper.fillPools(
            tmpA, tmpB, () -> 1, () -> 1, (value1, value2) -> !value1.equals(value2));
        break;
      case BYTES1_EQUAL_WITH_ZEROS:
        BenchmarkHelper.fillPool(tmpA, () -> 1);
        tmpA = Arrays.stream(tmpA).map(Bytes32::leftPad).toArray(Bytes[]::new);
        tmpB =
            Arrays.stream(tmpA)
                .map(bytes -> Bytes.wrap(bytes.toArrayUnsafe()))
                .toArray(Bytes[]::new);
        break;
      case BYTES1_NOT_EQUAL_WITH_ZEROS:
        BenchmarkHelper.fillPools(
            tmpA, tmpB, () -> 1, () -> 1, (value1, value2) -> !value1.equals(value2));
        tmpA = Arrays.stream(tmpA).map(Bytes32::leftPad).toArray(Bytes[]::new);
        tmpB = Arrays.stream(tmpB).map(Bytes32::leftPad).toArray(Bytes[]::new);
        break;
      case BYTES16_EQUAL:
        BenchmarkHelper.fillPool(tmpA, () -> 16);
        tmpB =
            Arrays.stream(tmpA)
                .map(bytes -> Bytes.wrap(bytes.toArrayUnsafe()))
                .toArray(Bytes[]::new);
        break;
      case BYTES16_NOT_EQUAL:
        BenchmarkHelper.fillPools(
            tmpA, tmpB, () -> 16, () -> 16, (value1, value2) -> !value1.equals(value2));
        break;
      case BYTES16_EQUAL_WITH_ZEROS:
        BenchmarkHelper.fillPool(tmpA, () -> 16);
        tmpA = Arrays.stream(tmpA).map(Bytes32::leftPad).toArray(Bytes[]::new);
        tmpB =
            Arrays.stream(tmpA)
                .map(bytes -> Bytes.wrap(bytes.toArrayUnsafe()))
                .toArray(Bytes[]::new);
        break;
      case BYTES16_NOT_EQUAL_WITH_ZEROS:
        BenchmarkHelper.fillPools(
            tmpA, tmpB, () -> 16, () -> 16, (value1, value2) -> !value1.equals(value2));
        tmpA = Arrays.stream(tmpA).map(Bytes32::leftPad).toArray(Bytes[]::new);
        tmpB = Arrays.stream(tmpB).map(Bytes32::leftPad).toArray(Bytes[]::new);
        break;
      case BYTES32_NOT_EQUAL_MOST_SIGNIFICANT:
        BenchmarkHelper.fillPool(tmpA, () -> 32, value -> value.get(0) != 0);
        tmpB =
            Arrays.stream(tmpA)
                .map(
                    bytes -> {
                      final MutableBytes mutableBytes = bytes.mutableCopy();
                      byte firstByte = bytes.get(0);
                      mutableBytes.set(0, (byte) (firstByte > 0 ? firstByte + 1 : firstByte - 1));
                      return mutableBytes;
                    })
                .toArray(Bytes[]::new);
        break;
      case BYTES32_NOT_EQUAL_LEAST_SIGNIFICANT:
        BenchmarkHelper.fillPool(tmpA, () -> 32, value -> value.get(0) != 0);
        tmpB =
            Arrays.stream(tmpA)
                .map(
                    bytes -> {
                      final MutableBytes mutableBytes = bytes.mutableCopy();
                      byte lastByte = bytes.get(bytes.size() - 1);
                      mutableBytes.set(
                          bytes.size() - 1, (byte) (lastByte > 0 ? lastByte + 1 : lastByte - 1));
                      return mutableBytes;
                    })
                .toArray(Bytes[]::new);
        break;
      case BYTES32_RANDOM:
        BenchmarkHelper.fillPool(tmpA, () -> 32, value -> value.get(0) != 0);
        BenchmarkHelper.fillPool(tmpB, () -> 32, value -> value.get(0) != 0);
        break;
      case BYTES30_BYTES16_RANDOM:
        BenchmarkHelper.fillPool(tmpA, () -> 30, value -> value.get(0) != 0);
        BenchmarkHelper.fillPool(tmpB, () -> 16, value -> value.get(0) != 0);
        break;
      case BYTES32_EQUAL_BYTE_REPEAT:
        BenchmarkHelper.fillPool(tmpA, () -> 1);
        tmpA =
            Arrays.stream(tmpA)
                .map(
                    bytes -> {
                      final byte[] newPool = new byte[32];
                      Arrays.fill(newPool, bytes.get(0));
                      return Bytes.wrap(newPool);
                    })
                .toArray(Bytes[]::new);
        tmpB =
            Arrays.stream(tmpA)
                .map(bytes -> Bytes.wrap(bytes.toArrayUnsafe()))
                .toArray(Bytes[]::new);
    }

    // Convert temporary Bytes[] to UInt256[]
    aPool = new UInt256[SAMPLE_SIZE];
    bPool = new UInt256[SAMPLE_SIZE];
    for (int i = 0; i < SAMPLE_SIZE; i++) {
      aPool[i] = bytesToUInt256(tmpA[i]);
      bPool[i] = bytesToUInt256(tmpB[i]);
    }
    index = 0;
  }

  private static UInt256 bytesToUInt256(final Bytes bytes) {
    if (bytes.isEmpty()) {
      return UInt256.ZERO;
    }
    final byte[] padded = new byte[32];
    final byte[] raw = bytes.toArrayUnsafe();
    System.arraycopy(raw, 0, padded, 32 - raw.length, raw.length);
    return UInt256.fromBytesBE(padded);
  }

  @Override
  protected Operation.OperationResult invoke(final MessageFrame frame) {
    return EqOperation.staticOperation(frame);
  }
}
