package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.merge.MergeContext;
import org.hyperledger.besu.consensus.merge.blockcreation.MergeMiningCoordinator;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;

import io.vertx.core.Vertx;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

public class EngineForkchoiceUpdatedTest {

  private EngineForkchoiceUpdated method;
  private static final Vertx vertx = Vertx.vertx();

  @Mock private ProtocolContext protocolContext;

  @Mock private MergeContext mergeContext;

  @Mock private MergeMiningCoordinator mergeCoordinator;

  @Mock private MutableBlockchain blockchain;

  @Before
  public void before() {
    when(protocolContext.getConsensusContext(Mockito.any())).thenReturn(mergeContext);
    when(protocolContext.getBlockchain()).thenReturn(blockchain);
    this.method = new EngineForkchoiceUpdated(vertx, protocolContext, mergeCoordinator);
  }

  @Test
  public void shouldReturnExpectedMethodName() {
    // will break as specs change, intentional:
    assertThat(method.getName()).isEqualTo("engine_forkchoiceUpdatedV1");
  }

  //  @Test
  //  public void ensureParamMapping() {
  //    new EngineForkChoiceUpdatedParameter()
  //  }

  //  private JsonRpcRequestContext mockRequest(
  //      EngineForkChoiceUpdatedParameter forkchoiceParam,
  //      Optional<EnginePayloadAttributesParameter> payloadParam) {
  //    return new JsonRpcRequestContext(
  //        new JsonRpcRequest(
  //            "2.0",
  //            RpcMethod.ENGINE_FORKCHOICE_UPDATED.getMethodName(),
  //            new Object[] {forkchoiceParam, payloadParam.orElse(null)}));
  //  }
}
