package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.engine;

import io.vertx.core.Vertx;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.EngineGetClientVersionResultV1;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class EngineGetClientVersionV1Test {

    private static final String ENGINE_CLIENT_CODE = "BU";
    private static final String ENGINE_CLIENT_NAME = "Besu";

    private static final String CLIENT_VERSION = "v25.6.7-dev-abcdef12";
    private static final String COMMIT = "abcdef12";

    private EngineGetClientVersionV1 getClientVersion;

    @BeforeEach
    void before() {
        getClientVersion = new EngineGetClientVersionV1(Mockito.mock(Vertx.class), Mockito.mock(ProtocolContext.class), Mockito.mock(EngineCallListener.class), CLIENT_VERSION, COMMIT);
    }

    @Test
    void testGetName() {
        assertThat(getClientVersion.getName()).isEqualTo("engine_getClientVersionsV1");
    }

    @Test
    void testSyncResponse() {
        JsonRpcRequestContext request = new JsonRpcRequestContext(new JsonRpcRequest("v", "m", null));
        JsonRpcResponse actualResult = getClientVersion.syncResponse(request);

        assertThat(actualResult).isInstanceOf(JsonRpcSuccessResponse.class);
        JsonRpcSuccessResponse successResponse = (JsonRpcSuccessResponse) actualResult;
        assertThat(successResponse.getResult()).isInstanceOf(EngineGetClientVersionResultV1.class);
        EngineGetClientVersionResultV1 actualEngineGetClientVersionResultV1 = (EngineGetClientVersionResultV1)successResponse.getResult();
        assertThat(actualEngineGetClientVersionResultV1.getName()).isEqualTo(ENGINE_CLIENT_NAME);
        assertThat(actualEngineGetClientVersionResultV1.getCode()).isEqualTo(ENGINE_CLIENT_CODE);
        assertThat(actualEngineGetClientVersionResultV1.getVersion()).isEqualTo(CLIENT_VERSION);
        assertThat(actualEngineGetClientVersionResultV1.getCommit()).isEqualTo(COMMIT);
    }
}