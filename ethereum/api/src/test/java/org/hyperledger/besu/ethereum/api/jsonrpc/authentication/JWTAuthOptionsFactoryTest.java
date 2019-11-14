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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import org.junit.Test;

public class JWTAuthOptionsFactoryTest {

  private static final String JWT_PUBLIC_KEY =
      "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAw6tMhjogMulRbYby7bCL"
          + "rFhukDnxvm4XR3KSXLKdLLQHHyouMOQaLac9M+/Z1KkIpqfZPjLfW2/yUg2IKx4T"
          + "dvFVzbVq17X6dq49ZS8jJtb8l2+Vius4d3LnpvxCOematRG9Acn+2qLwC+sK7RPY"
          + "OxEqKPU5LNBH1C0FfviazY5jkixBFICzIq/SyyRnGX+iIONnNsu0TlhWVLSlZbg5"
          + "NYf4cAzu/1d5MgspyZwnRo468gqaak3wQzkmk69Z25L1N7TXZvk2b7rT7/ssFnt+"
          + "//fKVpD6qkQ3OopD+7gOziAYUxChw6RUWekV+uRgNADQhaqV6wDdogBz77wTJedV"
          + "YwIDAQAB";

  @Test
  public void createsOptionsWithGeneratedKeyPair() {
    final JWTAuthOptionsFactory jwtAuthOptionsFactory = new JWTAuthOptionsFactory();
    final JWTAuthOptions jwtAuthOptions = jwtAuthOptionsFactory.createWithGeneratedKeyPair();

    assertThat(jwtAuthOptions.getPubSecKeys()).isNotNull();
    assertThat(jwtAuthOptions.getPubSecKeys()).hasSize(1);
    assertThat(jwtAuthOptions.getPubSecKeys().get(0).getAlgorithm()).isEqualTo("RS256");
    assertThat(jwtAuthOptions.getPubSecKeys().get(0).getPublicKey()).isNotEmpty();
    assertThat(jwtAuthOptions.getPubSecKeys().get(0).getSecretKey()).isNotEmpty();
  }

  @Test
  public void createsOptionsWithGeneratedKeyPairThatIsDifferentEachTime() {
    final JWTAuthOptionsFactory jwtAuthOptionsFactory = new JWTAuthOptionsFactory();
    final JWTAuthOptions jwtAuthOptions1 = jwtAuthOptionsFactory.createWithGeneratedKeyPair();
    final JWTAuthOptions jwtAuthOptions2 = jwtAuthOptionsFactory.createWithGeneratedKeyPair();

    final PubSecKeyOptions pubSecKeyOptions1 = jwtAuthOptions1.getPubSecKeys().get(0);
    final PubSecKeyOptions pubSecKeyOptions2 = jwtAuthOptions2.getPubSecKeys().get(0);
    assertThat(pubSecKeyOptions1.getPublicKey()).isNotEqualTo(pubSecKeyOptions2.getPublicKey());
    assertThat(pubSecKeyOptions1.getSecretKey()).isNotEqualTo(pubSecKeyOptions2.getSecretKey());
  }

  @Test
  public void createsOptionsUsingPublicKeyFile() throws URISyntaxException {
    final JWTAuthOptionsFactory jwtAuthOptionsFactory = new JWTAuthOptionsFactory();
    final File enclavePublicKeyFile =
        Paths.get(ClassLoader.getSystemResource("authentication/jwt_public_key").toURI())
            .toAbsolutePath()
            .toFile();

    final JWTAuthOptions jwtAuthOptions =
        jwtAuthOptionsFactory.createForExternalPublicKey(enclavePublicKeyFile);
    assertThat(jwtAuthOptions.getPubSecKeys()).hasSize(1);
    assertThat(jwtAuthOptions.getPubSecKeys().get(0).getAlgorithm()).isEqualTo("RS256");
    assertThat(jwtAuthOptions.getPubSecKeys().get(0).getSecretKey()).isNull();
    assertThat(jwtAuthOptions.getPubSecKeys().get(0).getPublicKey()).isEqualTo(JWT_PUBLIC_KEY);
  }

  @Test
  public void failsToCreateOptionsWhenPublicKeyFileDoesNotExist() {
    final JWTAuthOptionsFactory jwtAuthOptionsFactory = new JWTAuthOptionsFactory();
    final File enclavePublicKeyFile = new File("doesNotExist");

    assertThatThrownBy(() -> jwtAuthOptionsFactory.createForExternalPublicKey(enclavePublicKeyFile))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Authentication RPC public key could not be read");
  }

  @Test
  public void failsToCreateOptionsWhenPublicKeyFileIsInvalid() throws IOException {
    final JWTAuthOptionsFactory jwtAuthOptionsFactory = new JWTAuthOptionsFactory();
    final Path enclavePublicKey = Files.createTempFile("enclave", "pub");
    Files.writeString(enclavePublicKey, "invalidDataNo---HeadersAndNotBase64");

    assertThatThrownBy(
            () -> jwtAuthOptionsFactory.createForExternalPublicKey(enclavePublicKey.toFile()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Authentication RPC public key file format is invalid");
  }
}
