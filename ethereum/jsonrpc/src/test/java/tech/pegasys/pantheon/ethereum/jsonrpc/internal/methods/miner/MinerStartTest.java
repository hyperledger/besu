package tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.miner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;

import tech.pegasys.pantheon.ethereum.blockcreation.CoinbaseNotSetException;
import tech.pegasys.pantheon.ethereum.blockcreation.EthHashBlockMiner;
import tech.pegasys.pantheon.ethereum.blockcreation.EthHashMiningCoordinator;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcErrorResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MinerStartTest {

  private MinerStart<Void, EthHashBlockMiner> method;

  @Mock private EthHashMiningCoordinator miningCoordinator;

  @Before
  public void before() {
    method = new MinerStart<>(miningCoordinator);
  }

  @Test
  public void shouldReturnCorrectMethodName() {
    assertThat(method.getName()).isEqualTo("miner_start");
  }

  @Test
  public void shouldReturnTrueWhenMiningStartsSuccessfully() {
    final JsonRpcRequest request = minerStart();
    final JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse(null, true);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
  }

  @Test
  public void shouldReturnCoinbaseNotSetErrorWhenCoinbaseHasNotBeenSet() {
    final JsonRpcRequest request = minerStart();
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(null, JsonRpcError.COINBASE_NOT_SET);

    doThrow(new CoinbaseNotSetException("")).when(miningCoordinator).enable();

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
  }

  private JsonRpcRequest minerStart() {
    return new JsonRpcRequest("2.0", "miner_start", null);
  }
}
