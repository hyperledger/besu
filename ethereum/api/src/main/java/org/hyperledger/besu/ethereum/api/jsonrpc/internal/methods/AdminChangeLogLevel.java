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

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequestContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter.JsonRpcParameterException;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.RpcErrorType;
import org.hyperledger.besu.util.LogConfigurator;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AdminChangeLogLevel implements JsonRpcMethod {

  private static final Logger LOG = LoggerFactory.getLogger(AdminChangeLogLevel.class);
  private static final Set<String> VALID_PARAMS =
      Set.of("OFF", "ERROR", "WARN", "INFO", "DEBUG", "TRACE", "ALL");

  @Override
  public String getName() {
    return RpcMethod.ADMIN_CHANGE_LOG_LEVEL.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    try {
      final String logLevel;
      try {
        logLevel = requestContext.getRequiredParameter(0, String.class);
      } catch (JsonRpcParameterException e) {
        throw new InvalidJsonRpcParameters(
            "Invalid log level parameter (index 0)", RpcErrorType.INVALID_LOG_LEVEL_PARAMS, e);
      }
      if (!VALID_PARAMS.contains(logLevel)) {
        return new JsonRpcErrorResponse(
            requestContext.getRequest().getId(), RpcErrorType.INVALID_LOG_LEVEL_PARAMS);
      }
      final Optional<String[]> optionalLogFilters;
      try {
        optionalLogFilters = requestContext.getOptionalParameter(1, String[].class);
      } catch (JsonRpcParameterException e) {
        throw new InvalidJsonRpcParameters(
            "Invalid log filter parameters (index 1)", RpcErrorType.INVALID_LOG_FILTER_PARAMS, e);
      }
      optionalLogFilters.ifPresentOrElse(
          logFilters ->
              Arrays.stream(logFilters).forEach(logFilter -> setLogLevel(logFilter, logLevel)),
          () -> setLogLevel("", logLevel));
      return new JsonRpcSuccessResponse(requestContext.getRequest().getId());
    } catch (InvalidJsonRpcParameters invalidJsonRpcParameters) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), invalidJsonRpcParameters.getRpcErrorType());
    }
  }

  private void setLogLevel(final String logFilter, final String logLevel) {
    LOG.debug("Setting {} logging level to {} ", logFilter, logLevel);
    LogConfigurator.setLevel(logFilter, logLevel);
  }
}
