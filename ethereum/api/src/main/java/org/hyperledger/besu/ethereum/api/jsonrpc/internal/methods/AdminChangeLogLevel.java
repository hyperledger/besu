/*
 * Copyright 2019 ConsenSys AG.
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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import org.hyperledger.besu.ethereum.api.jsonrpc.RpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.JsonRpcRequest;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.JsonRpcParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;

import java.util.Arrays;
import java.util.Optional;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;

public class AdminChangeLogLevel implements JsonRpcMethod {

  private static final Logger LOG = LogManager.getLogger();
  private final JsonRpcParameter parameters;

  public AdminChangeLogLevel(final JsonRpcParameter parameters) {
    this.parameters = parameters;
  }

  @Override
  public String getName() {
    return RpcMethod.ADMIN_CHANGE_LOG_LEVEL.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest request) {
    try {
      final Level logLevel = parameters.required(request.getParams(), 0, Level.class);
      final Optional<String[]> optionalLogFilters =
          parameters.optional(request.getParams(), 1, String[].class);
      optionalLogFilters.ifPresentOrElse(
          logFilters ->
              Arrays.stream(logFilters).forEach(logFilter -> setLogLevel(logFilter, logLevel)),
          () -> setLogLevel("", logLevel));
      return new JsonRpcSuccessResponse(request.getId());
    } catch (InvalidJsonRpcParameters invalidJsonRpcParameters) {
      return new JsonRpcErrorResponse(request.getId(), JsonRpcError.INVALID_PARAMS);
    }
  }

  private void setLogLevel(final String logFilter, final Level logLevel) {
    LOG.log(Level.OFF, "Setting {} logging level to {} ", logFilter, logLevel.name());
    Configurator.setAllLevels(logFilter, logLevel);
  }
}
