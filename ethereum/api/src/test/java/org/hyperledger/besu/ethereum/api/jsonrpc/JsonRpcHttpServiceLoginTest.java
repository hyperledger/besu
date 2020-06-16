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

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.util.Lists.list;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import org.hyperledger.besu.config.StubGenesisConfigOptions;
import org.hyperledger.besu.ethereum.api.jsonrpc.authentication.AuthenticationUtils;
import org.hyperledger.besu.ethereum.api.jsonrpc.health.HealthService;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.filter.FilterManager;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.EthAccounts;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.EthBlockNumber;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.NetVersion;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.Web3ClientVersion;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.Web3Sha3;
import org.hyperledger.besu.ethereum.api.jsonrpc.methods.JsonRpcMethodsFactory;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.WebSocketConfiguration;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.blockcreation.EthHashMiningCoordinator;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.Synchronizer;
import org.hyperledger.besu.ethereum.eth.EthProtocol;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.MainnetProtocolSchedule;
import org.hyperledger.besu.ethereum.p2p.network.P2PNetwork;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.metrics.prometheus.MetricsConfiguration;
import org.hyperledger.besu.nat.NatService;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Paths;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.google.common.base.Splitter;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.jwt.JWTAuth;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.assertj.core.api.Assertions;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class JsonRpcHttpServiceLoginTest {
  @ClassRule public static final TemporaryFolder folder = new TemporaryFolder();

  private static final Vertx vertx = Vertx.vertx();

  protected static Map<String, JsonRpcMethod> rpcMethods;
  protected static JsonRpcHttpService service;
  protected static OkHttpClient client;
  protected static String baseUrl;
  protected static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
  protected static final String CLIENT_VERSION = "TestClientVersion/0.1.0";
  protected static final BigInteger CHAIN_ID = BigInteger.valueOf(123);
  protected static P2PNetwork peerDiscoveryMock;
  protected static BlockchainQueries blockchainQueries;
  protected static Synchronizer synchronizer;
  protected static final Collection<RpcApi> JSON_RPC_APIS =
      Arrays.asList(RpcApis.ETH, RpcApis.NET, RpcApis.WEB3, RpcApis.ADMIN);
  protected static JWTAuth jwtAuth;
  protected static String authPermissionsConfigFilePath = "JsonRpcHttpService/auth.toml";
  protected final JsonRpcTestHelper testHelper = new JsonRpcTestHelper();
  protected static final NatService natService = new NatService(Optional.empty());

  @BeforeClass
  public static void initServerAndClient() throws Exception {
    peerDiscoveryMock = mock(P2PNetwork.class);
    blockchainQueries = mock(BlockchainQueries.class);
    synchronizer = mock(Synchronizer.class);

    final Set<Capability> supportedCapabilities = new HashSet<>();
    supportedCapabilities.add(EthProtocol.ETH62);
    supportedCapabilities.add(EthProtocol.ETH63);

    final StubGenesisConfigOptions genesisConfigOptions =
        new StubGenesisConfigOptions().constantinopleBlock(0).chainId(CHAIN_ID);

    rpcMethods =
        spy(
            new JsonRpcMethodsFactory()
                .methods(
                    CLIENT_VERSION,
                    CHAIN_ID,
                    genesisConfigOptions,
                    peerDiscoveryMock,
                    blockchainQueries,
                    synchronizer,
                    MainnetProtocolSchedule.fromConfig(genesisConfigOptions),
                    mock(FilterManager.class),
                    mock(TransactionPool.class),
                    mock(EthHashMiningCoordinator.class),
                    new NoOpMetricsSystem(),
                    supportedCapabilities,
                    Optional.empty(),
                    Optional.empty(),
                    JSON_RPC_APIS,
                    mock(PrivacyParameters.class),
                    mock(JsonRpcConfiguration.class),
                    mock(WebSocketConfiguration.class),
                    mock(MetricsConfiguration.class),
                    natService,
                    new HashMap<>()));
    service = createJsonRpcHttpService();
    jwtAuth = service.authenticationService.get().getJwtAuthProvider();
    service.start().join();

    // Build an OkHttp client.
    client = new OkHttpClient();
    baseUrl = service.url();
  }

  private static JsonRpcHttpService createJsonRpcHttpService() throws Exception {
    final String authTomlPath =
        Paths.get(ClassLoader.getSystemResource(authPermissionsConfigFilePath).toURI())
            .toAbsolutePath()
            .toString();

    final JsonRpcConfiguration config = createJsonRpcConfig();
    config.setAuthenticationEnabled(true);
    config.setAuthenticationCredentialsFile(authTomlPath);

    return new JsonRpcHttpService(
        vertx,
        folder.newFolder().toPath(),
        config,
        new NoOpMetricsSystem(),
        natService,
        rpcMethods,
        HealthService.ALWAYS_HEALTHY,
        HealthService.ALWAYS_HEALTHY);
  }

  private static JsonRpcConfiguration createJsonRpcConfig() {
    final JsonRpcConfiguration config = JsonRpcConfiguration.createDefault();
    config.setPort(0);
    config.setHostsAllowlist(Collections.singletonList("*"));
    return config;
  }

  /** Tears down the HTTP server. */
  @AfterClass
  public static void shutdownServer() {
    service.stop().join();
  }

  @Test
  public void loginWithBadCredentials() throws IOException {
    final RequestBody body =
        RequestBody.create(JSON, "{\"username\":\"user\",\"password\":\"badpass\"}");
    final Request request = new Request.Builder().post(body).url(baseUrl + "/login").build();
    try (final Response resp = client.newCall(request).execute()) {
      assertThat(resp.code()).isEqualTo(401);
      assertThat(resp.message()).isEqualTo("Unauthorized");
    }
  }

  @Test
  public void loginWithGoodCredentials() throws IOException {
    final RequestBody body =
        RequestBody.create(JSON, "{\"username\":\"user\",\"password\":\"pegasys\"}");
    final Request request = new Request.Builder().post(body).url(baseUrl + "/login").build();
    try (final Response resp = client.newCall(request).execute()) {
      assertThat(resp.code()).isEqualTo(200);
      assertThat(resp.message()).isEqualTo("OK");
      assertThat(resp.body().contentType()).isNotNull();
      assertThat(resp.body().contentType().type()).isEqualTo("application");
      assertThat(resp.body().contentType().subtype()).isEqualTo("json");
      final String bodyString = resp.body().string();
      assertThat(bodyString).isNotNull();
      assertThat(bodyString).isNotBlank();

      final JsonObject respBody = new JsonObject(bodyString);
      final String token = respBody.getString("token");
      assertThat(token).isNotNull();

      jwtAuth.authenticate(
          new JsonObject().put("jwt", token),
          (r) -> {
            assertThat(r.succeeded()).isTrue();
            final User user = r.result();
            user.isAuthorized(
                "noauths",
                (authed) -> {
                  assertThat(authed.succeeded()).isTrue();
                  assertThat(authed.result()).isFalse();
                });
          });
    }
  }

  @Test
  public void loginWithGoodCredentialsAndPermissions() throws IOException {
    final RequestBody body =
        RequestBody.create(JSON, "{\"username\":\"user\",\"password\":\"pegasys\"}");
    final Request request = new Request.Builder().post(body).url(baseUrl + "/login").build();
    try (final Response resp = client.newCall(request).execute()) {
      assertThat(resp.code()).isEqualTo(200);
      assertThat(resp.message()).isEqualTo("OK");
      assertThat(resp.body().contentType()).isNotNull();
      assertThat(resp.body().contentType().type()).isEqualTo("application");
      assertThat(resp.body().contentType().subtype()).isEqualTo("json");
      final String bodyString = resp.body().string();
      assertThat(bodyString).isNotNull();
      assertThat(bodyString).isNotBlank();

      final JsonObject respBody = new JsonObject(bodyString);
      final String token = respBody.getString("token");
      assertThat(token).isNotNull();

      jwtAuth.authenticate(
          new JsonObject().put("jwt", token),
          (r) -> {
            assertThat(r.succeeded()).isTrue();
            final User user = r.result();
            user.isAuthorized(
                "noauths",
                (authed) -> {
                  assertThat(authed.succeeded()).isTrue();
                  assertThat(authed.result()).isFalse();
                });
            user.isAuthorized(
                "fakePermission",
                (authed) -> {
                  assertThat(authed.succeeded()).isTrue();
                  assertThat(authed.result()).isTrue();
                });
          });
    }
  }

  @Test
  public void loginDoesntPopulateJWTPayloadWithPassword()
      throws IOException, KeyStoreException, CertificateException, NoSuchAlgorithmException {
    final RequestBody body =
        RequestBody.create(JSON, "{\"username\":\"user\",\"password\":\"pegasys\"}");
    final Request request = new Request.Builder().post(body).url(baseUrl + "/login").build();
    try (final Response resp = client.newCall(request).execute()) {
      assertThat(resp.code()).isEqualTo(200);
      assertThat(resp.message()).isEqualTo("OK");
      assertThat(resp.body().contentType()).isNotNull();
      assertThat(resp.body().contentType().type()).isEqualTo("application");
      assertThat(resp.body().contentType().subtype()).isEqualTo("json");
      final String bodyString = resp.body().string();
      assertThat(bodyString).isNotNull();
      assertThat(bodyString).isNotBlank();

      final JsonObject respBody = new JsonObject(bodyString);
      final String token = respBody.getString("token");
      assertThat(token).isNotNull();

      final JsonObject jwtPayload = decodeJwtPayload(token);
      final String jwtPayloadString = jwtPayload.encode();
      assertThat(jwtPayloadString.contains("password")).isFalse();
      assertThat(jwtPayloadString.contains("pegasys")).isFalse();
    }
  }

  @Test
  public void loginPopulatesJWTPayloadWithRequiredValues()
      throws IOException, KeyStoreException, CertificateException, NoSuchAlgorithmException {
    final RequestBody body =
        RequestBody.create(JSON, "{\"username\":\"user\",\"password\":\"pegasys\"}");
    final Request request = new Request.Builder().post(body).url(baseUrl + "/login").build();
    try (final Response resp = client.newCall(request).execute()) {
      assertThat(resp.code()).isEqualTo(200);
      assertThat(resp.message()).isEqualTo("OK");
      assertThat(resp.body().contentType()).isNotNull();
      assertThat(resp.body().contentType().type()).isEqualTo("application");
      assertThat(resp.body().contentType().subtype()).isEqualTo("json");
      final String bodyString = resp.body().string();
      assertThat(bodyString).isNotNull();
      assertThat(bodyString).isNotBlank();

      final JsonObject respBody = new JsonObject(bodyString);
      final String token = respBody.getString("token");
      assertThat(token).isNotNull();

      final JsonObject jwtPayload = decodeJwtPayload(token);
      assertThat(jwtPayload.getString("username")).isEqualTo("user");
      assertThat(jwtPayload.getJsonArray("permissions"))
          .isEqualTo(
              new JsonArray(list("fakePermission", "eth:blockNumber", "eth:subscribe", "web3:*")));
      assertThat(jwtPayload.getString("privacyPublicKey"))
          .isEqualTo("A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=");
      assertThat(jwtPayload.containsKey("iat")).isTrue();
      assertThat(jwtPayload.containsKey("exp")).isTrue();
      final long tokenExpiry = jwtPayload.getLong("exp") - jwtPayload.getLong("iat");
      assertThat(tokenExpiry).isEqualTo(MINUTES.toSeconds(5));
    }
  }

  private String login(final String username, final String password) throws IOException {
    final RequestBody loginBody =
        RequestBody.create(
            JSON, "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
    final Request loginRequest =
        new Request.Builder().post(loginBody).url(baseUrl + "/login").build();
    final String token;
    try (final Response loginResp = client.newCall(loginRequest).execute()) {
      assertThat(loginResp.code()).isEqualTo(200);
      assertThat(loginResp.message()).isEqualTo("OK");
      assertThat(loginResp.body().contentType()).isNotNull();
      assertThat(loginResp.body().contentType().type()).isEqualTo("application");
      assertThat(loginResp.body().contentType().subtype()).isEqualTo("json");
      final String bodyString = loginResp.body().string();
      assertThat(bodyString).isNotNull();
      assertThat(bodyString).isNotBlank();

      final JsonObject respBody = new JsonObject(bodyString);
      token = respBody.getString("token");
      assertThat(token).isNotNull();
    }
    return token;
  }

  @Test
  public void checkJsonRpcMethodsAvailableWithGoodCredentialsAndPermissions() throws IOException {
    final RequestBody body =
        RequestBody.create(JSON, "{\"username\":\"user\",\"password\":\"pegasys\"}");
    final Request request = new Request.Builder().post(body).url(baseUrl + "/login").build();
    try (final Response resp = client.newCall(request).execute()) {
      assertThat(resp.code()).isEqualTo(200);
      assertThat(resp.message()).isEqualTo("OK");
      assertThat(resp.body().contentType()).isNotNull();
      assertThat(resp.body().contentType().type()).isEqualTo("application");
      assertThat(resp.body().contentType().subtype()).isEqualTo("json");
      final String bodyString = resp.body().string();
      assertThat(bodyString).isNotNull();
      assertThat(bodyString).isNotBlank();

      final JsonObject respBody = new JsonObject(bodyString);
      final String token = respBody.getString("token");
      assertThat(token).isNotNull();

      final JsonRpcMethod ethAccounts = new EthAccounts();
      final JsonRpcMethod netVersion = new NetVersion(Optional.of(BigInteger.valueOf(123)));
      final JsonRpcMethod ethBlockNumber = new EthBlockNumber(blockchainQueries);
      final JsonRpcMethod web3Sha3 = new Web3Sha3();
      final JsonRpcMethod web3ClientVersion = new Web3ClientVersion("777");

      jwtAuth.authenticate(
          new JsonObject().put("jwt", token),
          (r) -> {
            assertThat(r.succeeded()).isTrue();
            final User user = r.result();
            // single eth/blockNumber method permitted
            Assertions.assertThat(
                    AuthenticationUtils.isPermitted(
                        service.authenticationService, Optional.of(user), ethBlockNumber))
                .isTrue();
            // eth/accounts not permitted
            assertThat(
                    AuthenticationUtils.isPermitted(
                        service.authenticationService, Optional.of(user), ethAccounts))
                .isFalse();
            // allowed by web3/*
            assertThat(
                    AuthenticationUtils.isPermitted(
                        service.authenticationService, Optional.of(user), web3ClientVersion))
                .isTrue();
            assertThat(
                    AuthenticationUtils.isPermitted(
                        service.authenticationService, Optional.of(user), web3Sha3))
                .isTrue();
            // no net permissions
            assertThat(
                    AuthenticationUtils.isPermitted(
                        service.authenticationService, Optional.of(user), netVersion))
                .isFalse();
          });
    }
  }

  @Test
  public void checkPermissionsWithEmptyUser() {
    final JsonRpcMethod ethAccounts = new EthAccounts();

    assertThat(
            AuthenticationUtils.isPermitted(
                service.authenticationService, Optional.empty(), ethAccounts))
        .isFalse();
  }

  @Test
  public void web3ClientVersionUnsuccessfulBeforeLogin() throws Exception {
    final String id = "123";
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":"
                + Json.encode(id)
                + ",\"method\":\"web3_clientVersion\"}");

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      assertThat(resp.code()).isEqualTo(401);
      assertThat(resp.message()).isEqualTo("Unauthorized");
    }
  }

  @Test
  public void web3ClientVersionUnsuccessfulWithBadBearer() throws Exception {
    final String id = "123";
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":"
                + Json.encode(id)
                + ",\"method\":\"web3_clientVersion\"}");

    try (final Response resp = client.newCall(buildPostRequest(body, "badtoken")).execute()) {
      assertThat(resp.code()).isEqualTo(401);
      assertThat(resp.message()).isEqualTo("Unauthorized");
    }
  }

  @Test
  public void web3ClientVersionSuccessfulAfterLogin() throws Exception {
    final String token = login("user", "pegasys");

    final String id = "123";
    final RequestBody web3ClientVersionBody =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":"
                + Json.encode(id)
                + ",\"method\":\"web3_clientVersion\"}");

    try (final Response web3ClientVersionResp =
        client.newCall(buildPostRequest(web3ClientVersionBody, token)).execute()) {
      assertThat(web3ClientVersionResp.code()).isEqualTo(200);
      // Check general format of result
      final JsonObject json = new JsonObject(web3ClientVersionResp.body().string());
      testHelper.assertValidJsonRpcResult(json, id);
      // Check result
      final String result = json.getString("result");
      assertThat(result).isEqualTo("TestClientVersion/0.1.0");
    }
  }

  @Test
  public void ethSyncingUnauthorisedWithoutPermission() throws Exception {
    final String token = login("user", "pegasys");

    final String id = "007";
    final RequestBody body =
        RequestBody.create(
            JSON,
            "{\"jsonrpc\":\"2.0\",\"id\":" + Json.encode(id) + ",\"method\":\"eth_syncing\"}");

    try (final Response resp = client.newCall(buildPostRequest(body, token)).execute()) {
      assertThat(resp.code()).isEqualTo(401);
      assertThat(resp.message()).isEqualTo("Unauthorized");
    }
  }

  private Request buildPostRequest(final RequestBody body) {
    return buildPostRequest(body, Optional.empty());
  }

  private Request buildPostRequest(final RequestBody body, final String token) {
    return buildPostRequest(body, Optional.of(token));
  }

  private Request buildPostRequest(final RequestBody body, final Optional<String> token) {
    final Request.Builder request = new Request.Builder().post(body).url(baseUrl);
    token.ifPresent(t -> request.addHeader("Authorization", "Bearer " + t));
    return request.build();
  }

  private JsonObject decodeJwtPayload(final String token) {
    final List<String> tokenParts = Splitter.on('.').splitToList(token);
    final String payload = tokenParts.get(1);
    return new JsonObject(new String(Base64.getUrlDecoder().decode(payload), UTF_8));
  }
}
