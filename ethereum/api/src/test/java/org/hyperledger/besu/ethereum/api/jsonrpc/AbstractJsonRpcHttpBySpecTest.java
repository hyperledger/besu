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
package org.hyperledger.besu.ethereum.api.jsonrpc;

import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;
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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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

  private static final ObjectMapper objectMapper = new ObjectMapper();
  private static final Pattern GAS_MATCH_FOR_STATE_DIFF =
      Pattern.compile("\"balance\":(?!\"=\").*?},");
  private static final Pattern GAS_MATCH_FOR_VM_TRACE =
      Pattern.compile(",*\"cost\":[0-9a-fA-F]+,*|,\"used\":[0-9a-fA-F]+");
  private static final Pattern GAS_MATCH_FOR_TRACE =
      Pattern.compile("\"gasUsed\":\"[x0-9a-fA-F]+\",");

  private final URL specURL;

  protected AbstractJsonRpcHttpBySpecTest(final String specName, final URL specURL) {
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
  public static Object[][] findSpecFiles(
      final String[] subDirectoryPaths, final String... exceptions) {
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
            .filter(f -> !f.getPath().contains("genesis"))
            .filter(f -> Arrays.stream(exceptions).noneMatch(f.getPath()::contains))
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
    } catch (final MalformedURLException e) {
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

      final JsonNode expectedResponse = specNode.get("response");
      if (expectedResponse.isObject()) {
        try {
          final ObjectNode responseBody =
              (ObjectNode) objectMapper.readTree(Objects.requireNonNull(resp.body()).string());
          checkResponse(
              responseBody,
              (ObjectNode) expectedResponse,
              getMethod(rawRequestBody),
              getTraceType(specFile.toString()));
        } catch (final Exception e) {
          throw new RuntimeException("Unable to parse response as json object", e);
        }
      } else if (expectedResponse.isArray()) {
        final ArrayNode responseBody;
        try {
          responseBody =
              (ArrayNode) objectMapper.readTree(Objects.requireNonNull(resp.body()).string());
        } catch (final Exception e) {
          throw new RuntimeException("Unable to parse response as json Array", e);
        }
        for (int i = 0; i < ((ArrayNode) expectedResponse).size(); i++) {
          checkResponse(
              (ObjectNode) responseBody.get(i),
              (ObjectNode) ((ArrayNode) expectedResponse).get(i),
              getMethod(rawRequestBody),
              getTraceType(specFile.toString()));
        }
      }
    }
  }

  private enum Method {
    TRACE_CALL_MANY,
    OTHER
  }

  private enum TraceType {
    TRACE,
    VM_TRACE,
    STATE_DIFF,
    OTHER
  }

  private Method getMethod(final String rawRequest) {
    if (rawRequest.contains("\"method\":\"trace_callMany\"")) {
      return Method.TRACE_CALL_MANY;
    } else {
      return Method.OTHER;
    }
  }

  private TraceType getTraceType(final String fileName) {
    if (fileName.contains("_trace")) {
      return TraceType.TRACE;
    } else if (fileName.contains("_vmTrace")) {
      return TraceType.VM_TRACE;
    } else if (fileName.contains("_stateDiff")) {
      return TraceType.STATE_DIFF;
    } else {
      return TraceType.OTHER;
    }
  }

  private void checkResponse(
      final ObjectNode responseBody,
      final ObjectNode expectedResponse,
      final Method method,
      final TraceType traceType)
      throws JsonProcessingException {
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

      String expectedResult = expectedResponse.get("result").toString();
      String actualResult = responseBody.get("result").toString();

      if (method.equals(Method.TRACE_CALL_MANY)) {
        // TODO: There are differences in gas cost (causing different balances as well). These are
        // caused by
        // OpenEthereum not implementing "Istanbul" correctly. Specially the SSTORE to dirty storage
        // Slots should cost 800 gas rather than 5000
        // This should be fixed, e.g. by using
        // Erigon to
        // create the expected output, or by making OpenEthereum work correctly.
        switch (traceType) {
          case TRACE:
            expectedResult = filterStringTrace(expectedResult);
            actualResult = filterStringTrace(actualResult);
            break;
          case VM_TRACE:
            expectedResult = filterStringVmTrace(expectedResult);
            actualResult = filterStringVmTrace(actualResult);
            break;
          case STATE_DIFF:
            expectedResult = filterStringStateDiff(expectedResult);
            actualResult = filterStringStateDiff(actualResult);
            break;
          default:
            throw new RuntimeException(
                "Unrecognized trace type (expected trace | vmTrace | stateDiff)");
        }
      }

      final ObjectMapper mapper = new ObjectMapper();
      mapper.configure(INDENT_OUTPUT, true);
      assertThat(
              mapper
                  .writerWithDefaultPrettyPrinter()
                  .withoutAttribute("creationMethod")
                  .writeValueAsString(mapper.readTree(actualResult)))
          .isEqualTo(
              mapper
                  .writerWithDefaultPrettyPrinter()
                  .withoutAttribute("creationMethod")
                  .writeValueAsString(mapper.readTree(expectedResult)));
    }

    // Check error
    if (expectedResponse.has("error")) {
      assertThat(responseBody.has("error")).isTrue();
      final String expectedError = expectedResponse.get("error").toString();
      final String actualError = responseBody.get("error").toString();
      assertThat(actualError).isEqualToIgnoringWhitespace(expectedError);
    }
  }

  private String filterStringStateDiff(final String expectedResult) {
    final Matcher m = GAS_MATCH_FOR_STATE_DIFF.matcher(expectedResult);
    return m.replaceAll("");
  }

  private String filterStringVmTrace(final String expectedResult) {
    final Matcher m = GAS_MATCH_FOR_VM_TRACE.matcher(expectedResult);
    return m.replaceAll("");
  }

  private String filterStringTrace(final String expectedResult) {
    final Matcher m = GAS_MATCH_FOR_TRACE.matcher(expectedResult);
    return m.replaceAll("");
  }
}
