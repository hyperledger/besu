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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.witness.ExecutionWitness;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;

public class ExecutionWitnessParameter {

  private final StateDiffParameter stateDiffParameter;
  private final VerkleProofParameter verkleProofParameter;
  private final Hash parentStateRoot;

  @VisibleForTesting
  public ExecutionWitnessParameter() {
    this.stateDiffParameter = null;
    this.verkleProofParameter = null;
    this.parentStateRoot = null;
  }

  @JsonCreator
  public ExecutionWitnessParameter(
      @JsonProperty("stateDiff") final StateDiffParameter stateDiff,
      @JsonProperty("verkleProof") final VerkleProofParameter verkleProof,
      @JsonProperty("parentStateRoot") final Hash parentStateRoot) {
    this.stateDiffParameter = stateDiff;
    this.verkleProofParameter = verkleProof;
    this.parentStateRoot = parentStateRoot;
  }

  public static ExecutionWitnessParameter fromExecutionWitness(
      final ExecutionWitness executionWitness) {
    return new ExecutionWitnessParameter(
        StateDiffParameter.fromStateDiff(executionWitness.getStateDiff()),
        VerkleProofParameter.fromVerkleProof(executionWitness.getVerkleProof()),
        executionWitness.getParentStateRoot());
  }

  public ExecutionWitness toExecutionWitness() {
    return new ExecutionWitness(
        StateDiffParameter.toStateDiff(stateDiffParameter),
        VerkleProofParameter.toVerkleProof(verkleProofParameter),
        parentStateRoot);
  }

  @JsonGetter
  public StateDiffParameter getStateDiff() {
    return stateDiffParameter;
  }

  @JsonGetter
  public VerkleProofParameter getVerkleProof() {
    return verkleProofParameter;
  }

  @JsonGetter
  public Hash getParentStateRoot() {
    return parentStateRoot;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ExecutionWitnessParameter that = (ExecutionWitnessParameter) o;
    return Objects.equals(stateDiffParameter, that.stateDiffParameter)
        && Objects.equals(verkleProofParameter, that.verkleProofParameter)
        && Objects.equals(parentStateRoot, that.parentStateRoot);
  }

  @Override
  public int hashCode() {
    return Objects.hash(stateDiffParameter, verkleProofParameter, parentStateRoot);
  }
}
