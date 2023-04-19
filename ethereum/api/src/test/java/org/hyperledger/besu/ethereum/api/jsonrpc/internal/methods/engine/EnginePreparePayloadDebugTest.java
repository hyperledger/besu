package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.engine;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.EngineStatus.SYNCING;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.EngineStatus.VALID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.merge.MergeContext;
import org.hyperledger.besu.consensus.merge.blockcreation.MergeMiningCoordinator;
import org.hyperledger.besu.consensus.merge.blockcreation.PayloadIdentifier;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.EnginePreparePayloadParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.EnginePreparePayloadResult;

import java.util.Optional;

import io.vertx.core.Vertx;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EnginePreparePayloadDebugTest {
  private static final Vertx vertx = Vertx.vertx();
  EnginePreparePayloadDebug method;
  @Mock private ProtocolContext protocolContext;
  @Mock private EngineCallListener engineCallListener;
  @Mock private MergeMiningCoordinator mergeCoordinator;
  @Mock private MergeContext mergeContext;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private JsonRpcRequestContext requestContext;

  @Mock private EnginePreparePayloadParameter param;

  @Before
  public void setUp() {
    when(protocolContext.safeConsensusContext(MergeContext.class))
        .thenReturn(Optional.of(mergeContext));
    when(requestContext.getRequiredParameter(0, EnginePreparePayloadParameter.class))
        .thenReturn(param);
    method =
        spy(
            new EnginePreparePayloadDebug(
                vertx, protocolContext, engineCallListener, mergeCoordinator));
  }

  @Test
  public void shouldReturnSyncing() {
    when(mergeContext.isSyncing()).thenReturn(true);
    var resp = method.syncResponse(requestContext);
    assertThat(resp).isInstanceOf(JsonRpcSuccessResponse.class);
    var result = ((JsonRpcSuccessResponse) resp).getResult();
    assertThat(result).isInstanceOf(EnginePreparePayloadResult.class);
    var payloadResult = (EnginePreparePayloadResult) result;
    assertThat(payloadResult.getStatus()).isEqualTo(SYNCING.name());
  }

  @Test
  public void shouldReturnPayloadId() {
    doAnswer(__ -> Optional.of(new PayloadIdentifier(0xdeadbeefL)))
        .when(method)
        .generatePayload(any());
    var resp = method.syncResponse(requestContext);
    assertThat(resp).isInstanceOf(JsonRpcSuccessResponse.class);
    var result = ((JsonRpcSuccessResponse) resp).getResult();
    assertThat(result).isInstanceOf(EnginePreparePayloadResult.class);
    var payloadResult = (EnginePreparePayloadResult) result;
    assertThat(payloadResult.getStatus()).isEqualTo(VALID.name());
    assertThat(payloadResult.getPayloadId()).isEqualTo("0xdeadbeef");
  }
}
