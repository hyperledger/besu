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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.response;

import org.hyperledger.besu.plugin.services.rpc.RpcResponseType;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;

/**
 * A JSON-RPC success response that streams the result field directly to a JsonGenerator, avoiding
 * materializing the entire result in memory. Used for large responses like debug_traceBlock.
 */
public class StreamingJsonRpcSuccessResponse implements JsonRpcResponse {

  private final Object id;
  private final ResultWriter resultWriter;

  @FunctionalInterface
  public interface ResultWriter {
    void writeResult(JsonGenerator generator) throws IOException;
  }

  public StreamingJsonRpcSuccessResponse(final Object id, final ResultWriter resultWriter) {
    this.id = id;
    this.resultWriter = resultWriter;
  }

  public Object getId() {
    return id;
  }

  public void writeTo(final JsonGenerator generator) throws IOException {
    generator.writeStartObject();
    generator.writeStringField("jsonrpc", "2.0");
    generator.writeFieldName("id");
    generator.writeObject(id);
    generator.writeFieldName("result");
    resultWriter.writeResult(generator);
    generator.writeEndObject();
    generator.flush();
  }

  @Override
  public RpcResponseType getType() {
    return RpcResponseType.SUCCESS;
  }
}
