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

import org.hyperledger.besu.ethereum.core.witness.ExecutionWitness;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ExecutionWitnessParameter {

  private final StateDiffParameter stateDiffParameter;
  private final VerkleProofParameter verkleProofParameter;

  // No-op contructor for testing
  public ExecutionWitnessParameter() {
    this.stateDiffParameter = null;
    this.verkleProofParameter = null;
  }

  @JsonCreator
  public ExecutionWitnessParameter(
      @JsonProperty("stateDiff") final StateDiffParameter stateDiff,
      @JsonProperty("verkleProof") final VerkleProofParameter verkleProof) {
    this.stateDiffParameter = stateDiff;
    this.verkleProofParameter = verkleProof;
  }

  public static ExecutionWitnessParameter fromExecutionWitness(
      final ExecutionWitness executionWitness) {
    return new ExecutionWitnessParameter(
        StateDiffParameter.fromStateDiff(executionWitness.getStateDiff()),
        VerkleProofParameter.fromVerkleProof(executionWitness.getVerkleProof()));
  }

  public ExecutionWitness toExecutionWitness() {
    return new ExecutionWitness(
        StateDiffParameter.toStateDiff(stateDiffParameter),
        VerkleProofParameter.toVerkleProof(verkleProofParameter));
  }

  @JsonGetter
  public StateDiffParameter getStateDiff() {
    return stateDiffParameter;
  }

  @JsonGetter
  public VerkleProofParameter getVerkleProof() {
    return verkleProofParameter;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final ExecutionWitnessParameter that = (ExecutionWitnessParameter) o;

    if (!stateDiffParameter.equals(that.stateDiffParameter)) return false;
    return verkleProofParameter.equals(that.verkleProofParameter);
  }

  @Override
  public int hashCode() {
    int result = stateDiffParameter.hashCode();
    result = 31 * result + verkleProofParameter.hashCode();
    return result;
  }
}
