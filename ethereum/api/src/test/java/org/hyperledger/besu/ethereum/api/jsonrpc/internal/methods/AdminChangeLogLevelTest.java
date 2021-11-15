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

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class AdminChangeLogLevelTest {

  private AdminChangeLogLevel adminChangeLogLevel;

  @Before
  public void before() {
    adminChangeLogLevel = new AdminChangeLogLevel();
    Configurator.setAllLevels("", Level.INFO);
  }

  @Test
  public void shouldReturnExpectedMethodName() {
    assertThat(adminChangeLogLevel.getName()).isEqualTo("admin_changeLogLevel");
  }

  @Test
  public void shouldReturnCorrectResponseWhenRequestHasLogLevel() {
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("2.0", "admin_changeLogLevel", new Object[] {Level.DEBUG}));
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(request.getRequest().getId());

    final Level levelBeforeJsonRpcRequest = LogManager.getLogger().getLevel();
    final JsonRpcSuccessResponse actualResponse =
        (JsonRpcSuccessResponse) adminChangeLogLevel.response(request);
    final Level levelAfterJsonRpcRequest = LogManager.getLogger().getLevel();

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
    assertThat(levelBeforeJsonRpcRequest).isEqualByComparingTo(Level.INFO);
    assertThat(levelAfterJsonRpcRequest).isEqualByComparingTo(Level.DEBUG);
  }

  @Test
  public void shouldReturnCorrectResponseWhenRequestHasLogLevelAndFilters() {
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest(
                "2.0", "admin_changeLogLevel", new Object[] {Level.DEBUG, new String[] {"com"}}));
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(request.getRequest().getId());

    final Level levelOfAllProjectBeforeJsonRpcRequest = LogManager.getLogger().getLevel();
    final Level levelWithSpecificPackageBeforeJsonRpcRequest =
        LogManager.getLogger("com").getLevel();
    final JsonRpcSuccessResponse actualResponse =
        (JsonRpcSuccessResponse) adminChangeLogLevel.response(request);
    final Level levelOfAllProjectAfterJsonRpcRequest = LogManager.getLogger().getLevel();
    final Level levelWithSpecificPackageAfterJsonRpcRequest =
        LogManager.getLogger("com").getLevel();

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
    assertThat(levelOfAllProjectBeforeJsonRpcRequest).isEqualByComparingTo(Level.INFO);
    assertThat(levelOfAllProjectAfterJsonRpcRequest).isEqualByComparingTo(Level.INFO);
    assertThat(levelWithSpecificPackageBeforeJsonRpcRequest).isEqualByComparingTo(Level.INFO);
    assertThat(levelWithSpecificPackageAfterJsonRpcRequest).isEqualByComparingTo(Level.DEBUG);
  }

  @Test
  public void requestHasValidStringLogLevelParameter() {
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("2.0", "admin_changeLogLevel", new String[] {"DEBUG"}));
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(request.getRequest().getId());

    final Level levelBeforeJsonRpcRequest = LogManager.getLogger().getLevel();
    final JsonRpcSuccessResponse actualResponse =
        (JsonRpcSuccessResponse) adminChangeLogLevel.response(request);
    final Level levelAfterJsonRpcRequest = LogManager.getLogger().getLevel();

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
    assertThat(levelBeforeJsonRpcRequest).isEqualByComparingTo(Level.INFO);
    assertThat(levelAfterJsonRpcRequest).isEqualByComparingTo(Level.DEBUG);
  }

  @Test
  public void requestHasNullArrayParameter() {
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("2.0", "admin_changeLogLevel", new Object[] {null}));
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getRequest().getId(), JsonRpcError.INVALID_PARAMS);

    final JsonRpcResponse actualResponse = adminChangeLogLevel.response(request);
    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void requestHasInvalidStringLogLevelParameter() {
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("2.0", "admin_changeLogLevel", new String[] {"INVALID"}));
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getRequest().getId(), JsonRpcError.INVALID_PARAMS);

    final JsonRpcResponse actualResponse = adminChangeLogLevel.response(request);
    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void requestHasInvalidLogFilterParameter() {
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("2.0", "admin_changeLogLevel", new Object[] {"DEBUG", "INVALID"}));
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(request.getRequest().getId(), JsonRpcError.INVALID_PARAMS);

    final JsonRpcResponse actualResponse = adminChangeLogLevel.response(request);
    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }
}
