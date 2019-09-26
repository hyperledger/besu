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

package org.hyperledger.besu.cli.subcommands.networkcreate.generate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.tuweni.crypto.sodium.Box;
import org.apache.tuweni.crypto.sodium.Box.KeyPair;
import org.apache.tuweni.crypto.sodium.PasswordHash;
import org.apache.tuweni.crypto.sodium.SecretBox;
import org.apache.tuweni.io.Base64;

public class OrionKeys {
  private static final PasswordHash.Algorithm ENCRYPT_ALGORITHM =
      PasswordHash.Algorithm.argon2i13();

  private final KeyPair keyPair;

  public OrionKeys() {
    this.keyPair = Box.KeyPair.random();
  }

  public Box.PublicKey getPublicKey() {
    return keyPair.publicKey();
  }

  public String encryptToJson(final String password) throws JsonProcessingException {
    final ObjectMapper mapper = new ObjectMapper();
    final ObjectNode encryptedKeyJson = mapper.createObjectNode();
    ObjectNode encryptedKeyNode = mapper.createObjectNode();

    final String encryptedKey = generateEncryptedKey(keyPair, password);

    encryptedKeyNode.set("bytes", mapper.convertValue(encryptedKey, JsonNode.class));
    encryptedKeyJson.set("data", encryptedKeyNode);
    encryptedKeyJson.set("type", mapper.convertValue("sodium-encrypted", JsonNode.class));
    return mapper.writeValueAsString(encryptedKeyJson);
  }

  private String generateEncryptedKey(final KeyPair keyPair, final String password) {
    byte[] encryptedKey =
        SecretBox.encrypt(keyPair.secretKey().bytesArray(), password, ENCRYPT_ALGORITHM);
    return Base64.encodeBytes(encryptedKey);
  }
}
