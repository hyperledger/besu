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
package org.hyperledger.besu.ethereum.core.witness;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import java.util.Objects;

public class ExecutionWitness {

  private final StateDiff stateDiff;
  private final VerkleProof verkleProof;
  private final Hash parentStateRoot;

  // No-op contructor for testing
  public ExecutionWitness() {
    this.stateDiff = null;
    this.verkleProof = null;
    this.parentStateRoot = null;
  }

  public ExecutionWitness(
      final StateDiff stateDiff, final VerkleProof verkleProof, final Hash parentStateRoot) {
    this.stateDiff = stateDiff;
    this.verkleProof = verkleProof;
    this.parentStateRoot = parentStateRoot;
  }

  @SuppressWarnings("unused")
  public static ExecutionWitness readFrom(final RLPInput input) {
    return new ExecutionWitness();
  }

  @Override
  public String toString() {
    return "ExecutionWitness{"
        + "stateDiff="
        + stateDiff
        + ", verkleProof="
        + verkleProof
        + ", parentStateRoot="
        + parentStateRoot
        + '}';
  }

  public StateDiff getStateDiff() {
    return stateDiff;
  }

  public VerkleProof getVerkleProof() {
    return verkleProof;
  }

  public Hash getParentStateRoot() {
    return parentStateRoot;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ExecutionWitness that = (ExecutionWitness) o;
    return Objects.equals(stateDiff, that.stateDiff)
        && Objects.equals(verkleProof, that.verkleProof)
        && Objects.equals(parentStateRoot, that.parentStateRoot);
  }

  @Override
  public int hashCode() {
    return Objects.hash(stateDiff, verkleProof, parentStateRoot);
  }
}
