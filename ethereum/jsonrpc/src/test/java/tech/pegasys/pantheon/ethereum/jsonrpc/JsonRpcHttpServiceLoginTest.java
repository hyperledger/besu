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
package tech.pegasys.pantheon.ethereum.jsonrpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.config.StubGenesisConfigOptions;
import tech.pegasys.pantheon.ethereum.blockcreation.EthHashMiningCoordinator;
import tech.pegasys.pantheon.ethereum.core.PrivacyParameters;
import tech.pegasys.pantheon.ethereum.core.Synchronizer;
import tech.pegasys.pantheon.ethereum.core.TransactionPool;
import tech.pegasys.pantheon.ethereum.eth.EthProtocol;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.filter.FilterManager;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.JsonRpcMethod;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.queries.BlockchainQueries;
import tech.pegasys.pantheon.ethereum.mainnet.MainnetProtocolSchedule;
import tech.pegasys.pantheon.ethereum.p2p.api.P2PNetwork;
import tech.pegasys.pantheon.ethereum.p2p.wire.Capability;
import tech.pegasys.pantheon.ethereum.privacy.PrivateTransactionHandler;
import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
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
  protected static final int CHAIN_ID = 123;
  protected static P2PNetwork peerDiscoveryMock;
  protected static BlockchainQueries blockchainQueries;
  protected static Synchronizer synchronizer;
  protected static final Collection<RpcApi> JSON_RPC_APIS =
      Arrays.asList(RpcApis.ETH, RpcApis.NET, RpcApis.WEB3, RpcApis.ADMIN);
  private static StubAuthProvider stubCredentialProvider;
  private static final JWTAuthOptions jwtOptions =
      new JWTAuthOptions()
          .setPermissionsClaimKey("permissions")
          .addPubSecKey(
              new PubSecKeyOptions()
                  .setAlgorithm("HS256")
                  .setPublicKey("keyboard cat")
                  .setSymmetric(true));

  private static class StubAuthProvider implements AuthProvider {
    private Optional<User> respondUser = Optional.empty();
    private Optional<String> respondError = Optional.empty();

    @Override
    public void authenticate(
        final JsonObject authInfo, final Handler<AsyncResult<User>> resultHandler) {
      if (respondUser.isPresent()) {
        resultHandler.handle(Future.succeededFuture(respondUser.get()));
      } else if (respondError.isPresent()) {
        resultHandler.handle(Future.failedFuture(respondError.get()));
      } else {
        throw new IllegalStateException("Setup your auth provider stub");
      }
    }

    public void setRespondUser(final User respondUser) {
      this.respondError = Optional.empty();
      this.respondUser = Optional.of(respondUser);
    }

    public void setRespondError(final String respondError) {
      this.respondUser = Optional.empty();
      this.respondError = Optional.of(respondError);
    }
  }

  @BeforeClass
  public static void initServerAndClient() throws Exception {
    stubCredentialProvider = new StubAuthProvider();
    peerDiscoveryMock = mock(P2PNetwork.class);
    blockchainQueries = mock(BlockchainQueries.class);
    synchronizer = mock(Synchronizer.class);

    final Set<Capability> supportedCapabilities = new HashSet<>();
    supportedCapabilities.add(EthProtocol.ETH62);
    supportedCapabilities.add(EthProtocol.ETH63);

    rpcMethods =
        spy(
            new JsonRpcMethodsFactory()
                .methods(
                    CLIENT_VERSION,
                    peerDiscoveryMock,
                    blockchainQueries,
                    synchronizer,
                    MainnetProtocolSchedule.fromConfig(
                        new StubGenesisConfigOptions().constantinopleBlock(0).chainId(CHAIN_ID),
                        PrivacyParameters.noPrivacy()),
                    mock(FilterManager.class),
                    mock(TransactionPool.class),
                    mock(EthHashMiningCoordinator.class),
                    new NoOpMetricsSystem(),
                    supportedCapabilities,
                    Optional.empty(),
                    JSON_RPC_APIS,
                    mock(PrivateTransactionHandler.class)));
    service = createJsonRpcHttpService();
    service.start().join();

    // Build an OkHttp client.
    client = new OkHttpClient();
    baseUrl = service.url();
  }

  private static JsonRpcHttpService createJsonRpcHttpService() throws Exception {
    return new JsonRpcHttpService(
        vertx,
        folder.newFolder().toPath(),
        createJsonRpcConfig(),
        new NoOpMetricsSystem(),
        rpcMethods,
        jwtOptions,
        stubCredentialProvider);
  }

  private static JsonRpcConfiguration createJsonRpcConfig() {
    final JsonRpcConfiguration config = JsonRpcConfiguration.createDefault();
    config.setPort(0);
    config.setHostsWhitelist(Collections.singletonList("*"));
    return config;
  }

  /** Tears down the HTTP server. */
  @AfterClass
  public static void shutdownServer() {
    service.stop().join();
  }

  @Test
  public void loginWithBadCredentials() throws IOException {
    stubCredentialProvider.setRespondError("Invalid password");

    final RequestBody body =
        RequestBody.create(JSON, "{\"username\":\"user\",\"password\":\"pass\"}");
    final Request request = new Request.Builder().post(body).url(baseUrl + "/login").build();
    try (final Response resp = client.newCall(request).execute()) {
      assertThat(resp.code()).isEqualTo(401);
      assertThat(resp.message()).isEqualTo("Unauthorized");
    }
  }

  @Test
  public void loginWithGoodCredentials() throws IOException {
    final User mockUser = mock(User.class);
    stubCredentialProvider.setRespondUser(mockUser);
    when(mockUser.principal()).thenReturn(new JsonObject());

    final RequestBody body =
        RequestBody.create(JSON, "{\"username\":\"user\",\"password\":\"pass\"}");
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

      final JWTAuth auth = JWTAuth.create(vertx, jwtOptions);

      auth.authenticate(
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
    final User mockUser = mock(User.class);
    stubCredentialProvider.setRespondUser(mockUser);
    when(mockUser.principal())
        .thenReturn(
            new JsonObject()
                .put("permissions", new JsonArray(Collections.singletonList("fakePermission"))));

    final RequestBody body =
        RequestBody.create(JSON, "{\"username\":\"user\",\"password\":\"pass\"}");
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

      final JWTAuth auth = JWTAuth.create(vertx, jwtOptions);

      auth.authenticate(
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
}
