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
package org.hyperledger.besu.ethereum.rlp;

import org.hyperledger.besu.ethereum.rlp.util.RLPTestUtil;

import java.util.ArrayList;
import java.util.List;

import org.apache.tuweni.bytes.Bytes;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Benchmark)
public class RLPBench {

  private static Object generate(final int depth, final int width, final int size) {
    final byte[] bytes = new byte[size];
    for (int i = 0; i < size; i++) {
      bytes[i] = (byte) ((100 + i) * i);
    }
    return generateAndRecurse(Bytes.wrap(bytes), depth, width);
  }

  private static Object generateAndRecurse(final Bytes value, final int depth, final int width) {
    if (depth == 0) {
      return value;
    }

    final List<Object> l = new ArrayList<>(width);
    for (int i = 0; i < width; i++) {
      l.add(i % 3 == 0 ? value : generateAndRecurse(value, depth - 1, width));
    }
    return l;
  }

  @Param({"1", "3", "8"})
  public int depth;

  @Param({"4", "8"})
  public int width;

  @Param({"4", "100"})
  public int size;

  volatile Object toEncode;
  volatile Bytes toDecode;

  @Setup(Level.Trial)
  public void prepare() {
    toEncode = generate(depth, width, size);
    toDecode = RLPTestUtil.encode(toEncode);
  }

  @Benchmark
  public Bytes getBenchmarkEncoding() {
    return RLPTestUtil.encode(toEncode);
  }

  @Benchmark
  public Object getBenchmarkDecoding() {
    return RLPTestUtil.decode(toDecode);
  }
}
