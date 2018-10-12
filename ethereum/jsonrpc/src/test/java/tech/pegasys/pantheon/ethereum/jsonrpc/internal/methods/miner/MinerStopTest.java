package tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.miner;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.ethereum.blockcreation.EthHashBlockMiner;
import tech.pegasys.pantheon.ethereum.blockcreation.EthHashMiningCoordinator;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MinerStopTest {

  private MinerStop<Void, EthHashBlockMiner> method;

  @Mock private EthHashMiningCoordinator miningCoordinator;

  @Before
  public void before() {
    method = new MinerStop<>(miningCoordinator);
  }

  @Test
  public void shouldReturnCorrectMethodName() {
    assertThat(method.getName()).isEqualTo("miner_stop");
  }

  @Test
  public void shouldReturnTrueWhenMiningStopsSuccessfully() {
    final JsonRpcRequest request = minerStop();
    final JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse(null, true);

    final JsonRpcResponse actualResponse = method.response(request);

    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
  }

  private JsonRpcRequest minerStop() {
    return new JsonRpcRequest("2.0", "miner_stop", null);
  }
}
