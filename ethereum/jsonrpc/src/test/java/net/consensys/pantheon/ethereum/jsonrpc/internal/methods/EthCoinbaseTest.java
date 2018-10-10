package net.consensys.pantheon.ethereum.jsonrpc.internal.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import net.consensys.pantheon.ethereum.blockcreation.EthHashMiningCoordinator;
import net.consensys.pantheon.ethereum.core.Address;
import net.consensys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcError;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcErrorResponse;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;

import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EthCoinbaseTest {

  @Mock private EthHashMiningCoordinator miningCoordinator;
  private EthCoinbase method;
  private final String JSON_RPC_VERSION = "2.0";
  private final String ETH_METHOD = "eth_coinbase";

  @Before
  public void setUp() {
    method = new EthCoinbase(miningCoordinator);
  }

  @Test
  public void returnsCorrectMethodName() {
    assertThat(method.getName()).isEqualTo(ETH_METHOD);
  }

  @Test
  public void shouldReturnExpectedValueWhenMiningCoordinatorExists() {
    final JsonRpcRequest request = requestWithParams();
    final String expectedAddressString = "fe3b557e8fb62b89f4916b721be55ceb828dbd73";
    final Address expectedAddress = Address.fromHexString(expectedAddressString);
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(request.getId(), "0x" + expectedAddressString);
    when(miningCoordinator.getCoinbase()).thenReturn(Optional.of(expectedAddress));

    final JsonRpcResponse actualResponse = method.response(request);
    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
    verify(miningCoordinator).getCoinbase();
    verifyNoMoreInteractions(miningCoordinator);
  }

  @Test
  public void shouldReturnErrorWhenCoinbaseNotSpecified() {
    final JsonRpcRequest request = requestWithParams();
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getId(), JsonRpcError.COINBASE_NOT_SPECIFIED);
    when(miningCoordinator.getCoinbase()).thenReturn(Optional.empty());

    final JsonRpcResponse actualResponse = method.response(request);
    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
  }

  private JsonRpcRequest requestWithParams(final Object... params) {
    return new JsonRpcRequest(JSON_RPC_VERSION, ETH_METHOD, params);
  }
}
