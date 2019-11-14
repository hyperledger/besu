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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import org.bouncycastle.util.io.pem.PemReader;

public class JWTAuthOptionsFactory {

  private static final String ALGORITHM = "RS256";
  private static final String PERMISSIONS = "permissions";

  public JWTAuthOptions createForExternalPublicKey(final File externalPublicKeyFile) {
    final byte[] externalJwtPublicKey = readPublicKey(externalPublicKeyFile);
    final String base64EncodedPublicKey = Base64.getEncoder().encodeToString(externalJwtPublicKey);
    return new JWTAuthOptions()
        .setPermissionsClaimKey(PERMISSIONS)
        .addPubSecKey(
            new PubSecKeyOptions().setAlgorithm(ALGORITHM).setPublicKey(base64EncodedPublicKey));
  }

  public JWTAuthOptions createWithGeneratedKeyPair() {
    final KeyPair keypair = generateJwtKeyPair();
    return new JWTAuthOptions()
        .setPermissionsClaimKey(PERMISSIONS)
        .addPubSecKey(
            new PubSecKeyOptions()
                .setAlgorithm(ALGORITHM)
                .setPublicKey(Base64.getEncoder().encodeToString(keypair.getPublic().getEncoded()))
                .setSecretKey(
                    Base64.getEncoder().encodeToString(keypair.getPrivate().getEncoded())));
  }

  private byte[] readPublicKey(final File authenticationPublicKeyFile) {
    try {
      final PemReader pemReader =
          new PemReader(new InputStreamReader(new FileInputStream(authenticationPublicKeyFile)));
      return pemReader.readPemObject().getContent();
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
}
