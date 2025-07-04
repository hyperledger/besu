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
import org.hyperledger.besu.evm.operation.AddOperation;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@BenchmarkMode(Mode.Throughput)
@State(Scope.Thread)
@OutputTimeUnit(value = TimeUnit.MILLISECONDS)
public class AddOperationBenchmark {

  private static final int SAMPLE_SIZE = 1000;

  private Bytes[] aPool;
  private Bytes[] bPool;

  private int index;

  private MessageFrame frame;

  @Setup(Level.Trial)
  public void setUp() {
    frame =
        MessageFrame.builder()
            .worldUpdater(mock(WorldUpdater.class))
            .originator(Address.ZERO)
            .gasPrice(Wei.ONE)
            .blobGasPrice(Wei.ONE)
            .blockValues(mock(BlockValues.class))
            .miningBeneficiary(Address.ZERO)
            .blockHashLookup((__, ___) -> Hash.ZERO)
            .type(MessageFrame.Type.MESSAGE_CALL)
            .initialGas(1_000_000)
            .address(Address.ZERO)
            .contract(Address.ZERO)
            .inputData(Bytes32.ZERO)
            .sender(Address.ZERO)
            .value(Wei.ZERO)
            .apparentValue(Wei.ZERO)
            .code(CodeV0.EMPTY_CODE)
            .completer(__ -> {})
            .build();

    ThreadLocalRandom random = ThreadLocalRandom.current();
    aPool = new Bytes[SAMPLE_SIZE];
    bPool = new Bytes[SAMPLE_SIZE];
    for (int i = 0; i < SAMPLE_SIZE; i++) {
      final byte[] a = new byte[32];
      final byte[] b = new byte[32];
      random.nextBytes(a);
      random.nextBytes(b);
      aPool[i] = Bytes.wrap(a);
      bPool[i] = Bytes.wrap(b);
    }

    index = 0;
  }

  @Benchmark
  public Operation.OperationResult addBenchmark() {
    final Bytes aBytes = aPool[index];
    final Bytes bBytes = bPool[index];
    index = (index + 1) % SAMPLE_SIZE;

    frame.pushStackItem(bBytes);
    frame.pushStackItem(aBytes);
    final Operation.OperationResult result = AddOperation.staticOperation(frame);
    frame.popStackItem();
    return result;
  }
}
