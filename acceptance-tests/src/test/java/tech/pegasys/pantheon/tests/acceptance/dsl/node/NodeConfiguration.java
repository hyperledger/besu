/*
 * Copyright 2018 ConsenSys AG.
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
package tech.pegasys.pantheon.tests.acceptance.dsl.node;

import tech.pegasys.pantheon.cli.EthNetworkConfig;

import java.net.URI;
import java.util.List;
import java.util.Optional;

public interface NodeConfiguration {

  String enodeUrl();

  void bootnodes(List<String> bootnodes);

  List<URI> bootnodes();

  void useWebSocketsForJsonRpc();

  void useAuthenticationTokenInHeaderForJsonRpc(String token);

  Optional<Integer> jsonRpcWebSocketPort();

  String hostName();

  boolean jsonRpcEnabled();

  GenesisConfigProvider genesisConfigProvider();

  Optional<EthNetworkConfig> ethNetworkConfig();

  void ethNetworkConfig(Optional<EthNetworkConfig> ethNetworkConfig);

  boolean isBootnode();
}
