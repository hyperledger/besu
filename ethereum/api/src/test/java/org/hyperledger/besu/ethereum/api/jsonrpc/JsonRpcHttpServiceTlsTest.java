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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import org.hyperledger.besu.config.StubGenesisConfigOptions;
import org.hyperledger.besu.ethereum.api.jsonrpc.health.HealthService;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.filter.FilterManager;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.methods.JsonRpcMethodsFactory;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.WebSocketConfiguration;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.tls.TlsConfiguration;
import org.hyperledger.besu.ethereum.api.tls.TlsStoreConfiguration;
import org.hyperledger.besu.ethereum.blockcreation.EthHashMiningCoordinator;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.Synchronizer;
import org.hyperledger.besu.ethereum.eth.EthProtocol;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.MainnetProtocolSchedule;
import org.hyperledger.besu.ethereum.p2p.network.P2PNetwork;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.ethereum.permissioning.AccountLocalConfigPermissioningController;
import org.hyperledger.besu.ethereum.permissioning.NodeLocalConfigPermissioningController;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.metrics.prometheus.MetricsConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class JsonRpcHttpServiceTlsTest {

  @ClassRule public static final TemporaryFolder folder = new TemporaryFolder();

  protected static final Vertx vertx = Vertx.vertx();

  private static Map<String, JsonRpcMethod> rpcMethods;
  private static JsonRpcHttpService service;
  private static String baseUrl;
  private static final String JSON_HEADER = "application/json; charset=utf-8";
  private static final String CLIENT_VERSION = "TestClientVersion/0.1.0";
  private static final BigInteger CHAIN_ID = BigInteger.valueOf(123);
  private static final Collection<RpcApi> JSON_RPC_APIS =
      Arrays.asList(RpcApis.ETH, RpcApis.NET, RpcApis.WEB3);
  private final JsonRpcConfiguration jsonRpcConfig = createJsonRpcConfig();
  private final JsonRpcTestHelper testHelper = new JsonRpcTestHelper();
  private static final String KEYSTORE_RESOURCE = "JsonRpcHttpService/rpc_keystore.pfx";
  private static final String KEYSTORE_CLIENT_RESOURCE =
      "JsonRpcHttpService/rpc_client_keystore.pfx";
  private static final String KEYSTORE_PASSWORD_RESOURCE =
      "JsonRpcHttpService/rpc_keystore.password";
  private static final String KNOWN_CLIENTS_RESOURCE = "JsonRpcHttpService/rpc_known_clients.txt";
  @Before
  public void initServerAndClient() throws Exception {
    System.setProperty("javax.net.debug", "ssl, handshake");

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
                    mock(FilterManager.class),
                    mock(TransactionPool.class),
                    mock(EthHashMiningCoordinator.class),
                    new NoOpMetricsSystem(),
                    supportedCapabilities,
                    Optional.of(mock(AccountLocalConfigPermissioningController.class)),
                    Optional.of(mock(NodeLocalConfigPermissioningController.class)),
                    JSON_RPC_APIS,
                    mock(PrivacyParameters.class),
                    mock(JsonRpcConfiguration.class),
                    mock(WebSocketConfiguration.class),
                    mock(MetricsConfiguration.class)));
    service = createJsonRpcHttpService();
    service.start().join();
    baseUrl = service.url();
  }

  private JsonRpcHttpService createJsonRpcHttpService() throws Exception {
    return new JsonRpcHttpService(
        vertx,
        folder.newFolder().toPath(),
        jsonRpcConfig,
        new NoOpMetricsSystem(),
        Optional.empty(),
        rpcMethods,
        HealthService.ALWAYS_HEALTHY,
        HealthService.ALWAYS_HEALTHY);
  }

  private static JsonRpcConfiguration createJsonRpcConfig() {
    final JsonRpcConfiguration config = JsonRpcConfiguration.createDefault();
    config.setPort(0);
    config.setHostsWhitelist(Collections.singletonList("*"));
    config.setTlsConfiguration(getRpcHttpTlsConfiguration());
    return config;
  }

  private static TlsConfiguration getRpcHttpTlsConfiguration() {
    return new TlsConfiguration(
        new TlsStoreConfiguration(getKeyStorePath(), getKeystorePassword()),
        Optional.of(getKnownClientsFile()));
  }

  private static String getKeyStorePath() {
    try {
      return Paths.get(ClassLoader.getSystemResource(KEYSTORE_RESOURCE).toURI())
          .toAbsolutePath()
          .toString();
    } catch (URISyntaxException e) {
      throw new RuntimeException("Unable to read keystore resource.", e);
    }
  }

  private static String getKeystorePassword() {
    try {
      final Path keyStorePassdwordFile =
          Paths.get(
                  ClassLoader.getSystemResource(
                          JsonRpcHttpServiceTlsTest.KEYSTORE_PASSWORD_RESOURCE)
                      .toURI())
              .toAbsolutePath();

      return Files.asCharSource(keyStorePassdwordFile.toFile(), Charsets.UTF_8).readFirstLine();
    } catch (URISyntaxException | IOException e) {
      throw new RuntimeException("Unable to read keystore password file", e);
    }
  }

  private static Path getKnownClientsFile() {
    try {
      return Paths.get(ClassLoader.getSystemResource(KNOWN_CLIENTS_RESOURCE).toURI())
          .toAbsolutePath();
    } catch (URISyntaxException e) {
      throw new RuntimeException("Unable to read keystore resource.", e);
    }
  }

  @After
  public void shutdownServer() {
    service.stop().join();
    System.clearProperty("javax.net.debug");
  }

  @Test
  public void netVersionSuccessfulOnTls() throws Exception {
    final String id = "123";
    final String json =
        "{\"jsonrpc\":\"2.0\",\"id\":" + Json.encode(id) + ",\"method\":\"net_version\"}";
    final HttpClient httpClient = getHttpClient();

    final HttpRequest request =
        HttpRequest.newBuilder()
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .uri(URI.create(baseUrl))
            .header("Accept", JSON_HEADER)
            .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    // Check general format of result
    final JsonObject jsonObject = new JsonObject(response.body());
    testHelper.assertValidJsonRpcResult(jsonObject, id);
    // Check result
    final String result = jsonObject.getString("result");
    assertThat(result).isEqualTo(String.valueOf(CHAIN_ID));
  }

  private HttpClient getHttpClient() {
    try {
      final TrustManagerFactory trustManagerFactory = getTrustManagerFactory();
      final KeyManagerFactory keyManagerFactory = getKeyManagerFactory();
      final SSLContext sslContext = getCustomSslContext(keyManagerFactory, trustManagerFactory);
      final SSLParameters sslParameters = sslContext.getDefaultSSLParameters();
      sslParameters.setNeedClientAuth(true);
      sslParameters.setWantClientAuth(true);

      return HttpClient.newBuilder().sslContext(sslContext).sslParameters(sslParameters).build();

    } catch (GeneralSecurityException e) {
      throw new RuntimeException(e);
    }
  }

  private TrustManagerFactory getTrustManagerFactory() throws GeneralSecurityException {
    final String keystorePassword = getKeystorePassword();
    final KeyStore trustStore = loadP12KeyStore(KEYSTORE_RESOURCE, keystorePassword);
    final TrustManagerFactory trustManagerFactory =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    trustManagerFactory.init(trustStore);
    return trustManagerFactory;
  }

  private KeyManagerFactory getKeyManagerFactory() throws GeneralSecurityException {
    final String keystorePassword = getKeystorePassword();
    final KeyStore keyStore = loadP12KeyStore(KEYSTORE_CLIENT_RESOURCE, keystorePassword);
    final KeyManagerFactory keyManagerFactory =
        KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    keyManagerFactory.init(keyStore, keystorePassword.toCharArray());
    return keyManagerFactory;
  }

  private SSLContext getCustomSslContext(
      final KeyManagerFactory keyManagerFactory, final TrustManagerFactory trustManagerFactory)
      throws GeneralSecurityException {
    final SSLContext sslContext = SSLContext.getInstance("TLS");
    sslContext.init(
        keyManagerFactory.getKeyManagers(),
        trustManagerFactory.getTrustManagers(),
        SecureRandom.getInstanceStrong());
    return sslContext;
  }

  private KeyStore loadP12KeyStore(final String resource, final String password)
      throws KeyStoreException, NoSuchAlgorithmException, CertificateException {
    final KeyStore store = KeyStore.getInstance("pkcs12");
    try (final InputStream keystoreStream = ClassLoader.getSystemResource(resource).openStream()) {
      store.load(keystoreStream, password.toCharArray());
    } catch (IOException e) {
      throw new RuntimeException("Unable to load keystore.", e);
    }
    return store;
  }
}
