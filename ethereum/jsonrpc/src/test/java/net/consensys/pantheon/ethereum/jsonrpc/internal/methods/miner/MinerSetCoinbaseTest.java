package net.consensys.pantheon.ethereum.jsonrpc.internal.methods.miner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import net.consensys.pantheon.ethereum.blockcreation.MiningCoordinator;
import net.consensys.pantheon.ethereum.core.Address;
import net.consensys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import net.consensys.pantheon.ethereum.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import net.consensys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import net.consensys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MinerSetCoinbaseTest {

  private MinerSetCoinbase method;

  @Mock private MiningCoordinator miningCoordinator;

  @Before
  public void before() {
    this.method = new MinerSetCoinbase(miningCoordinator, new JsonRpcParameter());
  }

  @Test
  public void shouldReturnExpectedMethodName() {
    assertThat(method.getName()).isEqualTo("miner_setCoinbase");
  }

  @Test
  public void shouldFailWhenMissingAddress() {
    final JsonRpcRequest request = minerSetCoinbaseRequest(null);

    final Throwable thrown = catchThrowable(() -> method.response(request));

    assertThat(thrown)
        .isInstanceOf(InvalidJsonRpcParameters.class)
        .hasMessage("Missing required json rpc parameter at index 0");
  }

  @Test
  public void shouldFailWhenAddressIsInvalid() {
    final JsonRpcRequest request = minerSetCoinbaseRequest("foo");

    final Throwable thrown = catchThrowable(() -> method.response(request));

    assertThat(thrown).isInstanceOf(InvalidJsonRpcParameters.class);
  }

  @Test
  public void shouldSetCoinbaseWhenRequestHasAddress() {
    final JsonRpcRequest request = minerSetCoinbaseRequest("0x0");
    final JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse(null, true);

    final JsonRpcResponse response = method.response(request);

    verify(miningCoordinator).setCoinbase(eq(Address.fromHexString("0x0")));
    assertThat(response).isEqualToComparingFieldByField(expectedResponse);
  }

  private JsonRpcRequest minerSetCoinbaseRequest(final String hexString) {
    if (hexString != null) {
      return new JsonRpcRequest("2.0", "miner_setCoinbase", new Object[] {hexString});
    } else {
      return new JsonRpcRequest("2.0", "miner_setCoinbase", new Object[] {});
    }
  }
}
