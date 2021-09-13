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
package org.hyperledger.besu.plugins;

import static com.google.common.base.Preconditions.checkArgument;

import org.hyperledger.besu.plugin.BesuContext;
import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.plugin.services.RpcEndpointService;
import org.hyperledger.besu.plugin.services.rpc.PluginRpcRequest;

import java.util.concurrent.atomic.AtomicReference;

import com.google.auto.service.AutoService;

@AutoService(BesuPlugin.class)
public class TestRpcEndpointServicePlugin implements BesuPlugin {

  static class Bean {
    final String value;

    Bean(final String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }
  }

  private final AtomicReference<String> storage = new AtomicReference<>("InitialValue");

  private String replaceValue(final PluginRpcRequest request) {
    checkArgument(request.getParams().length == 1, "Only one parameter accepted");
    return storage.getAndSet(request.getParams()[0].toString());
  }

  private String[] replaceValueArray(final PluginRpcRequest request) {
    return new String[] {replaceValue(request)};
  }

  private Bean replaceValueBean(final PluginRpcRequest request) {
    return new Bean(replaceValue(request));
  }

  @Override
  public void register(final BesuContext context) {
    context
        .getService(RpcEndpointService.class)
        .ifPresent(
            rpcEndpointService -> {
              rpcEndpointService.registerRPCEndpoint(
                  "unitTests", "replaceValue", this::replaceValue);
              rpcEndpointService.registerRPCEndpoint(
                  "unitTests", "replaceValueArray", this::replaceValueArray);
              rpcEndpointService.registerRPCEndpoint(
                  "unitTests", "replaceValueBean", this::replaceValueBean);
            });
  }

  @Override
  public void start() {}

  @Override
  public void stop() {}
}
