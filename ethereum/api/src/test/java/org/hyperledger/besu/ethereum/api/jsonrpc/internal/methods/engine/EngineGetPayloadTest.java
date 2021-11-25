package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.merge.MergeContext;
import org.hyperledger.besu.consensus.merge.blockcreation.PayloadIdentifier;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.BlockResultFactory;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.ExecutionBlockResult;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;

import java.util.Collections;
import java.util.Optional;

import io.vertx.core.Vertx;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EngineGetPayloadTest {

  private EngineGetPayload method;
  private static final Vertx vertx = Vertx.vertx();
  private static final BlockResultFactory factory = new BlockResultFactory();
  private static final PayloadIdentifier mockPid = PayloadIdentifier.random();
  private static final BlockHeader mockHeader =
      new BlockHeaderTestFixture().random(Bytes32.random()).buildHeader();
  private static final Block mockBlock =
      new Block(mockHeader, new BlockBody(Collections.emptyList(), Collections.emptyList()));

  @Mock private ProtocolContext protocolContext;

  @Mock private MergeContext mergeContext;

  @Before
  public void before() {
    when(mergeContext.retrieveBlockById(mockPid)).thenReturn(Optional.of(mockBlock));
    when(protocolContext.getConsensusContext(Mockito.any())).thenReturn(mergeContext);
    this.method = new EngineGetPayload(vertx, protocolContext, factory);
  }

  @Test
  public void shouldReturnExpectedMethodName() {
    // will break as specs change, intentional:
    assertThat(method.getName()).isEqualTo("engine_getPayloadV1");
  }

  @Test
  public void shouldReturnBlockForKnownPayloadId() {
    var resp = resp(mockPid);
    assertThat(resp).isInstanceOf(JsonRpcSuccessResponse.class);
    Optional.of(resp)
        .map(JsonRpcSuccessResponse.class::cast)
        .ifPresent(
            r -> {
              assertThat(r.getResult()).isInstanceOf(ExecutionBlockResult.class);
              ExecutionBlockResult res = (ExecutionBlockResult) r.getResult();
              assertThat(res.getHash()).isEqualTo(mockHeader.getHash().toString());
              assertThat(res.getRandom())
                  .isEqualTo(mockHeader.getRandom().map(Bytes32::toString).orElse(""));
            });
  }

  @Test
  public void shouldFailForUnknownPayloadId() {
    var resp = resp(PayloadIdentifier.random());
    assertThat(resp).isInstanceOf(JsonRpcErrorResponse.class);
  }

  private JsonRpcResponse resp(final PayloadIdentifier pid) {
    return method.response(
        new JsonRpcRequestContext(
            new JsonRpcRequest(
                "2.0",
                RpcMethod.ENGINE_GET_PAYLOAD.getMethodName(),
                new Object[] {pid.serialize()})));
  }
}
