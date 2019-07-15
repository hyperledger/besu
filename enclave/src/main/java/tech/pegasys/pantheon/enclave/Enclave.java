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
package tech.pegasys.pantheon.enclave;

import tech.pegasys.pantheon.enclave.types.CreatePrivacyGroupRequest;
import tech.pegasys.pantheon.enclave.types.DeletePrivacyGroupRequest;
import tech.pegasys.pantheon.enclave.types.ErrorResponse;
import tech.pegasys.pantheon.enclave.types.FindPrivacyGroupRequest;
import tech.pegasys.pantheon.enclave.types.FindPrivacyGroupResponse;
import tech.pegasys.pantheon.enclave.types.PrivacyGroup;
import tech.pegasys.pantheon.enclave.types.ReceiveRequest;
import tech.pegasys.pantheon.enclave.types.ReceiveResponse;
import tech.pegasys.pantheon.enclave.types.SendRequest;
import tech.pegasys.pantheon.enclave.types.SendResponse;

import java.io.IOException;
import java.net.URI;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Enclave {
  private static final Logger LOG = LogManager.getLogger();
  private static final ObjectMapper objectMapper = new ObjectMapper();
  private static final MediaType JSON = MediaType.parse("application/json");
  private static final MediaType ORION = MediaType.get("application/vnd.orion.v1+json");

  private final URI enclaveUri;
  private final OkHttpClient client;

  public Enclave(final URI enclaveUri) {
    this.enclaveUri = enclaveUri;
    this.client = new OkHttpClient();
  }

  public boolean upCheck() throws IOException {
    final String url = enclaveUri.resolve("/upcheck").toString();
    final Request request = new Request.Builder().url(url).get().build();

    try (Response response = client.newCall(request).execute()) {
      return response.isSuccessful();
    } catch (IOException e) {
      LOG.error("Enclave failed to execute upcheck");
      throw new IOException("Failed to perform upcheck", e);
    }
  }

  public SendResponse send(final SendRequest content) throws Exception {
    return executePost(buildPostRequest(JSON, content, "/send"), SendResponse.class);
  }

  public ReceiveResponse receive(final ReceiveRequest content) throws Exception {
    return executePost(buildPostRequest(ORION, content, "/receive"), ReceiveResponse.class);
  }

  public PrivacyGroup createPrivacyGroup(final CreatePrivacyGroupRequest content) throws Exception {
    return executePost(buildPostRequest(JSON, content, "/createPrivacyGroup"), PrivacyGroup.class);
  }

  public String deletePrivacyGroup(final DeletePrivacyGroupRequest content) throws Exception {
    return executePost(buildPostRequest(JSON, content, "/deletePrivacyGroup"), String.class);
  }

  public FindPrivacyGroupResponse[] findPrivacyGroup(final FindPrivacyGroupRequest content)
      throws Exception {
    Request request = buildPostRequest(JSON, content, "/findPrivacyGroup");
    return executePost(request, FindPrivacyGroupResponse[].class);
  }

  private Request buildPostRequest(
      final MediaType mediaType, final Object content, final String endpoint) throws Exception {
    final RequestBody body =
        RequestBody.create(mediaType, objectMapper.writeValueAsString(content));
    final String url = enclaveUri.resolve(endpoint).toString();
    return new Request.Builder().url(url).post(body).build();
  }

  private <T> T executePost(final Request request, final Class<T> responseType) throws Exception {
    try (Response response = client.newCall(request).execute()) {
      if (response.isSuccessful()) {
        return objectMapper.readValue(response.body().string(), responseType);
      } else {
        final ErrorResponse errorResponse =
            objectMapper.readValue(response.body().string(), ErrorResponse.class);
        throw new EnclaveException(errorResponse.getError());
      }
    } catch (Exception e) {
      LOG.error("Enclave failed to execute {}", request, e);
      throw e;
    }
  }
}
