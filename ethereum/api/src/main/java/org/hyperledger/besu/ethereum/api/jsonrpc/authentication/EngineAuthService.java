/*
 * Copyright Hyperledger Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.hyperledger.besu.ethereum.api.jsonrpc.authentication;

import java.util.Optional;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.impl.Codec;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.web.RoutingContext;
import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EngineAuthService implements AuthenticationService {

  private static final Logger LOG = LoggerFactory.getLogger(EngineAuthService.class);
  private final JWTAuth jwtAuthProvider;

  public EngineAuthService(final Vertx vertx) {
    final JWTAuthOptions jwtAuthOptions = engineApiJWTOptions(JwtAlgorithm.HS256);
    this.jwtAuthProvider = JWTAuth.create(vertx, jwtAuthOptions);
    LOG.info(
        "ENGINE API JWT EPHEMERAL KEY: {}",
        Codec.base16Encode(jwtAuthOptions.getPubSecKeys().get(0).getBuffer().getBytes()));
  }

  public JWTAuthOptions engineApiJWTOptions(final JwtAlgorithm jwtAlgorithm) {
    byte[] ephemeralKey = Bytes32.random().toArray();
    return new JWTAuthOptions()
        .setJWTOptions(new JWTOptions().setIgnoreExpiration(true).setLeeway(5))
        .addPubSecKey(
            new PubSecKeyOptions()
                .setAlgorithm(jwtAlgorithm.toString())
                .setBuffer(Buffer.buffer(ephemeralKey)));
  }

  @Override
  public void handleLogin(final RoutingContext routingContext) {
    LOG.warn("Engine Auth does not support logins, no login handled");
  }

  @Override
  public JWTAuth getJwtAuthProvider() {
    return this.jwtAuthProvider;
  }

  @Override
  public void getUser(final String token, final Handler<Optional<User>> handler) {
    try {
      getJwtAuthProvider()
          .authenticate(
              new JsonObject().put("token", token),
              r -> {
                if (r.succeeded()) {
                  final Optional<User> user = Optional.ofNullable(r.result());
                  handler.handle(user);
                } else {
                  LOG.debug("Invalid JWT token {}", r.cause().toString());
                  handler.handle(Optional.empty());
                }
              });

    } catch (Exception e) {
      LOG.debug("exception validating JWT ", e);
      handler.handle(Optional.empty());
    }
  }
}
