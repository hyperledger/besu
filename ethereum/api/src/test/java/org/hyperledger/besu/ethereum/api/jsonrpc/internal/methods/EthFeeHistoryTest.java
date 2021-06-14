package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import org.hyperledger.besu.ethereum.api.ImmutableApiConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Optional;

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
    assertThatThrownBy(() -> method.response(feeHistoryRequestContext(new Object[] {})))
        .isInstanceOf(InvalidJsonRpcParameters.class);
    // should fail because newestBlock not given
    assertThatThrownBy(() -> method.response(feeHistoryRequestContext(new Object[] {1})))
        .isInstanceOf(InvalidJsonRpcParameters.class);
    // should fail because blockCount not given
    assertThatThrownBy(() -> method.response(feeHistoryRequestContext(new Object[] {"latest"})))
        .isInstanceOf(InvalidJsonRpcParameters.class);
    // should pass because both required params given
    method.response(feeHistoryRequestContext(new Object[] {1, "latest"}));
    // should pass because both required params and optional param given
    method.response(feeHistoryRequestContext(new Object[] {1, "latest", new double[] {1, 20.4}}));
  }

  @NotNull
  private JsonRpcRequestContext feeHistoryRequestContext(final Object[] params) {
    return new JsonRpcRequestContext(new JsonRpcRequest("2.0", "eth_feeHistory", params));
  }

  //  @Test
  //  public void allFieldsPresentForLatestBlock() {
  //    method.response(new JsonRpcRequestContext(new JsonRpcRequest()))
  //  }

}
