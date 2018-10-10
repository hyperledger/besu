package net.consensys.pantheon.ethereum.jsonrpc.internal.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import net.consensys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import net.consensys.pantheon.ethereum.jsonrpc.internal.filter.FilterManager;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EthNewBlockFilterTest {

  @Mock private FilterManager filterManager;
  private EthNewBlockFilter method;
  private final String ETH_METHOD = "eth_newBlockFilter";

  @Before
  public void setUp() {
    method = new EthNewBlockFilter(filterManager);
  }

  @Test
  public void getMethodReturnsExpectedName() {
    assertThat(method.getName()).isEqualTo(ETH_METHOD);
  }

  @Test
  public void getResponse() {
    when(filterManager.installBlockFilter()).thenReturn("0x0");
    final JsonRpcRequest request = new JsonRpcRequest("2.0", ETH_METHOD, new String[] {});
    final JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse(request.getId(), "0x0");
    final JsonRpcResponse actualResponse = method.response(request);
    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
    verify(filterManager).installBlockFilter();
    verifyNoMoreInteractions(filterManager);
  }
}
