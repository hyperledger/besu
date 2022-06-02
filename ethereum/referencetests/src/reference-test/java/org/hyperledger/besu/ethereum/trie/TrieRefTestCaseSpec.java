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
package org.hyperledger.besu.ethereum.trie;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.ArrayList;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.tuweni.bytes.Bytes;

/**
 * A Trie reference test case specification.
 *
 * <p>Note: this class will be auto-generated with the JSON test specification.
 */
public class TrieRefTestCaseSpec {

  /** The set of inputs to insert into the Trie. */
  private final Bytes[][] in;

  /** The expected root hash of the Trie after all inputs have been entered. */
  private final Bytes root;

  /**
   * Public constructor.
   *
   * @param inAsObj The set of inputs to insert into the Trie.
   * @param root The expected root hash of the Trie after all inputs have been entered.
   */
  @JsonCreator
  public TrieRefTestCaseSpec(
      @JsonProperty("in") final Object inAsObj, @JsonProperty("root") final String root) {
    if (inAsObj instanceof ArrayList) {
      @SuppressWarnings("unchecked")
      final ArrayList<ArrayList<String>> in = (ArrayList<ArrayList<String>>) inAsObj;

      this.in = new Bytes[in.size()][2];

      for (int i = 0; i < in.size(); ++i) {
        final String key = in.get(i).get(0);
        final String value = in.get(i).get(1);

        this.in[i][0] = stringParamToBytes(key);
        this.in[i][1] = stringParamToBytes(value);
      }
    } else {
      throw new RuntimeException("in has unknown structure.");
    }

    this.root = Bytes.fromHexStringLenient(root);
  }

  private Bytes stringParamToBytes(final String s) {
    if (s == null) {
      return null;
    }
    if (s.startsWith("0x")) {
      return Bytes.fromHexString(s);
    }
    return Bytes.wrap(s.getBytes(UTF_8));
  }

  /**
   * Returns the set of inputs to insert into the Trie.
   *
   * @return The set of inputs to insert into the Trie.
   */
  public Bytes[][] getIn() {
    return in;
  }

  /**
   * Returns the expected root hash of the Trie after all inputs have been entered.
   *
   * @return The expected root hash of the Trie after all inputs have been entered.
   */
  public Bytes getRoot() {
    return root;
  }
}
