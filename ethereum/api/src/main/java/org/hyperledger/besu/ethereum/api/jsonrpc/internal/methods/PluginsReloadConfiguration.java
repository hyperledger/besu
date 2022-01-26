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
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PluginsReloadConfiguration implements JsonRpcMethod {

  private static final Logger LOG = LoggerFactory.getLogger(PluginsReloadConfiguration.class);
  private final Map<String, BesuPlugin> namedPlugins;

  public PluginsReloadConfiguration(final Map<String, BesuPlugin> namedPlugins) {
    this.namedPlugins = namedPlugins;
  }

  @Override
  public String getName() {
    return RpcMethod.PLUGINS_RELOAD_CONFIG.getMethodName();
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequestContext requestContext) {
    try {
      final String pluginName = requestContext.getRequiredParameter(0, String.class);
      if (!namedPlugins.containsKey(pluginName)) {
        LOG.error(
            "Plugin cannot be reloaded because no plugin has been registered with specified name: {}.",
            pluginName);
        return new JsonRpcErrorResponse(
            requestContext.getRequest().getId(), JsonRpcError.PLUGIN_NOT_FOUND);
      }
      reloadPluginConfig(namedPlugins.get(pluginName));
      return new JsonRpcSuccessResponse(requestContext.getRequest().getId());
    } catch (InvalidJsonRpcParameters invalidJsonRpcParameters) {
      return new JsonRpcErrorResponse(
          requestContext.getRequest().getId(), JsonRpcError.INVALID_PARAMS);
    }
  }

  private void reloadPluginConfig(final BesuPlugin plugin) {
    final String name = plugin.getName().orElseThrow();
    LOG.info("Reloading plugin configuration: {}.", name);
    final CompletableFuture<Void> future = plugin.reloadConfiguration();
    future.thenAcceptAsync(aVoid -> LOG.info("Plugin {} has been reloaded.", name));
  }
}
