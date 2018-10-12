package tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.miner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MinerSetEtherbaseTest {

  private MinerSetEtherbase method;

  @Mock private MinerSetCoinbase minerSetCoinbase;

  @Before
  public void before() {
    this.method = new MinerSetEtherbase(minerSetCoinbase);
  }

  @Test
  public void shouldReturnExpectedMethodName() {
    assertThat(method.getName()).isEqualTo("miner_setEtherbase");
  }

  @Test
  public void shouldDelegateToMinerSetCoinbase() {
    final JsonRpcRequest request =
        new JsonRpcRequest(null, "miner_setEtherbase", new Object[] {"0x0"});

    final ArgumentCaptor<JsonRpcRequest> requestCaptor =
        ArgumentCaptor.forClass(JsonRpcRequest.class);
    when(minerSetCoinbase.response(requestCaptor.capture()))
        .thenReturn(new JsonRpcSuccessResponse(null, true));

    method.response(request);

    final JsonRpcRequest delegatedRequest = requestCaptor.getValue();
    assertThat(delegatedRequest).isEqualToComparingFieldByField(request);
  }
}
