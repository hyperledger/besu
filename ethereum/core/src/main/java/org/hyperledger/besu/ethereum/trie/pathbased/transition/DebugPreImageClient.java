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
package org.hyperledger.besu.ethereum.trie.pathbased.transition;

import org.hyperledger.besu.datatypes.Hash;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.tuweni.bytes.Bytes;

public class DebugPreImageClient {

  private static final String NODE_URL = "http://10.0.0.19:8545";

  public static Bytes getPreImage(final Hash hash) throws Exception {
    // Construction de la requête JSON avec Vert.x
    JsonObject request =
        new JsonObject()
            .put("jsonrpc", "2.0")
            .put("method", "debug_getPreImage")
            .put("params", new JsonArray().add(hash.toHexString()))
            .put("id", 1);

    // Connexion HTTP
    URL url = new URL(NODE_URL);
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestMethod("POST");
    conn.setRequestProperty("Content-Type", "application/json");
    conn.setDoOutput(true);

    // Envoi de la requête
    try (OutputStream os = conn.getOutputStream()) {
      byte[] input = request.encode().getBytes(StandardCharsets.UTF_8);
      os.write(input, 0, input.length);
    }

    // Lecture de la réponse
    StringBuilder response = new StringBuilder();
    try (BufferedReader br =
        new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"))) {
      String line;
      while ((line = br.readLine()) != null) {
        response.append(line.trim());
      }
    }

    // Parsing de la réponse avec Vert.x
    JsonObject jsonResponse = new JsonObject(response.toString());
    return Bytes.fromHexString(jsonResponse.getString("result"));
  }
}
