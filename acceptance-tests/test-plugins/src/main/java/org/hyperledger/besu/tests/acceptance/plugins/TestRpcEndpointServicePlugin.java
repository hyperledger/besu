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
package org.hyperledger.besu.tests.acceptance.plugins;

import static com.google.common.base.Preconditions.checkArgument;

import org.hyperledger.besu.plugin.BesuContext;
import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.plugin.services.RpcEndpointService;
import org.hyperledger.besu.plugin.services.rpc.PluginRpcRequest;

import java.util.concurrent.atomic.AtomicReference;

import com.google.auto.service.AutoService;

@AutoService(BesuPlugin.class)
public class TestRpcEndpointServicePlugin implements BesuPlugin {

  private final AtomicReference<String> stringStorage = new AtomicReference<>("InitialValue");
  private final AtomicReference<Object[]> arrayStorage = new AtomicReference<>();

  private String setValue(final PluginRpcRequest request) {
    checkArgument(request.getParams().length == 1, "Only one parameter accepted");
    return stringStorage.updateAndGet(x -> request.getParams()[0].toString());
  }

  private String getValue(final PluginRpcRequest request) {
    return stringStorage.get();
  }

  private Object[] replaceValueList(final PluginRpcRequest request) {
    return arrayStorage.updateAndGet(x -> request.getParams());
  }

  private String throwException(final PluginRpcRequest request) {
    throw new RuntimeException("Kaboom");
  }

  @Override
  public void register(final BesuContext context) {
    context
        .getService(RpcEndpointService.class)
        .ifPresent(
            rpcEndpointService -> {
              rpcEndpointService.registerRPCEndpoint("tests", "getValue", this::getValue);
              rpcEndpointService.registerRPCEndpoint("tests", "setValue", this::setValue);
              rpcEndpointService.registerRPCEndpoint(
                  "tests", "replaceValueList", this::replaceValueList);
              rpcEndpointService.registerRPCEndpoint(
                  "tests", "throwException", this::throwException);
              rpcEndpointService.registerRPCEndpoint("notEnabled", "getValue", this::getValue);
            });
  }

  @Override
  public void start() {}

  @Override
  public void stop() {}
}
