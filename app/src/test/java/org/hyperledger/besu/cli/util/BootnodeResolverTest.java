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
package org.hyperledger.besu.cli.util;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class BootnodeResolverTest {

  private static final String VALID_NODE_ID =
      "0x000000000000000000000000000000000000000000000000000000000000000a";

  private HttpServer server;

  @AfterEach
  void stopServer() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void shouldAcceptBootnodesFromLocalFile(@TempDir final Path tempDir) throws Exception {
    final Path file = tempDir.resolve("enodes.txt");
    Files.write(
        file,
        List.of(
            "enode://" + VALID_NODE_ID + "@192.168.0.1:4567",
            "",
            "enode://" + VALID_NODE_ID + "@192.168.0.2:4567"),
        UTF_8);

    final List<String> result = BootnodeResolver.resolve(List.of(file.toString()));

    assertThat(result)
        .containsExactly(
            "enode://" + VALID_NODE_ID + "@192.168.0.1:4567",
            "enode://" + VALID_NODE_ID + "@192.168.0.2:4567");
  }

  @Test
  void shouldAcceptBootnodesFromFileUri(@TempDir final Path tempDir) throws Exception {
    final Path file = tempDir.resolve("enodes.txt");
    Files.writeString(file, "enode://" + VALID_NODE_ID + "@10.0.0.1:30303\n", UTF_8);

    final List<String> result = BootnodeResolver.resolve(List.of(file.toUri().toString()));

    assertThat(result).containsExactly("enode://" + VALID_NODE_ID + "@10.0.0.1:30303");
  }

  @Test
  void shouldAcceptBootnodesFromHttpUrl() throws Exception {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/enodes.txt",
        new HttpHandler() {
          @Override
          public void handle(HttpExchange exchange) throws IOException {
            final byte[] body =
                ("enode://"
                        + VALID_NODE_ID
                        + "@11.11.11.11:30303\n"
                        + "enode://"
                        + VALID_NODE_ID
                        + "@22.22.22.22:30303\n")
                    .getBytes(UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
          }
        });
    server.start();

    final String url = "http://localhost:" + server.getAddress().getPort() + "/enodes.txt";

    final List<String> result = BootnodeResolver.resolve(List.of(url));

    assertThat(result)
        .containsExactly(
            "enode://" + VALID_NODE_ID + "@11.11.11.11:30303",
            "enode://" + VALID_NODE_ID + "@22.22.22.22:30303");
  }

  @Test
  void shouldAcceptBootnodesAsRawStrings() {
    final String raw = "enode://" + VALID_NODE_ID + "@33.33.33.33:30303";

    final List<String> result = BootnodeResolver.resolve(List.of(raw));

    assertThat(result).containsExactly(raw);
  }

  @Test
  void fileUri_openStream_readsLines(@TempDir final Path tempDir) throws Exception {

    final Path file = tempDir.resolve("enodes.txt");
    Files.writeString(
        file,
        "enode://"
            + VALID_NODE_ID
            + "@10.0.0.1:30303\n"
            + "# comment\n"
            + "\n"
            + "enode://"
            + VALID_NODE_ID
            + "@10.0.0.2:30303\n",
        UTF_8);

    final URI uri = file.toUri();
    final List<String> lines;
    try (var in = uri.toURL().openStream();
        var br = new BufferedReader(new InputStreamReader(in, UTF_8))) {
      lines =
          br.lines()
              .map(String::trim)
              .filter(l -> !l.isEmpty())
              .filter(l -> !l.startsWith("#"))
              .toList();
    }

    assertThat(lines)
        .containsExactly(
            "enode://" + VALID_NODE_ID + "@10.0.0.1:30303",
            "enode://" + VALID_NODE_ID + "@10.0.0.2:30303");
  }

  @Test
  void shouldThrowWhenBootnodesUrlIsUnreachable() {
    assertThatThrownBy(
            () -> BootnodeResolver.resolve(List.of("http://localhost:65535/missing.txt")))
        .isInstanceOf(BootnodeResolver.BootnodeResolutionException.class);
  }
}
