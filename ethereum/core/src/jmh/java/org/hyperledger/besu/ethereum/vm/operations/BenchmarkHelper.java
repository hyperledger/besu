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

import static org.mockito.Mockito.mock;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.code.CodeV0;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.Random;
import java.util.function.Supplier;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

public class BenchmarkHelper {
  public static MessageFrame createMessageCallFrame() {
    return MessageFrame.builder()
        .worldUpdater(mock(WorldUpdater.class))
        .originator(Address.ZERO)
        .gasPrice(Wei.ONE)
        .blobGasPrice(Wei.ONE)
        .blockValues(mock(BlockValues.class))
        .miningBeneficiary(Address.ZERO)
        .blockHashLookup((__, ___) -> Hash.ZERO)
        .type(MessageFrame.Type.MESSAGE_CALL)
        .initialGas(Long.MAX_VALUE)
        .address(Address.ZERO)
        .contract(Address.ZERO)
        .inputData(Bytes32.ZERO)
        .sender(Address.ZERO)
        .value(Wei.ZERO)
        .apparentValue(Wei.ZERO)
        .code(CodeV0.EMPTY_CODE)
        .completer(__ -> {})
        .build();
  }

  public static MessageFrame createMessageCallFrameWithCallData(final Bytes callData) {
    return MessageFrame.builder()
        .worldUpdater(mock(WorldUpdater.class))
        .originator(Address.ZERO)
        .gasPrice(Wei.ONE)
        .blobGasPrice(Wei.ONE)
        .blockValues(mock(BlockValues.class))
        .miningBeneficiary(Address.ZERO)
        .blockHashLookup((__, ___) -> Hash.ZERO)
        .type(MessageFrame.Type.MESSAGE_CALL)
        .initialGas(Long.MAX_VALUE)
        .address(Address.ZERO)
        .contract(Address.ZERO)
        .inputData(callData)
        .sender(Address.ZERO)
        .value(Wei.ZERO)
        .apparentValue(Wei.ZERO)
        .code(CodeV0.EMPTY_CODE)
        .completer(__ -> {})
        .build();
  }

  /**
   * Fills an array with random 32-byte values.
   *
   * @param pool the destination array
   */
  public static void fillPool(final Bytes[] pool) {
    final Random random = new Random();
    fillPool(pool, () -> 1 + random.nextInt(32)); //  [1, 32]
  }

  public static void fillPool(final Bytes[] pool, final Supplier<Integer> sizeSupplier) {
    final Random random = new Random();
    for (int i = 0; i < pool.length; i++) {
      final byte[] a = new byte[sizeSupplier.get()];
      random.nextBytes(a);
      pool[i] = Bytes.wrap(a);
    }
  }

  static Bytes createCallData(final int size, final boolean nonZero) {
    byte[] data = new byte[size];
    if (nonZero) {
      for (int i = 0; i < size; i++) {
        data[i] = (byte) (i % 256);
      }
    }
    return Bytes.wrap(data);
  }

  static void fillPoolsForCallData(
      final Bytes[] sizePool,
      final Bytes[] destOffsetPool,
      final Bytes[] srcOffsetPool,
      final int dataSize,
      final boolean fixedSrcDst) {
    for (int i = 0; i < sizePool.length; i++) {
      sizePool[i] = Bytes.wrap(UInt256.valueOf(dataSize));

      if (fixedSrcDst) {
        destOffsetPool[i] = Bytes.wrap(UInt256.valueOf(0));
        srcOffsetPool[i] = Bytes.wrap(UInt256.valueOf(0));
      } else {
        destOffsetPool[i] = Bytes.wrap(UInt256.valueOf((i * 32) % 1024));
        srcOffsetPool[i] = Bytes.wrap(UInt256.valueOf(i % Math.max(1, dataSize)));
      }
    }
  }
}
