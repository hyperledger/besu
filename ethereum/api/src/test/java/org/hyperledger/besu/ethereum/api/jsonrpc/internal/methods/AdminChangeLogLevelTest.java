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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.util.LogConfigurator;

import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
public class AdminChangeLogLevelTest {

  private AdminChangeLogLevel adminChangeLogLevel;

  @BeforeEach
  public void before() {
    adminChangeLogLevel = new AdminChangeLogLevel();
    LogConfigurator.setLevel("", "INFO");
  }

  @Test
  public void shouldReturnExpectedMethodName() {
    assertThat(adminChangeLogLevel.getName()).isEqualTo("admin_changeLogLevel");
  }

  @Test
  public void shouldReturnCorrectResponseWhenRequestHasLogLevel() {
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("2.0", "admin_changeLogLevel", new Object[] {Level.DEBUG.name()}));
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(request.getRequest().getId());

    final Logger loggerBeforeJsonRpcRequest = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    final JsonRpcSuccessResponse actualResponse =
        (JsonRpcSuccessResponse) adminChangeLogLevel.response(request);
    final Logger loggerAfterJsonRpcRequest = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
    assertThat(loggerBeforeJsonRpcRequest.isInfoEnabled()).isTrue();
    assertThat(loggerAfterJsonRpcRequest.isDebugEnabled()).isTrue();
  }

  @Test
  public void shouldReturnCorrectResponseWhenRequestHasLogLevelAndFilters() {
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest(
                "2.0",
                "admin_changeLogLevel",
                new Object[] {Level.DEBUG.name(), new String[] {"com"}}));
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(request.getRequest().getId());

    final Logger loggerOfAllProjectBeforeJsonRpcRequest =
        LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    final Logger loggerWithSpecificPackageBeforeJsonRpcRequest = LoggerFactory.getLogger("com");
    final JsonRpcSuccessResponse actualResponse =
        (JsonRpcSuccessResponse) adminChangeLogLevel.response(request);
    final Logger loggerOfAllProjectAfterJsonRpcRequest =
        LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    final Logger loggerWithSpecificPackageAfterJsonRpcRequest = LoggerFactory.getLogger("com");

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
    assertThat(loggerOfAllProjectBeforeJsonRpcRequest.isInfoEnabled()).isTrue();
    assertThat(loggerOfAllProjectAfterJsonRpcRequest.isInfoEnabled()).isTrue();
    assertThat(loggerWithSpecificPackageBeforeJsonRpcRequest.isInfoEnabled()).isTrue();
    assertThat(loggerWithSpecificPackageAfterJsonRpcRequest.isDebugEnabled()).isTrue();
  }

  @Test
  public void requestHasValidStringLogLevelParameter() {
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("2.0", "admin_changeLogLevel", new String[] {"DEBUG"}));
    final JsonRpcResponse expectedResponse =
        new JsonRpcSuccessResponse(request.getRequest().getId());

    final Logger loggerBeforeJsonRpcRequest = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    final JsonRpcSuccessResponse actualResponse =
        (JsonRpcSuccessResponse) adminChangeLogLevel.response(request);
    final Logger loggerAfterJsonRpcRequest = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
    assertThat(loggerBeforeJsonRpcRequest.isInfoEnabled()).isTrue();
    assertThat(loggerAfterJsonRpcRequest.isDebugEnabled()).isTrue();
  }

  @Test
  public void requestHasNullArrayParameter() {
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("2.0", "admin_changeLogLevel", new Object[] {null}));
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(
            request.getRequest().getId(), RpcErrorType.INVALID_LOG_LEVEL_PARAMS);

    final JsonRpcResponse actualResponse = adminChangeLogLevel.response(request);
    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void requestHasInvalidStringLogLevelParameter() {
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("2.0", "admin_changeLogLevel", new String[] {"INVALID"}));
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(
            request.getRequest().getId(), RpcErrorType.INVALID_LOG_LEVEL_PARAMS);

    final JsonRpcResponse actualResponse = adminChangeLogLevel.response(request);
    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }

  @Test
  public void requestHasInvalidLogFilterParameter() {
    final JsonRpcRequestContext request =
        new JsonRpcRequestContext(
            new JsonRpcRequest("2.0", "admin_changeLogLevel", new Object[] {"DEBUG", "INVALID"}));
    final JsonRpcResponse expectedResponse =
        new JsonRpcErrorResponse(
            request.getRequest().getId(), RpcErrorType.INVALID_LOG_FILTER_PARAMS);

    final JsonRpcResponse actualResponse = adminChangeLogLevel.response(request);
    assertThat(actualResponse).usingRecursiveComparison().isEqualTo(expectedResponse);
  }
}
