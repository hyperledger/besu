/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.tests.acceptance.dsl.httptransaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import io.vertx.core.json.JsonObject;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class HttpRequestFactory {
  private final String uri;
  private final OkHttpClient client;
  private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

  public HttpRequestFactory(final String uri) {
    this.uri = uri;
    client = new OkHttpClient();
  }

  public JsonObject loginSuccessful(final String username, final String password)
      throws IOException {
    final RequestBody requestBody =
        RequestBody.create(
            JSON, "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
    final Request request = new Request.Builder().post(requestBody).url(uri + "/login").build();
    try (final Response response = client.newCall(request).execute()) {

      assertThat(response.code()).isEqualTo(200);
      assertThat(response.message()).isEqualTo("OK");
      final ResponseBody responseBody = response.body();
      assertThat(responseBody.contentType()).isNotNull();
      assertThat(responseBody.contentType().type()).isEqualTo("application");
      assertThat(responseBody.contentType().subtype()).isEqualTo("json");
      final String bodyString = responseBody.string();
      assertThat(bodyString).isNotNull();
      assertThat(bodyString).isNotBlank();

      return new JsonObject(bodyString);
    }
  }

  public void loginUnauthorized(final String username, final String password) throws IOException {
    final RequestBody requestBody =
        RequestBody.create(
            JSON, "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
    final Request request = new Request.Builder().post(requestBody).url(uri + "/login").build();
    try (final Response response = client.newCall(request).execute()) {
      assertThat(response.code()).isEqualTo(401);
      assertThat(response.message()).isEqualTo("Unauthorized");
    }
  }

  public String loginResponds(final String username, final String password) throws IOException {
    final RequestBody requestBody =
        RequestBody.create(
            JSON, "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
    final Request request = new Request.Builder().post(requestBody).url(uri + "/login").build();
    try (final Response response = client.newCall(request).execute()) {
      assertThat(response).isNotNull();
      assertThat(response.message()).isNotNull();
      return response.message();
    }
  }
}
