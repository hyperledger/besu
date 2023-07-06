/*
 * Copyright Hyperledger Besu Contributors.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.hyperledger.besu.ethereum.trie.patricia.SimpleMerklePatriciaTrie;
import org.hyperledger.besu.testutil.JsonTestParameters;

import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class TrieRefTest {

  private static final String[] TEST_CONFIG_FILES = {"TrieTests/trietest.json"};

  public static Stream<Arguments> getTestParametersForConfig() {
    return JsonTestParameters.create(TrieRefTestCaseSpec.class).generate(TEST_CONFIG_FILES).stream().map(params -> Arguments.of(params[0], params[1], params[2]));
  }

  @ParameterizedTest(name = "Name: {0}")
  @MethodSource("getTestParametersForConfig")
  public void rootHashAfterInsertionsAndRemovals(final String name, final TrieRefTestCaseSpec spec, final boolean runTest) {
    assumeTrue(runTest, "Test was blacklisted");
    final SimpleMerklePatriciaTrie<Bytes, Bytes> trie =
        new SimpleMerklePatriciaTrie<>(Function.identity());
    for (final Bytes[] pair : spec.getIn()) {
      if (pair[1] == null) {
        trie.remove(pair[0]);
      } else {
        trie.put(pair[0], pair[1]);
      }
    }

    assertThat(spec.getRoot()).isEqualTo(trie.getRootHash());
  }
}
