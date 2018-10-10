package net.consensys.pantheon.ethereum.jsonrpc.internal.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import net.consensys.pantheon.ethereum.blockcreation.MiningCoordinator;
import net.consensys.pantheon.ethereum.core.Wei;
import net.consensys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EthGasPriceTest {

  @Mock private MiningCoordinator miningCoordinator;
  private EthGasPrice method;
  private final String JSON_RPC_VERSION = "2.0";
  private final String ETH_METHOD = "eth_gasPrice";

  @Before
  public void setUp() {
    method = new EthGasPrice(miningCoordinator);
  }

  @Test
  public void returnsCorrectMethodName() {
    assertThat(method.getName()).isEqualTo(ETH_METHOD);
  }

  @Test
  public void shouldReturnExpectedValueWhenMiningCoordinatorExists() {
    final JsonRpcRequest request = requestWithParams();
    final String expectedWei = "0x4d2";
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(request.getId(), expectedWei);
    when(miningCoordinator.getMinTransactionGasPrice()).thenReturn(Wei.of(1234));

    final JsonRpcResponse actualResponse = method.response(request);
    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
    verify(miningCoordinator).getMinTransactionGasPrice();
    verifyNoMoreInteractions(miningCoordinator);
  }

  private JsonRpcRequest requestWithParams(final Object... params) {
    return new JsonRpcRequest(JSON_RPC_VERSION, ETH_METHOD, params);
  }
}
