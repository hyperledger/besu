package tech.pegasys.pantheon.ethereum.trie;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.testutil.JsonTestParameters;
import tech.pegasys.pantheon.util.bytes.BytesValue;

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

  public TrieRefTest(final String name, final TrieRefTestCaseSpec spec) {
    this.spec = spec;
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
