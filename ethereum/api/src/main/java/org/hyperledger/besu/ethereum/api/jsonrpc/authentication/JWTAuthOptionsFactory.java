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
package org.hyperledger.besu.ethereum.api.jsonrpc.authentication;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.impl.Codec;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;

public class JWTAuthOptionsFactory {

  private static final String DEFAULT_ALGORITHM = "RS256";
  private static final String PERMISSIONS = "permissions";

  public JWTAuthOptions createForExternalPublicKey(final File externalPublicKeyFile) {
    final byte[] externalJwtPublicKey = readPublicKey(externalPublicKeyFile);
    final String base64EncodedPublicKey = Base64.getEncoder().encodeToString(externalJwtPublicKey);
    return new JWTAuthOptions()
        .setPermissionsClaimKey(PERMISSIONS)
        .addPubSecKey(
            new PubSecKeyOptions()
                .setAlgorithm(DEFAULT_ALGORITHM)
                .setPublicKey(base64EncodedPublicKey));
  }

  public JWTAuthOptions createForExternalPublicKeyWithAlgorithm(
      final File externalPublicKeyFile, final String algorithm) {
    final byte[] externalJwtPublicKey = readPublicKey(externalPublicKeyFile);
    final String base64EncodedPublicKey = Base64.getEncoder().encodeToString(externalJwtPublicKey);

    return new JWTAuthOptions()
        .setPermissionsClaimKey(PERMISSIONS)
        .addPubSecKey(
            new PubSecKeyOptions().setAlgorithm(algorithm).setPublicKey(base64EncodedPublicKey));
  }

  public JWTAuthOptions createWithGeneratedKeyPair() {
    final KeyPair keypair = generateJwtKeyPair();
    return new JWTAuthOptions()
        //.setPermissionsClaimKey(PERMISSIONS)
        .addPubSecKey(
            new PubSecKeyOptions()
                .setAlgorithm(DEFAULT_ALGORITHM)
                .setBuffer(keyPairToPublicPemString(keypair)))
        .addPubSecKey(
            new PubSecKeyOptions()
                .setAlgorithm(DEFAULT_ALGORITHM)
                .setBuffer(keyPairToPrivatePemString(keypair)));
  }

  private byte[] readPublicKey(final File publicKeyFile) {
    try (final BufferedReader reader = Files.newBufferedReader(publicKeyFile.toPath(), UTF_8);
        final PemReader pemReader = new PemReader(reader)) {
      final PemObject pemObject = pemReader.readPemObject();
      if (pemObject == null) {
        throw new IllegalStateException("Authentication RPC public key file format is invalid");
      }
      return pemObject.getContent();
    } catch (IOException e) {
      throw new IllegalStateException("Authentication RPC public key could not be read", e);
    }
  }

  private KeyPair generateJwtKeyPair() {
    final KeyPairGenerator keyGenerator;
    try {
      keyGenerator = KeyPairGenerator.getInstance("RSA");
      keyGenerator.initialize(2048);
    } catch (final NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }

    return keyGenerator.generateKeyPair();
  }

  private String keyPairToPublicPemString(final KeyPair kp) {
    StringBuilder pemBuffer = new StringBuilder();
    pemBuffer.append("-----BEGIN PUBLIC KEY-----\r\n");
    pemBuffer.append(Codec.base64MimeEncode(kp.getPublic().getEncoded()));
    pemBuffer.append("\r\n");
    pemBuffer.append("-----END PUBLIC KEY-----\r\n");
    return pemBuffer.toString();
  }

  private String keyPairToPrivatePemString(final KeyPair kp) {
    StringBuilder pemBuffer = new StringBuilder();
    pemBuffer.append("-----BEGIN PRIVATE KEY-----\r\n");
    pemBuffer.append(Codec.base64MimeEncode(kp.getPrivate().getEncoded()));
    pemBuffer.append("\r\n");
    pemBuffer.append("-----END PRIVATE KEY-----\r\n");
    return pemBuffer.toString();
  }
}
