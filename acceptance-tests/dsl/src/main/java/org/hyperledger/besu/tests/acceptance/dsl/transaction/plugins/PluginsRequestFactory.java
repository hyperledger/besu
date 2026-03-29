/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.tests.acceptance.dsl.transaction.plugins;

import java.util.Collections;
import java.util.Optional;

import org.web3j.protocol.Web3jService;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;

public class PluginsRequestFactory {

  public static class ReloadConfigurationResponse extends Response<String> {}

  private final Web3jService web3jService;

  public PluginsRequestFactory(final Web3jService web3jService) {
    this.web3jService = web3jService;
  }

  public Request<?, ReloadConfigurationResponse> reloadConfiguration(
      final Optional<String> maybePluginName) {
    return new Request<>(
        "plugins_reloadPluginConfig",
        maybePluginName.map(Collections::singletonList).orElse(Collections.emptyList()),
        web3jService,
        ReloadConfigurationResponse.class);
  }
}
