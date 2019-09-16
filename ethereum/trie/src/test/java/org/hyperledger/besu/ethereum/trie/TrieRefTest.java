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
package org.hyperledger.besu.ethereum.trie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeTrue;

import org.hyperledger.besu.testutil.JsonTestParameters;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.Collection;
import java.util.function.Function;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TrieRefTest {

  private static final String[] TEST_CONFIG_FILES = {"TrieTests/trietest.json"};

  private final TrieRefTestCaseSpec spec;

  public TrieRefTest(final String name, final TrieRefTestCaseSpec spec, final boolean runTest) {
    this.spec = spec;
    assumeTrue("Test was blacklisted", runTest);
  }

  @Parameters(name = "Name: {0}")
  public static Collection<Object[]> getTestParametersForConfig() {
    return JsonTestParameters.create(TrieRefTestCaseSpec.class).generate(TEST_CONFIG_FILES);
  }

  @Test
  public void rootHashAfterInsertionsAndRemovals() {
    final SimpleMerklePatriciaTrie<BytesValue, BytesValue> trie =
        new SimpleMerklePatriciaTrie<>(Function.identity());
    for (final BytesValue[] pair : spec.getIn()) {
      if (pair[1] == null) {
        trie.remove(pair[0]);
      } else {
        trie.put(pair[0], pair[1]);
      }
    }

    assertThat(spec.getRoot()).isEqualTo(trie.getRootHash());
  }
}
