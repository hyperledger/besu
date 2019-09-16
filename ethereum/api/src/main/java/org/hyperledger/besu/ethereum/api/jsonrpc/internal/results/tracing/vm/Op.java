/*
 * Copyright 2019 ConsenSys AG.
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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.vm;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public class Op {
  private long cost;
  private Ex ex;
  private long pc;
  private VmTrace sub;

  public Op() {}

  public Op(final long cost, final Ex ex, final long pc, final VmTrace sub) {
    this.cost = cost;
    this.ex = ex;
    this.pc = pc;
    this.sub = sub;
  }

  public static long totalGasCost(final Stream<Op> ops) {
    final AtomicLong total = new AtomicLong(0);
    ops.forEach(op -> total.addAndGet(op.cost));
    return total.get();
  }

  public long getCost() {
    return cost;
  }

  public Ex getEx() {
    return ex;
  }

  public long getPc() {
    return pc;
  }

  public VmTrace getSub() {
    return sub;
  }

  public void setCost(final long cost) {
    this.cost = cost;
  }

  public void setEx(final Ex ex) {
    this.ex = ex;
  }

  public void setPc(final long pc) {
    this.pc = pc;
  }

  public void setSub(final VmTrace sub) {
    this.sub = sub;
  }

  @Override
  public int hashCode() {
    return Objects.hash(cost, ex, pc, sub);
  }
}
