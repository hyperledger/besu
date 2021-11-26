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

package org.hyperledger.besu.enclave;

import static org.hyperledger.besu.enclave.TlsHelpers.populateFingerprintFile;

import java.io.IOException;
import java.nio.file.Path;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import com.google.common.collect.Lists;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.Vertx;
import io.vertx.core.http.ClientAuth;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.net.PfxOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.apache.tuweni.net.tls.VertxTrustOptions;

class TlsEnabledHttpServerFactory {

  private final Vertx vertx;
  private final List<HttpServer> serversCreated = Lists.newArrayList();

  TlsEnabledHttpServerFactory() {
    this.vertx = Vertx.vertx();
  }

  void shutdown() {
    serversCreated.forEach(HttpServer::close);
    vertx.close();
  }

  HttpServer create(
      final TlsCertificateDefinition serverCert,
      final TlsCertificateDefinition acceptedClientCerts,
      final Path workDir,
      final boolean tlsEnabled) {
    try {

      final Path serverFingerprintFile = workDir.resolve("server_known_clients");
      populateFingerprintFile(serverFingerprintFile, acceptedClientCerts, Optional.empty());

      final HttpServerOptions web3HttpServerOptions = new HttpServerOptions();
      web3HttpServerOptions.setPort(0);
      if (tlsEnabled) {
        web3HttpServerOptions.setSsl(true);
        web3HttpServerOptions.setClientAuth(ClientAuth.REQUIRED);
        web3HttpServerOptions.setTrustOptions(
            VertxTrustOptions.allowlistClients(serverFingerprintFile));
        web3HttpServerOptions.setPfxKeyCertOptions(
            new PfxOptions()
                .setPath(serverCert.getPkcs12File().toString())
                .setPassword(serverCert.getPassword()));
      }
      final Router router = Router.router(vertx);
      router
          .route(HttpMethod.GET, "/upcheck")
          .produces(HttpHeaderValues.APPLICATION_JSON.toString())
          .handler(TlsEnabledHttpServerFactory::handleRequest);

      final HttpServer mockOrionHttpServer = vertx.createHttpServer(web3HttpServerOptions);

      final CompletableFuture<Boolean> serverConfigured = new CompletableFuture<>();
      mockOrionHttpServer.requestHandler(router).listen(result -> serverConfigured.complete(true));

      serverConfigured.get();

      serversCreated.add(mockOrionHttpServer);
      return mockOrionHttpServer;
    } catch (final KeyStoreException
        | NoSuchAlgorithmException
        | CertificateException
        | IOException
        | ExecutionException
        | InterruptedException e) {
      throw new RuntimeException("Failed to construct a TLS Enabled Server", e);
    }
  }

  private static void handleRequest(final RoutingContext context) {
    final HttpServerResponse response = context.response();
    if (!response.closed()) {
      response.end("I'm up!");
    }
  }
}
