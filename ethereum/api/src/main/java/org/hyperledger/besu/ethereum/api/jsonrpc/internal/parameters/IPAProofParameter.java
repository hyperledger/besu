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

import org.hyperledger.besu.ethereum.core.json.HexStringDeserializer;
import org.hyperledger.besu.ethereum.trie.verkle.IPAProof;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.apache.tuweni.bytes.Bytes32;

public class IPAProofParameter {
  static final int IPA_PROOF_DEPTH = 8;
  private final List<String> cl;
  private final List<String> cr;
  private final Bytes32 finalEvaluation;

  @JsonCreator
  public IPAProofParameter(
      @JsonProperty("cl") final List<String> cl,
      @JsonProperty("cr") final List<String> cr,
      @JsonDeserialize(using = HexStringDeserializer.class) @JsonProperty("finalEvaluation")
          final Bytes32 finalEvaluation) {
    if (cl.size() != IPA_PROOF_DEPTH || cr.size() != IPA_PROOF_DEPTH) {
      throw new IllegalArgumentException("cl and cr must have a length of " + IPA_PROOF_DEPTH);
    }
    this.cl = cl;
    this.cr = cr;
    this.finalEvaluation = finalEvaluation;
  }

  public static IPAProofParameter fromIPAProof(final IPAProof ipaProof) {
    return new IPAProofParameter(
        ipaProof.cl().stream().map(Bytes32::toHexString).toList(),
        ipaProof.cr().stream().map(Bytes32::toHexString).toList(),
        ipaProof.finalEvaluation());
  }

  public static IPAProof toIPAProof(final IPAProofParameter ipaProofParameter) {
    return new org.hyperledger.besu.ethereum.trie.verkle.IPAProof(
        ipaProofParameter.getCl().stream().map(Bytes32::fromHexString).toList(),
        ipaProofParameter.getCr().stream().map(Bytes32::fromHexString).toList(),
        ipaProofParameter.getFinalEvaluation());
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

  public List<String> getCl() {
    return cl;
  }

  public List<String> getCr() {
    return cr;
  }

  public Bytes32 getFinalEvaluation() {
    return finalEvaluation;
  }
}
