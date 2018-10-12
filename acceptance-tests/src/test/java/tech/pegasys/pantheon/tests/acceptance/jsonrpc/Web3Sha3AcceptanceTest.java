package tech.pegasys.pantheon.tests.acceptance.jsonrpc;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.pantheon.tests.acceptance.dsl.node.PantheonNodeConfig.pantheonNode;

import tech.pegasys.pantheon.tests.acceptance.dsl.AcceptanceTestBase;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.PantheonNode;

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;

public class Web3Sha3AcceptanceTest extends AcceptanceTestBase {

  private PantheonNode node;

  @Before
  public void setUp() throws Exception {
    node = cluster.create(pantheonNode("node1"));
    cluster.start(node);
  }

  @Test
  public void shouldReturnCorrectSha3() throws IOException {
    final String input = "0x68656c6c6f20776f726c64";
    final String sha3 = "0x47173285a8d7341e5e972fc677286384f802f8ef42a5ec5f03bbfa254cb01fad";

    final String response = node.web3().web3Sha3(input);

    assertThat(response).isEqualTo(sha3);
  }
}
