/*
 * Copyright Hyperledger Besu Contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.ExecutionEngineJsonRpcMethod.EngineStatus.INVALID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.DataGas;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.EnginePayloadParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.EnginePayloadStatusResult;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.mainnet.ScheduledProtocolSpec;
import org.hyperledger.besu.evm.gascalculator.CancunGasCalculator;

import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.assertj.core.api.Assertions;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EngineNewPayloadV3Test extends AbstractEngineNewPayloadTest {

  public EngineNewPayloadV3Test() {
    super(EngineNewPayloadV3::new);
  }

  @Override
  @Test
  public void shouldReturnExpectedMethodName() {
    assertThat(method.getName()).isEqualTo("engine_newPayloadV3");
  }

  @Override
  public void before() {
    super.before();
    when(protocolSpec.getGasCalculator()).thenReturn(new CancunGasCalculator());
  }

  @Test
  public void shouldInvalidPayloadOnShortVersionedHash() {
    Bytes shortHash = Bytes.repeat((byte) 0x69, 31);
    EnginePayloadParameter payload = mock(EnginePayloadParameter.class);
    JsonRpcResponse badParam =
        method.response(
            new JsonRpcRequestContext(
                new JsonRpcRequest(
                    "2.0",
                    RpcMethod.ENGINE_NEW_PAYLOAD_V3.getMethodName(),
                    new Object[] {payload, List.of(shortHash.toHexString())})));
    EnginePayloadStatusResult res = fromSuccessResp(badParam);
    assertThat(res.getStatusAsString()).isEqualTo(INVALID.name());
    assertThat(res.getError()).isEqualTo("Invalid versionedHash");
  }

  private void mockAfterCancun() {
    when(protocolSchedule.hardforkFor(any()))
        .thenReturn(Optional.of(new ScheduledProtocolSpec.Hardfork("Cancun", 0L)));
  }

  @Test
  public void beforeCancun_NilDataFields_NilVersionedHashes_valid() {
    DataGas excessDataGas = null;
    Long dataGasUsed = null;
    List<String> versionedHashes = null;
    var response = responsePayload(excessDataGas, dataGasUsed, versionedHashes);
    Assertions.assertThat(response).isInstanceOf(JsonRpcSuccessResponse.class);
  }

  @Test
  public void beforeCancun_NilExcessDataGas_0x00DataGasUsed_NilVersionedHashes_invalid() {
    DataGas excessDataGas = DataGas.ZERO;
    Long dataGasUsed = null;
    List<String> versionedHashes = null;
    var response = responsePayload(excessDataGas, dataGasUsed, versionedHashes);
    Assertions.assertThat(response).isInstanceOf(JsonRpcErrorResponse.class);
    final JsonRpcErrorResponse errorResponse = (JsonRpcErrorResponse) response;
    assertThat(errorResponse.getError()).isEqualTo(JsonRpcError.UNSUPPORTED_FORK);
  }

  @Test
  public void beforeCancun_0x00DataFields_NilVersionedHashes_invalid() {
    DataGas excessDataGas = DataGas.ZERO;
    Long dataGasUsed = 0L;
    List<String> versionedHashes = List.of();
    var response = responsePayload(excessDataGas, dataGasUsed, versionedHashes);
    Assertions.assertThat(response).isInstanceOf(JsonRpcErrorResponse.class);
    final JsonRpcErrorResponse errorResponse = (JsonRpcErrorResponse) response;
    assertThat(errorResponse.getError()).isEqualTo(JsonRpcError.UNSUPPORTED_FORK);
  }

  @Test
  public void beforeCancun_NilExcessDataGas_0x00DataGasUsed_EmptyVersionedHashes_invalid() {
    DataGas excessDataGas = DataGas.ZERO;
    Long dataGasUsed = null;
    List<String> versionedHashes = List.of();
    var response = responsePayload(excessDataGas, dataGasUsed, versionedHashes);
    Assertions.assertThat(response).isInstanceOf(JsonRpcErrorResponse.class);
    final JsonRpcErrorResponse errorResponse = (JsonRpcErrorResponse) response;
    assertThat(errorResponse.getError()).isEqualTo(JsonRpcError.UNSUPPORTED_FORK);
  }

  @Test
  public void afterCancun_NilExcessDataGas_0x00DataGasUsed_EmptyVersionedHashes_invalid() {
    mockAfterCancun();
    DataGas excessDataGas = null;
    Long dataGasUsed = 0L;
    List<String> versionedHashes = List.of();
    var response = responsePayload(excessDataGas, dataGasUsed, versionedHashes);
    Assertions.assertThat(response).isInstanceOf(JsonRpcErrorResponse.class);
    final JsonRpcErrorResponse errorResponse = (JsonRpcErrorResponse) response;
    assertThat(errorResponse.getError()).isEqualTo(JsonRpcError.INVALID_PARAMS);
  }

  @Test
  public void afterCancun_0x00ExcessDataGas_NilDataGasUsed_EmptyVersionedHashes_invalid() {
    mockAfterCancun();
    DataGas excessDataGas = DataGas.ZERO;
    Long dataGasUsed = null;
    List<String> versionedHashes = List.of();
    var response = responsePayload(excessDataGas, dataGasUsed, versionedHashes);
    Assertions.assertThat(response).isInstanceOf(JsonRpcErrorResponse.class);
    final JsonRpcErrorResponse errorResponse = (JsonRpcErrorResponse) response;
    assertThat(errorResponse.getError()).isEqualTo(JsonRpcError.INVALID_PARAMS);
  }

  JsonRpcResponse responsePayload(
      final DataGas excessDataGas, final Long dataGasUsed, final List<String> versionedHashes) {
    BlockHeader mockHeader =
        new BlockHeaderTestFixture()
            .excessDataGas(excessDataGas)
            .dataGasUsed(dataGasUsed)
            .buildHeader();

    EnginePayloadParameter payload = mockPayload(mockHeader, List.of());
    return method.response(
        new JsonRpcRequestContext(
            new JsonRpcRequest(
                "2.0",
                RpcMethod.ENGINE_NEW_PAYLOAD_V3.getMethodName(),
                new Object[] {payload, versionedHashes})));
  }

  @Override
  @Ignore
  public void shouldRespondWithSyncingDuringBackwardsSync() {}
}
