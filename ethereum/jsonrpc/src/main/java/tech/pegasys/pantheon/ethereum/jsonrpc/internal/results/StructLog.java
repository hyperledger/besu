/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.jsonrpc.internal.results;

import tech.pegasys.pantheon.ethereum.core.Gas;
import tech.pegasys.pantheon.ethereum.debug.TraceFrame;
import tech.pegasys.pantheon.util.bytes.Bytes32s;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"pc", "op", "gas", "gasCost", "depth", "stack", "memory", "storage"})
public class StructLog {

  private final int depth;
  private final long gas;
  private final long gasCost;
  private final String[] memory;
  private final String op;
  private final int pc;
  private final String[] stack;
  private final Object storage;
  private final String reason;

  public StructLog(final TraceFrame traceFrame) {
    depth = traceFrame.getDepth() + 1;
    gas = traceFrame.getGasRemaining().toLong();
    gasCost = traceFrame.getGasCost().map(Gas::toLong).orElse(0L);
    memory =
        traceFrame
            .getMemory()
            .map(a -> Arrays.stream(a).map(Bytes32s::unprefixedHexString).toArray(String[]::new))
            .orElse(null);
    op = traceFrame.getOpcode();
    pc = traceFrame.getPc();
    stack =
        traceFrame
            .getStack()
            .map(a -> Arrays.stream(a).map(Bytes32s::unprefixedHexString).toArray(String[]::new))
            .orElse(null);
    storage = traceFrame.getStorage().map(StructLog::formatStorage).orElse(null);
    reason = traceFrame.getRevertReason().map(BytesValue::toUnprefixedString).orElse(null);
  }

  private static Map<String, String> formatStorage(final Map<UInt256, UInt256> storage) {
    final Map<String, String> formattedStorage = new TreeMap<>();
    storage.forEach(
        (key, value) ->
            formattedStorage.put(key.toUnprefixedHexString(), value.toUnprefixedHexString()));
    return formattedStorage;
  }

  @JsonGetter("depth")
  public int depth() {
    return depth;
  }

  @JsonGetter("gas")
  public long gas() {
    return gas;
  }

  @JsonGetter("gasCost")
  public long gasCost() {
    return gasCost;
  }

  @JsonGetter("memory")
  public String[] memory() {
    return memory;
  }

  @JsonGetter("op")
  public String op() {
    return op;
  }

  @JsonGetter("pc")
  public int pc() {
    return pc;
  }

  @JsonGetter("stack")
  public String[] stack() {
    return stack;
  }

  @JsonGetter("storage")
  public Object storage() {
    return storage;
  }

  @JsonGetter("reason")
  public String reason() {
    return reason;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final StructLog structLog = (StructLog) o;
    return depth == structLog.depth
        && gas == structLog.gas
        && gasCost == structLog.gasCost
        && pc == structLog.pc
        && Arrays.equals(memory, structLog.memory)
        && Objects.equals(op, structLog.op)
        && Arrays.equals(stack, structLog.stack)
        && Objects.equals(storage, structLog.storage);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(depth, gas, gasCost, op, pc, storage);
    result = 31 * result + Arrays.hashCode(memory);
    result = 31 * result + Arrays.hashCode(stack);
    return result;
  }
}
