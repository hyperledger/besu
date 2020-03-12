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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.vm;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"cost", "operation", "ex", "pc", "sub"})
public class VmOperation {
  private long cost;
  private String operation;
  // Information concerning the execution of the operation.
  private VmOperationExecutionReport vmOperationExecutionReport;
  private long pc;
  private VmTrace sub;

  VmOperation() {}

  public long getCost() {
    return cost;
  }

  @JsonGetter("ex")
  public VmOperationExecutionReport getVmOperationExecutionReport() {
    return vmOperationExecutionReport;
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

  void setVmOperationExecutionReport(final VmOperationExecutionReport vmOperationExecutionReport) {
    this.vmOperationExecutionReport = vmOperationExecutionReport;
  }

  public void setPc(final long pc) {
    this.pc = pc;
  }

  public void setSub(final VmTrace sub) {
    this.sub = sub;
  }

  @JsonInclude(NON_NULL)
  public String getOperation() {
    return operation;
  }

  public void setOperation(final String operation) {
    this.operation = operation;
  }

  @Override
  public int hashCode() {
    return Objects.hash(cost, vmOperationExecutionReport, pc, sub);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final VmOperation that = (VmOperation) o;
    return Objects.equals(vmOperationExecutionReport, that.vmOperationExecutionReport)
        && Objects.equals(sub, that.sub)
        && cost == that.cost
        && pc == that.pc;
  }
}
