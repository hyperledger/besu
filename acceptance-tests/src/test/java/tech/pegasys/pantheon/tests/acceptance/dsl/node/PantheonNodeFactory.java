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

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static tech.pegasys.pantheon.consensus.clique.jsonrpc.CliqueRpcApis.CLIQUE;

import tech.pegasys.pantheon.consensus.clique.CliqueExtraData;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.MiningParameters;
import tech.pegasys.pantheon.ethereum.core.MiningParametersTestBuilder;
import tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcConfiguration;
import tech.pegasys.pantheon.ethereum.jsonrpc.RpcApi;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.WebSocketConfiguration;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.google.common.io.Resources;

public class PantheonNodeFactory {

  private PantheonNode create(final PantheonFactoryConfiguration config) throws IOException {
    ServerSocket serverSocket = new ServerSocket(0);
    final PantheonNode node =
        new PantheonNode(
            config.getName(),
            config.getMiningParameters(),
            config.getJsonRpcConfiguration(),
            config.getWebSocketConfiguration(),
            config.isDevMode(),
            config.getGenesisConfigProvider(),
            serverSocket.getLocalPort());
    serverSocket.close();

    return node;
  }

  public PantheonNode createMinerNode(final String name) throws IOException {
    return create(
        new PantheonFactoryConfiguration(
            name, createMiningParameters(true), createJsonRpcConfig(), createWebSocketConfig()));
  }

  public PantheonNode createArchiveNode(final String name) throws IOException {
    return create(
        new PantheonFactoryConfiguration(
            name, createMiningParameters(false), createJsonRpcConfig(), createWebSocketConfig()));
  }

  public PantheonNode createArchiveNodeWithRpcDisabled(final String name) throws IOException {
    return create(
        new PantheonFactoryConfiguration(
            name,
            createMiningParameters(false),
            JsonRpcConfiguration.createDefault(),
            WebSocketConfiguration.createDefault()));
  }

  public PantheonNode createArchiveNodeWithRpcApis(
      final String name, final RpcApi... enabledRpcApis) throws IOException {
    final JsonRpcConfiguration jsonRpcConfig = createJsonRpcConfig();
    jsonRpcConfig.setRpcApis(asList(enabledRpcApis));
    final WebSocketConfiguration webSocketConfig = createWebSocketConfig();
    webSocketConfig.setRpcApis(asList(enabledRpcApis));

    return create(
        new PantheonFactoryConfiguration(
            name, createMiningParameters(false), jsonRpcConfig, webSocketConfig));
  }

  public PantheonNode createCliqueNode(final String name) throws IOException {
    return create(
        new PantheonFactoryConfiguration(
            name,
            createMiningParameters(true),
            jsonRpcConfigWithClique(),
            createWebSocketConfig(),
            false,
            this::createCliqueGenesisConfig));
  }

  public PantheonNode createCliqueNodeWithValidators(final String name, final String... validators)
      throws IOException {
    return create(
        new PantheonFactoryConfiguration(
            name,
            createMiningParameters(true),
            jsonRpcConfigWithClique(),
            createWebSocketConfig(),
            false,
            nodes -> createCliqueGenesisConfigForValidators(asList(validators), nodes)));
  }

  private Optional<String> createCliqueGenesisConfig(final Collection<RunnableNode> validators) {
    String genesisTemplate = cliqueGenesisTemplateConfig();
    String cliqueExtraData = encodeCliqueExtraData(validators);
    String genesis = genesisTemplate.replaceAll("%cliqueExtraData%", cliqueExtraData);
    return Optional.of(genesis);
  }

  private Optional<String> createCliqueGenesisConfigForValidators(
      final Collection<String> validators, final Collection<RunnableNode> pantheonNodes) {
    List<RunnableNode> collect =
        pantheonNodes.stream().filter(n -> validators.contains(n.getName())).collect(toList());
    return createCliqueGenesisConfig(collect);
  }

  private String cliqueGenesisTemplateConfig() {
    try {
      URI uri = Resources.getResource("clique/clique.json").toURI();
      return Resources.toString(uri.toURL(), Charset.defaultCharset());
    } catch (URISyntaxException | IOException e) {
      throw new IllegalStateException("Unable to get test clique genesis config");
    }
  }

  private String encodeCliqueExtraData(final Collection<RunnableNode> nodes) {
    final List<Address> addresses = nodes.stream().map(RunnableNode::getAddress).collect(toList());
    return CliqueExtraData.createGenesisExtraDataString(addresses);
  }

  private JsonRpcConfiguration jsonRpcConfigWithClique() {
    final JsonRpcConfiguration jsonRpcConfig = createJsonRpcConfig();
    final List<RpcApi> rpcApis = new ArrayList<>(jsonRpcConfig.getRpcApis());
    rpcApis.add(CLIQUE);
    jsonRpcConfig.setRpcApis(rpcApis);
    return jsonRpcConfig;
  }

  private MiningParameters createMiningParameters(final boolean miner) {
    return new MiningParametersTestBuilder().enabled(miner).build();
  }

  private JsonRpcConfiguration createJsonRpcConfig() {
    final JsonRpcConfiguration config = JsonRpcConfiguration.createDefault();
    config.setEnabled(true);
    config.setPort(0);
    return config;
  }

  private WebSocketConfiguration createWebSocketConfig() {
    final WebSocketConfiguration config = WebSocketConfiguration.createDefault();
    config.setEnabled(true);
    config.setPort(0);
    return config;
  }

  static class PantheonFactoryConfiguration {

    private final String name;
    private final MiningParameters miningParameters;
    private final JsonRpcConfiguration jsonRpcConfiguration;
    private final WebSocketConfiguration webSocketConfiguration;
    private final boolean devMode;
    private final GenesisConfigProvider genesisConfigProvider;

    public PantheonFactoryConfiguration(
        final String name,
        final MiningParameters miningParameters,
        final JsonRpcConfiguration jsonRpcConfiguration,
        final WebSocketConfiguration webSocketConfiguration) {
      this(
          name,
          miningParameters,
          jsonRpcConfiguration,
          webSocketConfiguration,
          true,
          ignore -> Optional.empty());
    }

    public PantheonFactoryConfiguration(
        final String name,
        final MiningParameters miningParameters,
        final JsonRpcConfiguration jsonRpcConfiguration,
        final WebSocketConfiguration webSocketConfiguration,
        final boolean devMode,
        final GenesisConfigProvider genesisConfigProvider) {
      this.name = name;
      this.miningParameters = miningParameters;
      this.jsonRpcConfiguration = jsonRpcConfiguration;
      this.webSocketConfiguration = webSocketConfiguration;
      this.devMode = devMode;
      this.genesisConfigProvider = genesisConfigProvider;
    }

    public String getName() {
      return name;
    }

    public MiningParameters getMiningParameters() {
      return miningParameters;
    }

    public JsonRpcConfiguration getJsonRpcConfiguration() {
      return jsonRpcConfiguration;
    }

    public WebSocketConfiguration getWebSocketConfiguration() {
      return webSocketConfiguration;
    }

    public boolean isDevMode() {
      return devMode;
    }

    public GenesisConfigProvider getGenesisConfigProvider() {
      return genesisConfigProvider;
    }
  }
}
