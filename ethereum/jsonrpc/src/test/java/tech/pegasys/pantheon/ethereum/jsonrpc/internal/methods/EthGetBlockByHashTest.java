package tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.results.BlockResultFactory;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EthGetBlockByHashTest {

  @Rule public final ExpectedException thrown = ExpectedException.none();

  @Mock private BlockchainQueries blockChainQueries;
  private final BlockResultFactory blockResult = new BlockResultFactory();
  private final JsonRpcParameter parameters = new JsonRpcParameter();
  private EthGetBlockByHash method;
  private final String JSON_RPC_VERSION = "2.0";
  private final String ETH_METHOD = "eth_getBlockByHash";
  private final String ZERO_HASH = String.valueOf(Hash.ZERO);

  @Before
  public void setUp() {
    method = new EthGetBlockByHash(blockChainQueries, blockResult, parameters);
  }

  @Test
  public void returnsCorrectMethodName() {
    assertThat(method.getName()).isEqualTo(ETH_METHOD);
  }

  @Test
  public void exceptionWhenNoParamsSupplied() {
    final JsonRpcRequest request = requestWithParams();

    thrown.expect(InvalidJsonRpcParameters.class);
    thrown.expectMessage("Missing required json rpc parameter at index 0");

    method.response(request);

    verifyNoMoreInteractions(blockChainQueries);
  }

  @Test
  public void exceptionWhenNoHashSupplied() {
    final JsonRpcRequest request = requestWithParams("false");

    thrown.expect(InvalidJsonRpcParameters.class);
    thrown.expectMessage("Invalid json rpc parameter at index 0");

    method.response(request);

    verifyNoMoreInteractions(blockChainQueries);
  }

  @Test
  public void exceptionWhenNoBoolSupplied() {
    final JsonRpcRequest request = requestWithParams(ZERO_HASH);

    thrown.expect(InvalidJsonRpcParameters.class);
    thrown.expectMessage("Missing required json rpc parameter at index 1");

    method.response(request);

    verifyNoMoreInteractions(blockChainQueries);
  }

  @Test
  public void exceptionWhenHashParamInvalid() {
    final JsonRpcRequest request = requestWithParams("hash", "true");

    thrown.expect(InvalidJsonRpcParameters.class);
    thrown.expectMessage("Invalid json rpc parameter at index 0");

    method.response(request);

    verifyNoMoreInteractions(blockChainQueries);
  }

  @Test
  public void exceptionWhenBoolParamInvalid() {
    final JsonRpcRequest request = requestWithParams(ZERO_HASH, "maybe");

    thrown.expect(InvalidJsonRpcParameters.class);
    thrown.expectMessage("Invalid json rpc parameter at index 1");

    method.response(request);

    verifyNoMoreInteractions(blockChainQueries);
  }

  private JsonRpcRequest requestWithParams(final Object... params) {
    return new JsonRpcRequest(JSON_RPC_VERSION, ETH_METHOD, params);
  }
}
