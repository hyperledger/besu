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

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.tracing.Trace;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonGetter;

public class VmTrace implements Trace {

  private String code;
  private final List<VmOperation> vmOperations;

  public VmTrace() {
    this("0x");
  }

  public VmTrace(final String code) {
    this(code, new ArrayList<>());
  }

  private VmTrace(final String code, final List<VmOperation> vmOperations) {
    this.code = code;
    this.vmOperations = vmOperations;
  }

  public void add(final VmOperation vmOperation) {
    vmOperations.add(vmOperation);
  }

  public String getCode() {
    return code;
  }

  public void setCode(final String code) {
    this.code = code;
  }

  @JsonGetter("ops")
  public List<VmOperation> getVmOperations() {
    return vmOperations;
  }

  @Override
  public int hashCode() {
    return Objects.hash(code, vmOperations);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final VmTrace that = (VmTrace) o;
    return Objects.equals(code, that.code) && Objects.equals(vmOperations, that.vmOperations);
  }
}
