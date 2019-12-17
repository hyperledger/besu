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
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcError;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcErrorResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcResponse;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.response.JsonRpcSuccessResponse;
import org.hyperledger.besu.plugin.BesuPlugin;

import java.util.Map;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AdminReloadPluginConfiguration implements JsonRpcMethod {

  private static final Logger LOG = LogManager.getLogger();
  private final Map<String, BesuPlugin> namedPlugins;

  public AdminReloadPluginConfiguration(final Map<String, BesuPlugin> namedPlugins) {
    this.namedPlugins = namedPlugins;
  }

  @Override
  public String getName() {
    return RpcMethod.ADMIN_RELOAD_PLUGIN_CONFIG.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    try {
      final Optional<String> maybePluginName = requestContext.getOptionalParameter(0, String.class);
      if (maybePluginName.isPresent() && namedPlugins.containsKey(maybePluginName.get())) {
        reloadPluginConfig(maybePluginName.get(), namedPlugins.get(maybePluginName.get()));
      } else {
        LOG.info("Attempting to reload all plugins configurations.");
        namedPlugins.forEach(this::reloadPluginConfig);
      }
      return new JsonRpcSuccessResponse(requestContext.getRequest().getId());
    } catch (InvalidJsonRpcParameters invalidJsonRpcParameters) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), JsonRpcError.INVALID_PARAMS);
    }
  }

  private void reloadPluginConfig(final String name, final BesuPlugin plugin) {
    LOG.info("Reloading plugin configuration: {}.", name);
    plugin.reloadConfiguration();
  }
}
