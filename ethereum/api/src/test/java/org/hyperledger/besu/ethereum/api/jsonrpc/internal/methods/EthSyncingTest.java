/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.SyncingResult;
import org.hyperledger.besu.ethereum.core.DefaultSyncStatus;
import org.hyperledger.besu.ethereum.core.Synchronizer;
import org.hyperledger.besu.plugin.data.SyncStatus;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EthSyncingTest {

  @Mock private Synchronizer synchronizer;
  private EthSyncing method;
  private final String JSON_RPC_VERSION = "2.0";
  private final String ETH_METHOD = "eth_syncing";

  @BeforeEach
  public void setUp() {
    method = new EthSyncing(synchronizer);
  }

  @Test
  public void returnsCorrectMethodName() {
    assertThat(method.getName()).isEqualTo(ETH_METHOD);
  }

  @Test
  public void shouldReturnFalseWhenSyncStatusIsEmpty() {
    final JsonRpcRequestContext request = requestWithParams();
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(request.getRequest().getId(), false);
    final Optional<SyncStatus> optionalSyncStatus = Optional.empty();
    when(synchronizer.getSyncStatus()).thenReturn(optionalSyncStatus);

    final JsonRpcResponse actualResponse = method.response(request);
    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
    verify(synchronizer).getSyncStatus();
    verifyNoMoreInteractions(synchronizer);
  }

  @Test
  public void shouldReturnExpectedValueWhenSyncStatusIsNotEmpty() {
    final JsonRpcRequestContext request = requestWithParams();
    final SyncStatus expectedSyncStatus =
        new DefaultSyncStatus(0, 1, 2, Optional.empty(), Optional.empty());
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(
            request.getRequest().getId(), new SyncingResult(expectedSyncStatus));
    final Optional<SyncStatus> optionalSyncStatus = Optional.of(expectedSyncStatus);
    when(synchronizer.getSyncStatus()).thenReturn(optionalSyncStatus);

    final JsonRpcResponse actualResponse = method.response(request);
    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
    verify(synchronizer).getSyncStatus();
    verifyNoMoreInteractions(synchronizer);
  }

  @Test
  public void shouldReturnExpectedValueWhenFastSyncStatusIsNotEmpty() {
    final JsonRpcRequestContext request = requestWithParams();
    final SyncStatus expectedSyncStatus =
        new DefaultSyncStatus(0, 1, 2, Optional.of(3L), Optional.of(4L));
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(
            request.getRequest().getId(), new SyncingResult(expectedSyncStatus));
    final Optional<SyncStatus> optionalSyncStatus = Optional.of(expectedSyncStatus);
    when(synchronizer.getSyncStatus()).thenReturn(optionalSyncStatus);

    final JsonRpcResponse actualResponse = method.response(request);
    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
    verify(synchronizer).getSyncStatus();
    verifyNoMoreInteractions(synchronizer);
  }

  private JsonRpcRequestContext requestWithParams(final Object... params) {
    return new JsonRpcRequestContext(new JsonRpcRequest(JSON_RPC_VERSION, ETH_METHOD, params));
  }
}
