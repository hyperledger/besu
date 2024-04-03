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

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.util.Set;

import io.vertx.core.json.JsonObject;

public class JsonRpcTestHelper {

  protected void assertValidJsonRpcResult(final JsonObject json, final Object id) {
    // Check all expected fieldnames are set
    final Set<String> fieldNames = json.fieldNames();
    assertThat(fieldNames.size()).isEqualTo(3);
    assertThat(fieldNames.contains("id")).isTrue();
    assertThat(fieldNames.contains("jsonrpc")).isTrue();
    assertThat(fieldNames.contains("result")).isTrue();

    // Check standard field values
    assertIdMatches(json, id);
    assertThat(json.getString("jsonrpc")).isEqualTo("2.0");
  }

  protected void assertValidJsonRpcError(
      final JsonObject json, final Object id, final int errorCode, final String errorMessage) {
    assertValidJsonRpcError(json, id, errorCode, errorMessage, null);
  }

  protected void assertValidJsonRpcError(
      final JsonObject json,
      final Object id,
      final int errorCode,
      final String errorMessage,
      final String data) {
    // Check all expected fieldnames are set
    final Set<String> fieldNames = json.fieldNames();
    assertThat(fieldNames.size()).isEqualTo(3);
    assertThat(fieldNames.contains("id")).isTrue();
    assertThat(fieldNames.contains("jsonrpc")).isTrue();
    assertThat(fieldNames.contains("error")).isTrue();

    // Check standard field values
    assertIdMatches(json, id);
    assertThat(json.getString("jsonrpc")).isEqualTo("2.0");

    // Check error format
    final JsonObject error = json.getJsonObject("error");
    final Set<String> errorFieldNames = error.fieldNames();
    assertThat(errorFieldNames.size()).isEqualTo(data == null ? 2 : 3);
    assertThat(errorFieldNames.contains("code")).isTrue();
    assertThat(errorFieldNames.contains("message")).isTrue();
    if (data != null) {
      assertThat(errorFieldNames.contains("data")).isTrue();
    }

    // Check error field values
    assertThat(error.getInteger("code")).isEqualTo(errorCode);
    assertThat(error.getString("message")).isEqualTo(errorMessage);
    if (data != null) {
      assertThat(error.getString("data")).isEqualTo(data);
    }
  }

  protected void assertIdMatches(final JsonObject json, final Object expectedId) {
    final Object actualId = json.getValue("id");
    if (expectedId == null) {
      assertThat(actualId).isNull();
      return;
    }

    assertThat(expectedId)
        .isInstanceOfAny(
            String.class, Integer.class, Long.class, Float.class, Double.class, BigInteger.class);
    assertThat(actualId).isInstanceOf(expectedId.getClass());
    assertThat(actualId.toString()).isEqualTo(expectedId.toString());
  }
}
