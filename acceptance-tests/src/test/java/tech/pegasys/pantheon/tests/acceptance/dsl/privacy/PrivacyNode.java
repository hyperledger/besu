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
package tech.pegasys.pantheon.tests.acceptance.dsl.privacy;

import static java.nio.charset.StandardCharsets.UTF_8;
import static tech.pegasys.pantheon.tests.acceptance.dsl.WaitUtils.waitFor;

import tech.pegasys.orion.testutil.OrionTestHarness;
import tech.pegasys.pantheon.enclave.Enclave;
import tech.pegasys.pantheon.enclave.types.SendRequest;
import tech.pegasys.pantheon.enclave.types.SendRequestLegacy;
import tech.pegasys.pantheon.ethereum.core.MiningParameters;
import tech.pegasys.pantheon.ethereum.core.PrivacyParameters;
import tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcConfiguration;
import tech.pegasys.pantheon.ethereum.jsonrpc.websocket.WebSocketConfiguration;
import tech.pegasys.pantheon.ethereum.p2p.config.NetworkingConfiguration;
import tech.pegasys.pantheon.ethereum.permissioning.PermissioningConfiguration;
import tech.pegasys.pantheon.metrics.prometheus.MetricsConfiguration;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.PantheonNode;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.configuration.genesis.GenesisConfigurationProvider;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.eea.EeaGetTransactionCountTransaction;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.bytes.BytesValues;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PrivacyNode extends PantheonNode {
  private static final Logger LOG = LogManager.getLogger();

  public OrionTestHarness orion;

  public PrivacyNode(
      final String name,
      final MiningParameters miningParameters,
      final PrivacyParameters privacyParameters,
      final JsonRpcConfiguration jsonRpcConfiguration,
      final WebSocketConfiguration webSocketConfiguration,
      final MetricsConfiguration metricsConfiguration,
      final Optional<PermissioningConfiguration> permissioningConfiguration,
      final Optional<String> keyfilePath,
      final boolean devMode,
      final GenesisConfigurationProvider genesisConfigProvider,
      final boolean p2pEnabled,
      final NetworkingConfiguration networkingConfiguration,
      final boolean discoveryEnabled,
      final boolean bootnodeEligible,
      final boolean revertReasonEnabled,
      final List<String> plugins,
      final List<String> extraCLIOptions,
      final OrionTestHarness orion)
      throws IOException {
    super(
        name,
        miningParameters,
        privacyParameters,
        jsonRpcConfiguration,
        webSocketConfiguration,
        metricsConfiguration,
        permissioningConfiguration,
        keyfilePath,
        devMode,
        genesisConfigProvider,
        p2pEnabled,
        networkingConfiguration,
        discoveryEnabled,
        bootnodeEligible,
        revertReasonEnabled,
        plugins,
        extraCLIOptions,
        new ArrayList<>());
    this.orion = orion;
  }

  public BytesValue getOrionPubKeyBytes() {
    return BytesValue.wrap(orion.getPublicKeys().get(0).getBytes(UTF_8));
  }

  public void testOrionConnection(final PrivacyNode... otherNodes) {
    LOG.info(
        String.format(
            "Testing Orion connectivity between %s (%s) and %s (%s)",
            getName(),
            orion.nodeUrl(),
            Arrays.toString(Arrays.stream(otherNodes).map(PrivacyNode::getName).toArray()),
            Arrays.toString(
                Arrays.stream(otherNodes).map(node -> node.orion.nodeUrl()).toArray())));
    Enclave orionEnclave = new Enclave(orion.clientUrl());
    SendRequest sendRequest1 =
        new SendRequestLegacy(
            "SGVsbG8sIFdvcmxkIQ==",
            orion.getPublicKeys().get(0),
            Arrays.stream(otherNodes)
                .map(node -> node.orion.getPublicKeys().get(0))
                .collect(Collectors.toList()));
    waitFor(() -> orionEnclave.send(sendRequest1));
  }

  public long nextNonce(final BytesValue privacyGroupId) {
    return execute(
            new EeaGetTransactionCountTransaction(
                getAddress().toString(), BytesValues.asBase64String(privacyGroupId)))
        .longValue();
  }
}
