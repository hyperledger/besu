package net.consensys.pantheon.tests.acceptance;

import static net.consensys.pantheon.tests.acceptance.dsl.node.PantheonNodeConfig.pantheonNode;
import static net.consensys.pantheon.tests.acceptance.dsl.node.PantheonNodeConfig.pantheonRpcDisabledNode;
import static net.consensys.pantheon.tests.acceptance.dsl.node.PantheonNodeConfig.patheonNodeWithRpcApis;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import net.consensys.pantheon.ethereum.jsonrpc.JsonRpcConfiguration.RpcApis;
import net.consensys.pantheon.tests.acceptance.dsl.AcceptanceTestBase;
import net.consensys.pantheon.tests.acceptance.dsl.node.PantheonNode;

import org.junit.Before;
import org.junit.Test;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.exceptions.ClientConnectionException;

public class RpcApisTogglesAcceptanceTest extends AcceptanceTestBase {

  private PantheonNode rpcEnabledNode;
  private PantheonNode rpcDisabledNode;
  private PantheonNode ethApiDisabledNode;

  @Before
  public void before() throws Exception {
    rpcEnabledNode = cluster.create(pantheonNode("rpc-enabled"));
    rpcDisabledNode = cluster.create(pantheonRpcDisabledNode("rpc-disabled"));
    ethApiDisabledNode = cluster.create(patheonNodeWithRpcApis("eth-api-disabled", RpcApis.NET));
    cluster.start(rpcEnabledNode, rpcDisabledNode, ethApiDisabledNode);
  }

  @Test
  public void shouldSucceedConnectingToNodeWithJsonRpcEnabled() {
    rpcEnabledNode.verifyJsonRpcEnabled();
  }

  @Test
  public void shouldFailConnectingToNodeWithJsonRpcDisabled() {
    rpcDisabledNode.verifyJsonRpcDisabled();
  }

  @Test
  public void shouldSucceedConnectingToNodeWithWsRpcEnabled() {
    rpcEnabledNode.verifyWsRpcEnabled();
  }

  @Test
  public void shouldFailConnectingToNodeWithWsRpcDisabled() {
    rpcDisabledNode.verifyWsRpcDisabled();
  }

  @Test
  public void shouldSucceedCallingMethodFromEnabledApiGroup() throws Exception {
    final Web3j web3j = ethApiDisabledNode.web3j();

    assertThat(web3j.netVersion().send().getError()).isNull();
  }

  @Test
  public void shouldFailCallingMethodFromDisabledApiGroup() {
    final Web3j web3j = ethApiDisabledNode.web3j();

    assertThat(catchThrowable(() -> web3j.ethAccounts().send()))
        .isInstanceOf(ClientConnectionException.class)
        .hasMessageContaining("Invalid response received: 400");
  }
}
