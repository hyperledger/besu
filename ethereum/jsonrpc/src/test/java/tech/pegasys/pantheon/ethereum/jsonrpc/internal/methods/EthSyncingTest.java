/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.ethereum.core.Synchronizer;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.results.SyncingResult;
import tech.pegasys.pantheon.plugin.data.SyncStatus;

import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EthSyncingTest {

  @Mock private Synchronizer synchronizer;
  private EthSyncing method;
  private final String JSON_RPC_VERSION = "2.0";
  private final String ETH_METHOD = "eth_syncing";

  @Before
  public void setUp() {
    method = new EthSyncing(synchronizer);
  }

  @Test
  public void returnsCorrectMethodName() {
    assertThat(method.getName()).isEqualTo(ETH_METHOD);
  }

  @Test
  public void shouldReturnFalseWhenSyncStatusIsEmpty() {
    final JsonRpcRequest request = requestWithParams();
    final JsonRpcResponse expectedResponse = new JsonRpcSuccessResponse(request.getId(), false);
    final Optional<SyncStatus> optionalSyncStatus = Optional.empty();
    when(synchronizer.getSyncStatus()).thenReturn(optionalSyncStatus);

    final JsonRpcResponse actualResponse = method.response(request);
    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
    verify(synchronizer).getSyncStatus();
    verifyNoMoreInteractions(synchronizer);
  }

  @Test
  public void shouldReturnExpectedValueWhenSyncStatusIsNotEmpty() {
    final JsonRpcRequest request = requestWithParams();
    final SyncStatus expectedSyncStatus =
        new tech.pegasys.pantheon.ethereum.core.SyncStatus(0, 1, 2);
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(request.getId(), new SyncingResult(expectedSyncStatus));
    final Optional<SyncStatus> optionalSyncStatus = Optional.of(expectedSyncStatus);
    when(synchronizer.getSyncStatus()).thenReturn(optionalSyncStatus);

    final JsonRpcResponse actualResponse = method.response(request);
    assertThat(actualResponse).isEqualToComparingFieldByField(expectedResponse);
    verify(synchronizer).getSyncStatus();
    verifyNoMoreInteractions(synchronizer);
  }

  private JsonRpcRequest requestWithParams(final Object... params) {
    return new JsonRpcRequest(JSON_RPC_VERSION, ETH_METHOD, params);
  }
}
