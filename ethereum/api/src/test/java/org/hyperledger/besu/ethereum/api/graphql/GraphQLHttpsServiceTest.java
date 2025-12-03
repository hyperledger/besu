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
package org.hyperledger.besu.ethereum.api.graphql;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.query.BlockWithMetadata;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.api.query.TransactionWithMetadata;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Synchronizer;
import org.hyperledger.besu.ethereum.eth.EthProtocol;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Capability;
import org.hyperledger.besu.testutil.BlockTestUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.Writer;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import graphql.GraphQL;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.tuweni.bytes.Bytes;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

public class GraphQLHttpsServiceTest {

  // this tempDir is deliberately static
  @TempDir private static Path folder;

  private static final Vertx vertx = Vertx.vertx();

  private static GraphQLHttpService service;
  private static OkHttpClient client;
  private static String baseUrl;
  protected static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
  protected static final MediaType GRAPHQL = MediaType.parse("application/graphql; charset=utf-8");
  private static final String EXPECTED_CHAIN_ID = "0x1";
  private static BlockchainQueries blockchainQueries;
  private static GraphQL graphQL;
  private static Map<GraphQLContextType, Object> graphQlContextMap;

  private final GraphQLTestHelper testHelper = new GraphQLTestHelper();
  // Generate a self-signed certificate
  private static SelfSignedCertificate ssc;
  private static SelfSignedCertificate clientSsc;

  @BeforeAll
  public static void initServerAndClient() throws Exception {
    blockchainQueries = Mockito.mock(BlockchainQueries.class);
    final Synchronizer synchronizer = Mockito.mock(Synchronizer.class);
    graphQL = Mockito.mock(GraphQL.class);
    ssc = new SelfSignedCertificate();
    clientSsc = new SelfSignedCertificate();

    graphQlContextMap =
        Map.of(
            GraphQLContextType.BLOCKCHAIN_QUERIES,
            blockchainQueries,
            GraphQLContextType.TRANSACTION_POOL,
            Mockito.mock(TransactionPool.class),
            GraphQLContextType.CHAIN_ID,
            Optional.of(BigInteger.valueOf(1)),
            GraphQLContextType.SYNCHRONIZER,
            synchronizer);

    final Set<Capability> supportedCapabilities = new HashSet<>();
    supportedCapabilities.add(EthProtocol.LATEST);
    final GraphQLDataFetchers dataFetchers = new GraphQLDataFetchers(supportedCapabilities);
    graphQL = GraphQLProvider.buildGraphQL(dataFetchers);
    service = createGraphQLHttpService();
    service.start().join();
    // Build an OkHttp client.
    client = createHttpClientforMtls();
    baseUrl = service.url() + "/graphql/";
  }

  public static OkHttpClient createHttpClientforMtls() throws Exception {

    // Create a temporary truststore file
    File truststoreFile = File.createTempFile("truststore", ".jks");
    truststoreFile.deleteOnExit();

    // Create a PKCS12 truststore and load the server's certificate
    KeyStore trustStore = KeyStore.getInstance("JKS");
    trustStore.load(null, null);
    trustStore.setCertificateEntry("alias", ssc.cert());

    // Save the truststore to the temporary file
    try (FileOutputStream fos = new FileOutputStream(truststoreFile)) {
      trustStore.store(fos, "password".toCharArray());
    }

    // Create TrustManagerFactory
    TrustManagerFactory trustManagerFactory =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    trustManagerFactory.init(trustStore);

    // Get TrustManagers
    TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();

    // Create a temporary keystore file
    File keystoreFile = File.createTempFile("keystore", ".jks");
    keystoreFile.deleteOnExit();

    // Create a PKCS12 keystore and load the client's certificate
    KeyStore keyStore = KeyStore.getInstance("JKS");
    keyStore.load(null, "password".toCharArray());
    keyStore.setKeyEntry(
        "alias",
        clientSsc.key(),
        "password".toCharArray(),
        new java.security.cert.Certificate[] {clientSsc.cert()});

    // Save the keystore to the temporary file
    try (FileOutputStream fos = new FileOutputStream(keystoreFile)) {
      keyStore.store(fos, "password".toCharArray());
    }

    // Create KeyManagerFactory
    KeyManagerFactory keyManagerFactory =
        KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    keyManagerFactory.init(keyStore, "password".toCharArray());

    // Get KeyManagers
    KeyManager[] keyManagers = keyManagerFactory.getKeyManagers();

    // Initialize SSLContext
    SSLContext sslContext = SSLContext.getInstance("TLS");
    // Obtain a SecureRandom instance
    SecureRandom secureRandom = SecureRandom.getInstanceStrong();

    // Initialize SSLContext
    sslContext.init(keyManagers, trustManagers, secureRandom);

    if (!(trustManagers[0] instanceof X509TrustManager)) {
      throw new IllegalStateException(
          "Unexpected default trust managers: " + Arrays.toString(trustManagers));
    }

    // Create OkHttpClient with custom SSLSocketFactory and TrustManager
    return new OkHttpClient.Builder()
        .sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustManagers[0])
        .hostnameVerifier((hostname, session) -> "localhost".equals(hostname))
        .followRedirects(false)
        .build();
  }

  private static GraphQLHttpService createGraphQLHttpService(final GraphQLConfiguration config)
      throws Exception {
    return new GraphQLHttpService(
        vertx, folder, config, graphQL, graphQlContextMap, Mockito.mock(EthScheduler.class));
  }

  private static GraphQLHttpService createGraphQLHttpService() throws Exception {
    return new GraphQLHttpService(
        vertx,
        folder,
        createGraphQLConfig(),
        graphQL,
        graphQlContextMap,
        Mockito.mock(EthScheduler.class));
  }

  private static GraphQLConfiguration createGraphQLConfig() throws Exception {
    final GraphQLConfiguration config = GraphQLConfiguration.createDefault();

    // Create a temporary keystore file
    File keystoreFile = File.createTempFile("keystore", ".jks");
    keystoreFile.deleteOnExit();

    // Create a PKCS12 keystore and load the self-signed certificate
    KeyStore keyStore = KeyStore.getInstance("JKS");
    keyStore.load(null, "password".toCharArray());
    keyStore.setKeyEntry(
        "alias",
        ssc.key(),
        "password".toCharArray(),
        new java.security.cert.Certificate[] {ssc.cert()});

    // Save the keystore to the temporary file
    FileOutputStream fos = new FileOutputStream(keystoreFile);
    keyStore.store(fos, "password".toCharArray());

    // Create a temporary password file
    File keystorePasswordFile = File.createTempFile("keystorePassword", ".txt");
    keystorePasswordFile.deleteOnExit();
    try (Writer writer =
        Files.newBufferedWriter(keystorePasswordFile.toPath(), Charset.defaultCharset())) {
      writer.write("password");
    }

    // Create a temporary truststore file
    File truststoreFile = File.createTempFile("truststore", ".jks");
    truststoreFile.deleteOnExit();

    // Create a JKS truststore and load the client's certificate
    KeyStore trustStore = KeyStore.getInstance("JKS");
    trustStore.load(null, "password".toCharArray());
    trustStore.setCertificateEntry("clientAlias", clientSsc.cert());

    // Save the truststore to the temporary file
    try (FileOutputStream fos2 = new FileOutputStream(truststoreFile)) {
      trustStore.store(fos2, "password".toCharArray());
    }

    config.setPort(0);
    config.setTlsEnabled(true);
    config.setTlsKeyStorePath(keystoreFile.getAbsolutePath());
    config.setTlsKeyStorePasswordFile(keystorePasswordFile.getAbsolutePath());
    config.setMtlsEnabled(true);
    config.setTlsTrustStorePath(truststoreFile.getAbsolutePath());
    config.setTlsTrustStorePasswordFile(keystorePasswordFile.getAbsolutePath());
    return config;
  }

  @BeforeAll
  public static void setupConstants() {
    final URL blocksUrl = BlockTestUtil.getTestBlockchainUrl();

    final URL genesisJsonUrl = BlockTestUtil.getTestGenesisUrl();

    Assertions.assertThat(blocksUrl).isNotNull();
    Assertions.assertThat(genesisJsonUrl).isNotNull();
  }

  /** Tears down the HTTP server. */
  @AfterAll
  public static void shutdownServer() {
    client.dispatcher().executorService().shutdown();
    client.connectionPool().evictAll();
    service.stop().join();
    vertx.close();
  }

  @Test
  public void invalidCallToStart() {
    service
        .start()
        .whenComplete(
            (unused, exception) -> assertThat(exception).isInstanceOf(IllegalStateException.class));
  }

  @Test
  public void http404() throws Exception {
    try (final Response resp = client.newCall(buildGetRequest("/foo")).execute()) {
      Assertions.assertThat(resp.code()).isEqualTo(404);
    }
  }

  @Test
  public void handleEmptyRequestAndRedirect_post() throws Exception {
    final RequestBody body = RequestBody.create("", null);
    try (final Response resp =
        client.newCall(new Request.Builder().post(body).url(service.url()).build()).execute()) {
      Assertions.assertThat(resp.code()).isEqualTo(HttpResponseStatus.PERMANENT_REDIRECT.code());
      final String location = resp.header("Location");
      Assertions.assertThat(location).isNotEmpty().isNotNull();
      final HttpUrl redirectUrl = resp.request().url().resolve(location);
      Assertions.assertThat(redirectUrl).isNotNull();
      final Request.Builder redirectBuilder = resp.request().newBuilder();
      redirectBuilder.post(resp.request().body());
      resp.body().close();
      try (final Response redirectResp =
          client.newCall(redirectBuilder.url(redirectUrl).build()).execute()) {
        Assertions.assertThat(redirectResp.code()).isEqualTo(HttpResponseStatus.BAD_REQUEST.code());
      }
    }
  }

  @Test
  public void handleEmptyRequestAndRedirect_get() throws Exception {
    String url = service.url();
    Request req = new Request.Builder().get().url(url).build();
    try (final Response resp = client.newCall(req).execute()) {
      Assertions.assertThat(resp.code()).isEqualTo(HttpResponseStatus.PERMANENT_REDIRECT.code());
      final String location = resp.header("Location");
      Assertions.assertThat(location).isNotEmpty().isNotNull();
      final HttpUrl redirectUrl = resp.request().url().resolve(location);
      Assertions.assertThat(redirectUrl).isNotNull();
      final Request.Builder redirectBuilder = resp.request().newBuilder();
      redirectBuilder.get();
      resp.body().close();
      try (final Response redirectResp =
          client.newCall(redirectBuilder.url(redirectUrl).build()).execute()) {
        Assertions.assertThat(redirectResp.code()).isEqualTo(HttpResponseStatus.BAD_REQUEST.code());
      }
    }
  }

  @Test
  public void handleInvalidQuerySchema() throws Exception {
    final RequestBody body = RequestBody.create("{chainID1}", GRAPHQL);

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      final JsonObject json = new JsonObject(resp.body().string());
      testHelper.assertValidGraphQLError(json);
      Assertions.assertThat(resp.code()).isEqualTo(400);
    }
  }

  @Test
  public void query_get() throws Exception {

    try (final Response resp = client.newCall(buildGetRequest("?query={chainID}")).execute()) {
      Assertions.assertThat(resp.code()).isEqualTo(200);
      final JsonObject json = new JsonObject(resp.body().string());
      testHelper.assertValidGraphQLResult(json);
      final String result = json.getJsonObject("data").getString("chainID");
      Assertions.assertThat(result).isEqualTo(EXPECTED_CHAIN_ID);
    }
  }

  @Test
  public void query_jsonPost() throws Exception {
    final RequestBody body = RequestBody.create("{\"query\":\"{chainID}\"}", JSON);

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      Assertions.assertThat(resp.code()).isEqualTo(200); // Check general format of result
      final JsonObject json = new JsonObject(resp.body().string());
      testHelper.assertValidGraphQLResult(json);
      final String result = json.getJsonObject("data").getString("chainID");
      Assertions.assertThat(result).isEqualTo(EXPECTED_CHAIN_ID);
    }
  }

  @Test
  public void query_graphqlPost() throws Exception {
    final RequestBody body = RequestBody.create("{chainID}", GRAPHQL);

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      Assertions.assertThat(resp.code()).isEqualTo(200); // Check general format of result
      final JsonObject json = new JsonObject(resp.body().string());
      testHelper.assertValidGraphQLResult(json);
      final String result = json.getJsonObject("data").getString("chainID");
      Assertions.assertThat(result).isEqualTo(EXPECTED_CHAIN_ID);
    }
  }

  @Test
  public void query_untypedPost() throws Exception {
    final RequestBody body = RequestBody.create("{chainID}", null);

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      Assertions.assertThat(resp.code()).isEqualTo(200); // Check general format of result
      final JsonObject json = new JsonObject(resp.body().string());
      testHelper.assertValidGraphQLResult(json);
      final String result = json.getJsonObject("data").getString("chainID");
      Assertions.assertThat(result).isEqualTo(EXPECTED_CHAIN_ID);
    }
  }

  @Test
  public void getSocketAddressWhenActive() {
    final InetSocketAddress socketAddress = service.socketAddress();
    Assertions.assertThat(socketAddress.getAddress().getHostAddress()).isEqualTo("127.0.0.1");
    Assertions.assertThat(socketAddress.getPort()).isPositive();
  }

  @Test
  public void getSocketAddressWhenStoppedIsEmpty() throws Exception {
    final GraphQLHttpService service = createGraphQLHttpService();

    final InetSocketAddress socketAddress = service.socketAddress();
    Assertions.assertThat(socketAddress.getAddress().getHostAddress()).isEqualTo("0.0.0.0");
    Assertions.assertThat(socketAddress.getPort()).isZero();
    Assertions.assertThat(service.url()).isEmpty();
  }

  @Test
  public void getSocketAddressWhenBindingToAllInterfaces() throws Exception {
    final GraphQLConfiguration config = createGraphQLConfig();
    config.setHost("0.0.0.0");
    final GraphQLHttpService service = createGraphQLHttpService(config);
    service.start().join();

    try {
      final InetSocketAddress socketAddress = service.socketAddress();
      Assertions.assertThat(socketAddress.getAddress().getHostAddress()).isEqualTo("0.0.0.0");
      Assertions.assertThat(socketAddress.getPort()).isPositive();
      Assertions.assertThat(!service.url().contains("0.0.0.0")).isTrue();
    } finally {
      service.stop().join();
    }
  }

  @Test
  public void responseContainsJsonContentTypeHeader() throws Exception {

    final RequestBody body = RequestBody.create("{chainID}", GRAPHQL);

    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      Assertions.assertThat(resp.header("Content-Type")).isEqualTo(JSON.toString());
    }
  }

  @Test
  public void ethGetBlockNumberByBlockHash() throws Exception {
    final long blockNumber = 12345L;
    final Hash blockHash = Hash.hash(Bytes.of(1));
    @SuppressWarnings("unchecked")
    final BlockWithMetadata<TransactionWithMetadata, Hash> block =
        Mockito.mock(BlockWithMetadata.class);
    @SuppressWarnings("unchecked")
    final BlockHeader blockHeader = Mockito.mock(BlockHeader.class);

    Mockito.when(blockchainQueries.blockByHash(blockHash)).thenReturn(Optional.of(block));
    Mockito.when(block.getHeader()).thenReturn(blockHeader);
    Mockito.when(blockHeader.getNumber()).thenReturn(blockNumber);

    final String query = "{block(hash:\"" + blockHash + "\") {number}}";

    final RequestBody body = RequestBody.create(query, GRAPHQL);
    try (final Response resp = client.newCall(buildPostRequest(body)).execute()) {
      Assertions.assertThat(resp.code()).isEqualTo(200);
      final String jsonStr = resp.body().string();
      final JsonObject json = new JsonObject(jsonStr);
      testHelper.assertValidGraphQLResult(json);
      final String result = json.getJsonObject("data").getJsonObject("block").getString("number");
      Assertions.assertThat(Integer.parseInt(result.substring(2), 16)).isEqualTo(blockNumber);
    }
  }

  private Request buildPostRequest(final RequestBody body) {
    return new Request.Builder().post(body).url(baseUrl).build();
  }

  private Request buildGetRequest(final String path) {
    return new Request.Builder().get().url(baseUrl + path).build();
  }
}
