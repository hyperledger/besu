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
package org.hyperledger.besu.ethereum.api.jsonrpc;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.hyperledger.besu.ethereum.api.jsonrpc.RpcApis.DEFAULT_RPC_APIS;
import static org.hyperledger.besu.ethereum.api.tls.KnownClientFileUtil.writeToKnownClientsFile;
import static org.hyperledger.besu.ethereum.api.tls.TlsClientAuthConfiguration.Builder.aTlsClientAuthConfiguration;
import static org.hyperledger.besu.ethereum.api.tls.TlsConfiguration.Builder.aTlsConfiguration;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import org.hyperledger.besu.config.StubGenesisConfigOptions;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.health.HealthService;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.filter.FilterManager;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.methods.JsonRpcMethodsFactory;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.WebSocketConfiguration;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.tls.FileBasedPasswordProvider;
import org.hyperledger.besu.ethereum.api.tls.SelfSignedP12Certificate;
import org.hyperledger.besu.ethereum.api.tls.TlsConfiguration;
import org.hyperledger.besu.ethereum.blockcreation.PoWMiningCoordinator;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.Synchronizer;
import org.hyperledger.besu.ethereum.eth.EthProtocol;
import org.hyperledger.besu.ethereum.eth.manager.EthPeers;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.MainnetProtocolSchedule;
import org.hyperledger.besu.ethereum.p2p.network.P2PNetwork;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.ethereum.permissioning.AccountLocalConfigPermissioningController;
import org.hyperledger.besu.ethereum.permissioning.NodeLocalConfigPermissioningController;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.metrics.prometheus.MetricsConfiguration;
import org.hyperledger.besu.nat.NatService;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionException;

import io.vertx.core.Vertx;
import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class JsonRpcHttpServiceTlsMisconfigurationTest {
  @ClassRule public static final TemporaryFolder folder = new TemporaryFolder();

  protected static final Vertx vertx = Vertx.vertx();

  private static final String CLIENT_VERSION = "TestClientVersion/0.1.0";
  private static final BigInteger CHAIN_ID = BigInteger.valueOf(123);
  private static final NatService natService = new NatService(Optional.empty());
  private final SelfSignedP12Certificate besuCertificate = SelfSignedP12Certificate.create();
  private Path knownClientsFile;
  private Map<String, JsonRpcMethod> rpcMethods;
  private JsonRpcHttpService service;

  @Before
  public void beforeEach() throws IOException {
    knownClientsFile = folder.newFile().toPath();
    writeToKnownClientsFile(
        besuCertificate.getCommonName(),
        besuCertificate.getCertificateHexFingerprint(),
        knownClientsFile);
    final P2PNetwork peerDiscoveryMock = mock(P2PNetwork.class);
    final BlockchainQueries blockchainQueries = mock(BlockchainQueries.class);
    final Synchronizer synchronizer = mock(Synchronizer.class);

    final Set<Capability> supportedCapabilities = new HashSet<>();
    supportedCapabilities.add(EthProtocol.ETH62);
    supportedCapabilities.add(EthProtocol.ETH63);

    rpcMethods =
        spy(
            new JsonRpcMethodsFactory()
                .methods(
                    CLIENT_VERSION,
                    CHAIN_ID,
                    new StubGenesisConfigOptions(),
                    peerDiscoveryMock,
                    blockchainQueries,
                    synchronizer,
                    MainnetProtocolSchedule.fromConfig(
                        new StubGenesisConfigOptions().constantinopleBlock(0).chainId(CHAIN_ID)),
                    mock(ProtocolContext.class),
                    mock(FilterManager.class),
                    mock(TransactionPool.class),
                    mock(PoWMiningCoordinator.class),
                    new NoOpMetricsSystem(),
                    supportedCapabilities,
                    Optional.of(mock(AccountLocalConfigPermissioningController.class)),
                    Optional.of(mock(NodeLocalConfigPermissioningController.class)),
                    DEFAULT_RPC_APIS,
                    mock(PrivacyParameters.class),
                    mock(JsonRpcConfiguration.class),
                    mock(WebSocketConfiguration.class),
                    mock(MetricsConfiguration.class),
                    natService,
                    Collections.emptyMap(),
                    folder.getRoot().toPath(),
                    mock(EthPeers.class)));
  }

  @After
  public void shutdownServer() {
    Optional.ofNullable(service).ifPresent(s -> service.stop().join());
  }

  @Test
  public void exceptionRaisedWhenNonExistentKeystoreFileIsSpecified() throws IOException {
    Assertions.setMaxStackTraceElementsDisplayed(60);
    service =
        createJsonRpcHttpService(
            rpcMethods, createJsonRpcConfig(invalidKeystorePathTlsConfiguration()));
    assertThatExceptionOfType(CompletionException.class)
        .isThrownBy(
            () -> {
              service.start().join();
              Assertions.fail("service.start should have failed");
            })
        .withCauseInstanceOf(JsonRpcServiceException.class);
  }

  @Test
  public void exceptionRaisedWhenIncorrectKeystorePasswordIsSpecified() throws IOException {
    service =
        createJsonRpcHttpService(
            rpcMethods, createJsonRpcConfig(invalidPasswordTlsConfiguration()));
    assertThatExceptionOfType(CompletionException.class)
        .isThrownBy(
            () -> {
              service.start().join();
              Assertions.fail("service.start should have failed");
            })
        .withCauseInstanceOf(JsonRpcServiceException.class)
        .withMessageContaining("keystore password was incorrect");
  }

  @Test
  public void exceptionRaisedWhenIncorrectKeystorePasswordFileIsSpecified() throws IOException {
    service =
        createJsonRpcHttpService(
            rpcMethods, createJsonRpcConfig(invalidPasswordFileTlsConfiguration()));
    assertThatExceptionOfType(CompletionException.class)
        .isThrownBy(
            () -> {
              service.start().join();
              Assertions.fail("service.start should have failed");
            })
        .withCauseInstanceOf(JsonRpcServiceException.class)
        .withMessageContaining("Unable to read keystore password file");
  }

  @Test
  public void exceptionRaisedWhenInvalidKeystoreFileIsSpecified() throws IOException {
    service =
        createJsonRpcHttpService(
            rpcMethods, createJsonRpcConfig(invalidKeystoreFileTlsConfiguration()));
    assertThatExceptionOfType(CompletionException.class)
        .isThrownBy(
            () -> {
              service.start().join();
              Assertions.fail("service.start should have failed");
            })
        .withCauseInstanceOf(JsonRpcServiceException.class)
        .withMessageContaining("Short read of DER length");
  }

  @Test
  public void exceptionRaisedWhenInvalidKnownClientsFileIsSpecified() throws IOException {
    service =
        createJsonRpcHttpService(
            rpcMethods, createJsonRpcConfig(invalidKnownClientsTlsConfiguration()));
    assertThatExceptionOfType(CompletionException.class)
        .isThrownBy(
            () -> {
              service.start().join();
              Assertions.fail("service.start should have failed");
            })
        .withCauseInstanceOf(JsonRpcServiceException.class)
        .withMessageContaining("Invalid fingerprint in");
  }

  private TlsConfiguration invalidKeystoreFileTlsConfiguration() throws IOException {
    final File tempFile = folder.newFile();
    return aTlsConfiguration()
        .withKeyStorePath(tempFile.toPath())
        .withKeyStorePasswordSupplier(() -> "invalid_password")
        .withClientAuthConfiguration(
            aTlsClientAuthConfiguration().withKnownClientsFile(knownClientsFile).build())
        .build();
  }

  private TlsConfiguration invalidKeystorePathTlsConfiguration() {
    return aTlsConfiguration()
        .withKeyStorePath(Path.of("/tmp/invalidkeystore.pfx"))
        .withKeyStorePasswordSupplier(() -> "invalid_password")
        .withClientAuthConfiguration(
            aTlsClientAuthConfiguration().withKnownClientsFile(knownClientsFile).build())
        .build();
  }

  private TlsConfiguration invalidPasswordTlsConfiguration() {
    return aTlsConfiguration()
        .withKeyStorePath(besuCertificate.getKeyStoreFile())
        .withKeyStorePasswordSupplier(() -> "invalid_password")
        .withClientAuthConfiguration(
            aTlsClientAuthConfiguration().withKnownClientsFile(knownClientsFile).build())
        .build();
  }

  private TlsConfiguration invalidPasswordFileTlsConfiguration() {
    return TlsConfiguration.Builder.aTlsConfiguration()
        .withKeyStorePath(besuCertificate.getKeyStoreFile())
        .withKeyStorePasswordSupplier(
            new FileBasedPasswordProvider(Path.of("/tmp/invalid_password_file.txt")))
        .withClientAuthConfiguration(
            aTlsClientAuthConfiguration().withKnownClientsFile(knownClientsFile).build())
        .build();
  }

  private TlsConfiguration invalidKnownClientsTlsConfiguration() throws IOException {
    final Path tempKnownClientsFile = folder.newFile().toPath();
    Files.write(tempKnownClientsFile, List.of("cn invalid_sha256"));

    return TlsConfiguration.Builder.aTlsConfiguration()
        .withKeyStorePath(besuCertificate.getKeyStoreFile())
        .withKeyStorePasswordSupplier(() -> new String(besuCertificate.getPassword()))
        .withClientAuthConfiguration(
            aTlsClientAuthConfiguration().withKnownClientsFile(tempKnownClientsFile).build())
        .build();
  }

  private JsonRpcHttpService createJsonRpcHttpService(
      final Map<String, JsonRpcMethod> rpcMethods, final JsonRpcConfiguration jsonRpcConfig)
      throws IOException {
    return new JsonRpcHttpService(
        vertx,
        folder.newFolder().toPath(),
        jsonRpcConfig,
        new NoOpMetricsSystem(),
        natService,
        rpcMethods,
        HealthService.ALWAYS_HEALTHY,
        HealthService.ALWAYS_HEALTHY);
  }

  private JsonRpcConfiguration createJsonRpcConfig(final TlsConfiguration tlsConfiguration) {
    final JsonRpcConfiguration config = JsonRpcConfiguration.createDefault();
    config.setPort(0);
    config.setHostsAllowlist(Collections.singletonList("*"));
    config.setTlsConfiguration(Optional.of(tlsConfiguration));
    return config;
  }
}
