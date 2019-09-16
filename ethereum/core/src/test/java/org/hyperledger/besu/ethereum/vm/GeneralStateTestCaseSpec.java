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
package org.hyperledger.besu.ethereum.vm;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderMock;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.Transaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** A Transaction test case specification. */
@JsonIgnoreProperties("_info")
public class GeneralStateTestCaseSpec {

  private final Map<String, List<GeneralStateTestCaseEipSpec>> finalStateSpecs;

  @JsonCreator
  public GeneralStateTestCaseSpec(
      @JsonProperty("env") final BlockHeaderMock blockHeader,
      @JsonProperty("pre") final WorldStateMock initialWorldState,
      @JsonProperty("post") final Map<String, List<PostSection>> postSection,
      @JsonProperty("transaction") final StateTestVersionedTransaction versionedTransaction) {
    this.finalStateSpecs =
        generate(blockHeader, initialWorldState, postSection, versionedTransaction);
  }

  private Map<String, List<GeneralStateTestCaseEipSpec>> generate(
      final BlockHeader blockHeader,
      final WorldStateMock initialWorldState,
      final Map<String, List<PostSection>> postSections,
      final StateTestVersionedTransaction versionedTransaction) {

    initialWorldState.persist();
    final Map<String, List<GeneralStateTestCaseEipSpec>> res = new HashMap<>(postSections.size());
    for (final Map.Entry<String, List<PostSection>> entry : postSections.entrySet()) {
      final String eip = entry.getKey();
      final List<PostSection> post = entry.getValue();
      final List<GeneralStateTestCaseEipSpec> specs = new ArrayList<>(post.size());
      for (final PostSection p : post) {
        final Supplier<Transaction> txSupplier = () -> versionedTransaction.get(p.indexes);
        specs.add(
            new GeneralStateTestCaseEipSpec(
                eip, txSupplier, initialWorldState, p.rootHash, p.logsHash, blockHeader));
      }
      res.put(eip, specs);
    }
    return res;
  }

  Map<String, List<GeneralStateTestCaseEipSpec>> finalStateSpecs() {
    return finalStateSpecs;
  }

  /**
   * Indexes in the "transaction" part of the general state spec json, which allow tests to vary the
   * input transaction of the tests based on the hard-fork.
   */
  public static class Indexes {

    public final int gas;
    public final int data;
    public final int value;

    @JsonCreator
    public Indexes(
        @JsonProperty("gas") final int gas,
        @JsonProperty("data") final int data,
        @JsonProperty("value") final int value) {
      this.gas = gas;
      this.data = data;
      this.value = value;
    }

    @Override
    public boolean equals(final Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (getClass() != obj.getClass()) return false;
      final Indexes other = (Indexes) obj;
      return data == other.data && gas == other.gas && value == other.value;
    }

    @Override
    public int hashCode() {
      return Objects.hash(data, gas, value);
    }
  }

  /** Represents the "post" part of a general state test json _for a specific hard-fork_. */
  public static class PostSection {

    private final Hash rootHash;
    private final Hash logsHash;
    private final Indexes indexes;

    @JsonCreator
    public PostSection(
        @JsonProperty("hash") final String hash,
        @JsonProperty("logs") final String logs,
        @JsonProperty("indexes") final Indexes indexes) {
      this.rootHash = Hash.fromHexString(hash);
      this.logsHash = Hash.fromHexString(logs);
      this.indexes = indexes;
    }
  }
}
