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
package org.hyperledger.besu.ethereum.trie.verkle;

import java.util.List;

import org.apache.tuweni.bytes.Bytes32;

public record IPAProof(List<Bytes32> cl, List<Bytes32> cr, Bytes32 finalEvaluation) {
  private static final int IPA_PROOF_DEPTH = 8;

  public IPAProof(final List<Bytes32> cl, final List<Bytes32> cr, final Bytes32 finalEvaluation) {
    if (cl.size() != IPA_PROOF_DEPTH || cr.size() != IPA_PROOF_DEPTH) {
      throw new IllegalArgumentException("cl and cr must have a length of " + IPA_PROOF_DEPTH);
    }
    this.cl = cl;
    this.cr = cr;
    this.finalEvaluation = finalEvaluation;
  }

  @Override
  public String toString() {
    return "IPAProof{"
        + "cl="
        + cl.toString()
        + ", cr="
        + cr.toString()
        + ", finalEvaluation="
        + finalEvaluation
        + '}';
  }
}
