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

import org.hyperledger.besu.crypto.Hash;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.apache.tuweni.bytes.Bytes;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Thread)
@OutputTimeUnit(value = TimeUnit.MILLISECONDS)
public class Hashing20kKeccak256Benchmark {

  @Param({"32", "64", "128", "256", "512"})
  private String inputSize;

  public Bytes bytes;

  @Setup
  public void setUp() {
    final Random random = new Random();
    final byte[] byteArray = new byte[Integer.parseInt(inputSize)];
    random.nextBytes(byteArray);
    bytes = Bytes.wrap(byteArray);
  }

  @Benchmark
  public void executeOperation() {
    for (int i = 0; i < 20000; i++) {
      Hash.keccak256(bytes);
    }
  }
}
