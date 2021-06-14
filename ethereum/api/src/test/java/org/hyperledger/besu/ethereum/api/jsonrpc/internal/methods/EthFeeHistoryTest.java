package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import org.hyperledger.besu.ethereum.api.ImmutableApiConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class EthFeeHistoryTest {
  @Mock private Blockchain blockchain;

  private EthFeeHistory method;

  @Before
  public void setUp() {
    method =
        new EthFeeHistory(
            new BlockchainQueries(
                blockchain,
                null,
                Optional.empty(),
                Optional.empty(),
                ImmutableApiConfiguration.builder().gasPriceMin(100).build()));
  }

  @Test
  public void params() {
    // should fail because no required params given
    assertThatThrownBy(() -> method.response(feeHistoryRequestContext()))
        .isInstanceOf(InvalidJsonRpcParameters.class);
    // should fail because newestBlock not given
    assertThatThrownBy(() -> method.response(feeHistoryRequestContext(1)))
        .isInstanceOf(InvalidJsonRpcParameters.class);
    // should fail because blockCount not given
    assertThatThrownBy(() -> method.response(feeHistoryRequestContext("latest")))
        .isInstanceOf(InvalidJsonRpcParameters.class);
    // should pass because both required params given
    method.response(feeHistoryRequestContext(1, "latest"));
    // should pass because both required params and optional param given
    method.response(feeHistoryRequestContext(1, "latest", new double[] {1, 20.4}));
  }

  @Test
  public void allFieldsPresentForLatestBlock() {
    final JsonRpcRequestContext requestContext =
        feeHistoryRequestContext(1, "latest", new double[] {4.0});
    assertThat(method.response(requestContext))
        .isEqualTo(
            new JsonRpcSuccessResponse(
                requestContext.getRequest().getId(),
                new EthFeeHistory.FeeHistory(
                    0x30, List.of(200L), List.of(0.5), List.of(List.of(150L)))));
  }

  private JsonRpcRequestContext feeHistoryRequestContext(final Object... params) {
    return new JsonRpcRequestContext(new JsonRpcRequest("2.0", "eth_feeHistory", params));
  }
}
