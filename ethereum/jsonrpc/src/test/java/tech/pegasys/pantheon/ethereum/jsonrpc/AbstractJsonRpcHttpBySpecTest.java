/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.jsonrpc;

import static com.google.common.base.Preconditions.checkState;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public abstract class AbstractJsonRpcHttpBySpecTest extends AbstractJsonRpcHttpServiceTest {

  private static ObjectMapper objectMapper = new ObjectMapper();
  private final URL specURL;

  public AbstractJsonRpcHttpBySpecTest(final String specName, final URL specURL) {
    this.specURL = specURL;
  }

  @Test
  public void jsonRPCCallWithSpecFile() throws Exception {
    jsonRPCCall(specURL);
  }

  /**
   * Searches the provided subdirectories for spec files containing test cases, and returns
   * parameters required to run these tests.
   *
   * @param subDirectoryPaths The subdirectories to search.
   * @return Parameters for this test which will contain the name of the test and the url of the
   *     spec file to run.
   */
  public static Object[][] findSpecFiles(final String... subDirectoryPaths) {
    final List<Object[]> specFiles = new ArrayList<>();
    for (final String path : subDirectoryPaths) {
      final URL url = AbstractJsonRpcHttpBySpecTest.class.getResource(path);
      checkState(url != null, "Cannot find test directory " + path);
      final Path dir;
      try {
        dir = Paths.get(url.toURI());
      } catch (final URISyntaxException e) {
        throw new RuntimeException("Problem converting URL to URI " + url, e);
      }
      try (final Stream<Path> s = Files.walk(dir, 1)) {
        s.map(Path::toFile)
            .filter(f -> f.getPath().endsWith(".json"))
            .map(AbstractJsonRpcHttpBySpecTest::fileToParams)
            .forEach(specFiles::add);
      } catch (final IOException e) {
        throw new RuntimeException("Problem reading directory " + dir, e);
      }
    }
    final Object[][] result = new Object[specFiles.size()][2];
    for (int i = 0; i < specFiles.size(); i++) {
      result[i] = specFiles.get(i);
    }
    return result;
  }

  private static Object[] fileToParams(final File file) {
    try {
      final String fileName = file.toPath().getFileName().toString();
      final URL fileURL = file.toURI().toURL();
      return new Object[] {fileName, fileURL};
    } catch (MalformedURLException e) {
      throw new RuntimeException("Problem reading spec file " + file.getAbsolutePath(), e);
    }
  }

  private void jsonRPCCall(final URL specFile) throws IOException {
    final String json = Resources.toString(specFile, Charsets.UTF_8);
    final ObjectNode specNode = (ObjectNode) objectMapper.readTree(json);
    final String rawRequestBody = specNode.get("request").toString();
    final RequestBody requestBody = RequestBody.create(JSON, rawRequestBody);
    final Request request = new Request.Builder().post(requestBody).url(baseUrl).build();

    try (final Response resp = client.newCall(request).execute()) {
      final int expectedStatusCode = specNode.get("statusCode").asInt();
      assertThat(resp.code()).isEqualTo(expectedStatusCode);

      final ObjectNode responseBody;
      try {
        responseBody = (ObjectNode) objectMapper.readTree(resp.body().string());
      } catch (Exception e) {
        throw new RuntimeException("Unable to parse response as json object", e);
      }

      final ObjectNode expectedResponse = (ObjectNode) specNode.get("response");

      // Check id
      final String actualId = responseBody.get("id").toString();
      final String expectedId = expectedResponse.get("id").toString();
      assertThat(actualId).isEqualTo(expectedId);

      // Check version
      final String actualVersion = responseBody.get("jsonrpc").toString();
      final String expectedVersion = expectedResponse.get("jsonrpc").toString();
      assertThat(actualVersion).isEqualTo(expectedVersion);

      // Check result
      if (expectedResponse.has("result")) {
        assertThat(responseBody.has("result")).isTrue();
        final String expectedResult = expectedResponse.get("result").toString();
        final String actualResult = responseBody.get("result").toString();
        final ObjectMapper mapper = new ObjectMapper();
        assertThat(mapper.readTree(actualResult)).isEqualTo(mapper.readTree(expectedResult));
      }

      // Check error
      if (expectedResponse.has("error")) {
        assertThat(responseBody.has("error")).isTrue();
        final String expectedError = expectedResponse.get("error").toString();
        final String actualError = responseBody.get("error").toString();
        assertThat(actualError).isEqualToIgnoringWhitespace(expectedError);
      }
    }
  }
}
