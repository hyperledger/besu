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
import org.hyperledger.besu.ethereum.trie.verkle.VerkleProof;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class VerkleProofParameter {
  // TODO Maybe create a Bytes31 in Tuweni for stems?

  final List<String> otherStems;
  private final Bytes depthExtensionPresent;
  private final List<String> commitmentsByPath;
  private final Bytes32 d;
  private final IPAProofParameter ipaProof;

  @JsonCreator
  public VerkleProofParameter(
      @JsonProperty("otherStems") final List<String> otherStems,
      @JsonDeserialize(using = HexStringDeserializer.class) @JsonProperty("depthExtensionPresent")
          final Bytes depthExtensionPresent,
      @JsonProperty("commitmentsByPath") final List<String> commitmentsByPath,
      @JsonDeserialize(using = HexStringDeserializer.class) @JsonProperty("d") final Bytes32 d,
      @JsonProperty("ipaProof") final IPAProofParameter ipaProof) {
    this.otherStems = otherStems;
    this.depthExtensionPresent = depthExtensionPresent;
    this.commitmentsByPath = commitmentsByPath;
    this.d = d;
    this.ipaProof = ipaProof;
  }

  public static VerkleProofParameter fromVerkleProof(
      final org.hyperledger.besu.ethereum.trie.verkle.VerkleProof verkleProof) {
    return new VerkleProofParameter(
        verkleProof.otherStems().stream().map(Bytes::toHexString).toList(),
        verkleProof.depthExtensionPresent(),
        verkleProof.commitmentsByPath().stream().map(Bytes32::toHexString).toList(),
        verkleProof.d(),
        IPAProofParameter.fromIPAProof(verkleProof.ipaProof()));
  }

  public static VerkleProof toVerkleProof(final VerkleProofParameter verkleProofParameter) {
    return new VerkleProof(
        verkleProofParameter.getOtherStems().stream().map(Bytes::fromHexString).toList(),
        verkleProofParameter.getDepthExtensionPresent(),
        verkleProofParameter.getCommitmentsByPath().stream().map(Bytes32::fromHexString).toList(),
        verkleProofParameter.getD(),
        IPAProofParameter.toIPAProof(verkleProofParameter.getIpaProof()));
  }

  @JsonGetter
  public List<String> getOtherStems() {
    return otherStems;
  }

  @JsonGetter
  public Bytes getDepthExtensionPresent() {
    return depthExtensionPresent;
  }

  @JsonGetter
  public List<String> getCommitmentsByPath() {
    return commitmentsByPath;
  }

  @JsonGetter
  public Bytes32 getD() {
    return d;
  }

  @JsonGetter
  public IPAProofParameter getIpaProof() {
    return ipaProof;
  }
}
