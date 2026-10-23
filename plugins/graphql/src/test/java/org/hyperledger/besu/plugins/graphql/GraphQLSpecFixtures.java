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
package org.hyperledger.besu.plugins.graphql;

import static com.google.common.base.Preconditions.checkState;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import com.google.common.io.Resources;
import io.vertx.core.json.JsonObject;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * The GraphQL spec fixtures: each JSON file holds a request, optionally its variables, and the
 * response body and status code Besu returns for it on the hive test chain.
 */
public final class GraphQLSpecFixtures {

  /**
   * The directory of the fixtures that came with GraphQL from Besu's core. They must stay
   * byte-identical to the fixtures of the core GraphQL service.
   */
  public static final String SPEC_DIRECTORY = "/org/hyperledger/besu/plugins/graphql/";

  /**
   * The directory of the fixtures added with the plugin, for fields no spec fixture covers. Their
   * responses were recorded from Besu's core GraphQL service on the same chain.
   */
  public static final String EXTRA_DIRECTORY = "/org/hyperledger/besu/plugins/graphqlextra/";

  private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
  private static final MediaType GRAPHQL = MediaType.parse("application/graphql; charset=utf-8");

  private GraphQLSpecFixtures() {}

  /**
   * Lists the fixtures of a directory.
   *
   * @param directory the resource directory, one of {@link #SPEC_DIRECTORY} and {@link
   *     #EXTRA_DIRECTORY}
   * @return the resource paths of the fixtures, sorted by name
   */
  public static List<String> list(final String directory) {
    // The package directory exists in several classpath roots, so all of them are searched.
    final Set<String> names = new TreeSet<>();
    try {
      final Enumeration<URL> urls =
          GraphQLSpecFixtures.class.getClassLoader().getResources(directory.substring(1));
      while (urls.hasMoreElements()) {
        try (Stream<Path> files = Files.list(Paths.get(urls.nextElement().toURI()))) {
          files
              .map(Path::getFileName)
              .map(Path::toString)
              .filter(p -> p.endsWith(".json"))
              .filter(p -> !p.contains("genesis"))
              .forEach(names::add);
        }
      }
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    } catch (final URISyntaxException e) {
      throw new IllegalStateException(e);
    }
    checkState(!names.isEmpty(), "Cannot find the fixtures of %s", directory);
    return names.stream().map(name -> directory + name).toList();
  }

  /**
   * Sends the request of a fixture and asserts that the response body and status code are the
   * expected ones.
   *
   * @param client the HTTP client
   * @param url the URL of the GraphQL endpoint
   * @param fixture the resource path of the fixture
   * @throws IOException if the request fails
   */
  public static void assertFixture(
      final OkHttpClient client, final String url, final String fixture) throws IOException {
    final URL resource = GraphQLSpecFixtures.class.getResource(fixture);
    checkState(resource != null, "Cannot find fixture %s", fixture);
    final JsonObject spec = new JsonObject(Resources.toString(resource, StandardCharsets.UTF_8));
    final String rawRequestBody = spec.getString("request");
    final String rawVariables = spec.getString("variables");
    final RequestBody requestBody =
        rawVariables == null
            ? RequestBody.create(rawRequestBody, GRAPHQL)
            : RequestBody.create(
                "{ \"query\":\"" + rawRequestBody + "\", \"variables\": " + rawVariables + "}",
                JSON);
    final Request request = new Request.Builder().post(requestBody).url(url).build();

    try (final Response resp = client.newCall(request).execute()) {
      final JsonObject expectedRespBody = spec.getJsonObject("response");
      final JsonObject result = new JsonObject(resp.body().string());
      assertThat(result).as(fixture).isEqualTo(expectedRespBody);
      assertThat(resp.code()).as(fixture).isEqualTo(spec.getInteger("statusCode"));
    }
  }
}
