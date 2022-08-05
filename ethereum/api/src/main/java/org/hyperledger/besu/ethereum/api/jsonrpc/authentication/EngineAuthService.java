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

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.JsonRpcMethod;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
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
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EngineAuthService implements AuthenticationService {

  private static final Logger LOG = LoggerFactory.getLogger(EngineAuthService.class);
  private static final int JWT_EXPIRATION_TIME = 60;

  private final JWTAuth jwtAuthProvider;

  public EngineAuthService(final Vertx vertx, final Optional<File> signingKey, final Path datadir) {
    final JWTAuthOptions jwtAuthOptions =
        engineApiJWTOptions(JwtAlgorithm.HS256, signingKey, datadir);
    this.jwtAuthProvider = JWTAuth.create(vertx, jwtAuthOptions);
  }

  public String createToken() {
    JsonObject claims = new JsonObject();
    claims.put("iat", System.currentTimeMillis() / 1000);
    return this.jwtAuthProvider.generateToken(claims);
  }

  private JWTAuthOptions engineApiJWTOptions(
      final JwtAlgorithm jwtAlgorithm, final Optional<File> keyFile, final Path datadir) {
    byte[] signingKey = null;
    if (!keyFile.isPresent()) {
      final File jwtFile = new File(datadir.toFile(), "jwt.hex");
      jwtFile.deleteOnExit();
      final byte[] ephemeralKey = Bytes32.random().toArray();
      try {
        Files.writeString(jwtFile.toPath(), Codec.base16Encode(ephemeralKey));
      } catch (IOException ioe) {
        LOG.warn("Unable to write ephemeral jwt key file to {}", jwtFile.toPath().toString());
        LOG.info("JWT KEY: {}", Codec.base16Encode(ephemeralKey));
      }
      signingKey = ephemeralKey;
    } else { // user configured option to use a specified file
      if (keyFile.get().exists()) {
        try {
          final String keyHex = Files.readAllLines(keyFile.get().toPath()).get(0);
          if (keyHex.length() >= 64) {
            signingKey = Bytes.fromHexString(keyHex).toArray();
          } else {
            UnsecurableEngineApiException e =
                new UnsecurableEngineApiException("signing key too short, 256 bits required");
            e.fillInStackTrace();
            throw e;
          }
        } catch (IOException ioe) {
          UnsecurableEngineApiException e =
              new UnsecurableEngineApiException(
                  "Could not read key from " + keyFile.get().toString());
          e.fillInStackTrace();
          e.initCause(ioe);
          throw e;
        }
      } else {
        UnsecurableEngineApiException e =
            new UnsecurableEngineApiException(
                "Could not read key from " + keyFile.get().toString());
        e.fillInStackTrace();
        throw e;
      }
    }
    if (signingKey == null || signingKey.length < 32) {
      UnsecurableEngineApiException e =
          new UnsecurableEngineApiException(
              "Could not read at least 256 bits of key from "
                  + (keyFile.isPresent() ? keyFile.get().toString() : "undefined"));
      e.fillInStackTrace();
      throw e;
    }

    return new JWTAuthOptions()
        .setJWTOptions(new JWTOptions().setIgnoreExpiration(true).setLeeway(5))
        .addPubSecKey(
            new PubSecKeyOptions()
                .setAlgorithm(jwtAlgorithm.toString())
                .setBuffer(Buffer.buffer(signingKey)));
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
  public void authenticate(final String token, final Handler<Optional<User>> handler) {
    try {
      JsonObject jwt = new JsonObject().put("token", token);
      getJwtAuthProvider()
          .authenticate(
              jwt,
              r -> {
                if (r.succeeded()) {
                  if (issuedRecently(r.result().attributes().getLong("iat"))) {
                    final Optional<User> user = Optional.ofNullable(r.result());
                    handler.handle(user);
                  } else {
                    LOG.warn("Client sent stale token: {}", r.result().attributes());
                    handler.handle(Optional.empty());
                  }

                } else {
                  LOG.debug("Authentication failed: {}", r.cause().toString());
                  handler.handle(Optional.empty());
                }
              });

    } catch (Exception e) {
      LOG.debug("exception validating JWT ", e);
      handler.handle(Optional.empty());
    }
  }

  @Override
  public boolean isPermitted(
      final Optional<User> optionalUser,
      final JsonRpcMethod jsonRpcMethod,
      final Collection<String> noAuthMethods) {
    return noAuthMethods.contains(jsonRpcMethod.getName()) || optionalUser.isPresent();
  }

  private boolean issuedRecently(final long iat) {
    long iatSecondsSinceEpoch = iat;
    long nowSecondsSinceEpoch = System.currentTimeMillis() / 1000;
    return (Math.abs((nowSecondsSinceEpoch - iatSecondsSinceEpoch)) <= JWT_EXPIRATION_TIME);
  }
}
