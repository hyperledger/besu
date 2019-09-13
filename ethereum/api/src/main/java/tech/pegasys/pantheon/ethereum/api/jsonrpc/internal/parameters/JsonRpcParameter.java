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
package tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.parameters;

import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.exception.InvalidJsonRpcParameters;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonRpcParameter {

  private static final ObjectMapper mapper = new ObjectMapper();

  /**
   * Retrieves a required parameter at the given index interpreted as the given class. Throws
   * InvalidJsonRpcParameters if parameter is missing or of the wrong type.
   *
   * @param params the list of objects from which to extract a typed object.
   * @param index Which index of the params array to access.
   * @param paramClass What type is expected at this index.
   * @param <T> The type of parameter.
   * @return Returns the parameter cast as T if available, otherwise throws exception.
   */
  public <T> T required(final Object[] params, final int index, final Class<T> paramClass) {
    return optional(params, index, paramClass)
        .orElseThrow(
            () ->
                new InvalidJsonRpcParameters(
                    "Missing required json rpc parameter at index " + index));
  }

  /**
   * Retrieves an optional parameter at the given index interpreted as the given class. Throws
   * InvalidJsonRpcParameters if parameter is of the wrong type.
   *
   * @param params the list of objects from which to extract a typed object.
   * @param index Which index of the params array to access.
   * @param paramClass What type is expected at this index.
   * @param <T> The type of parameter.
   * @return Returns the parameter cast as T if available.
   */
  @SuppressWarnings("unchecked")
  public <T> Optional<T> optional(
      final Object[] params, final int index, final Class<T> paramClass) {
    if (params == null || params.length <= index || params[index] == null) {
      return Optional.empty();
    }

    final T param;
    final Object rawParam = params[index];
    if (paramClass.isAssignableFrom(rawParam.getClass())) {
      // If we're dealing with a simple type, just cast the value
      param = (T) rawParam;
    } else {
      // Otherwise, serialize param back to json and then deserialize to the paramClass type
      try {
        final String json = mapper.writeValueAsString(rawParam);
        param = mapper.readValue(json, paramClass);
      } catch (final JsonProcessingException e) {
        throw new InvalidJsonRpcParameters("Invalid json rpc parameter at index " + index, e);
      } catch (final IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    return Optional.of(param);
  }
}
