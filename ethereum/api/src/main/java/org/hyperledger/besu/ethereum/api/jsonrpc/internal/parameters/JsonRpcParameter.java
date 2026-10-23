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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters;

import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcObjectMapperFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.SequencedSet;
import java.util.Set;
import java.util.function.IntFunction;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsonRpcParameter {
  private static final Logger LOG = LoggerFactory.getLogger(JsonRpcParameter.class);

  /**
   * Jackson's default {@link ObjectMapper}. Classes that need to tolerate unknown JSON properties
   * must opt out individually via {@code @JsonIgnoreProperties(ignoreUnknown = true)} — without
   * that annotation, an unknown property fails deserialization regardless of its value.
   */
  private static final ObjectMapper mapperDefault = JsonRpcObjectMapperFactory.getParameterMapper();

  /**
   * Like mapperDefault but with {@link DeserializationFeature#FAIL_ON_UNKNOWN_PROPERTIES}
   * explicitly enabled and a handler attached: unknown JSON properties whose value is {@code null}
   * are silently dropped, while unknown properties with a non-{@code null} value still cause
   * deserialization to fail.
   */
  private static final ObjectMapper mapperFailOnUnknownButNull =
      JsonRpcObjectMapperFactory.getParameterMapperIgnoringUnknownNulls();

  /** As mapperFailOnUnknownButNull, but empty unknown properties are dropped too. */
  private static final ObjectMapper mapperFailOnUnknownButEmpty =
      JsonRpcObjectMapperFactory.getParameterMapperIgnoringUnknownEmpties();

  /**
   * Retrieves a required parameter at the given index interpreted as the given class. Throws
   * InvalidJsonRpcParameters if the parameter is missing or of the wrong type.
   *
   * @param params the list of objects from which to extract a typed object.
   * @param index Which index of the params array to access.
   * @param paramClass What type is expected at this index.
   * @param configuration the {@link Configuration} controlling deserialization behaviour (e.g.
   *     {@link Configuration#DEFAULT} for Jackson's default mapper, {@link
   *     Configuration#FAIL_ON_UNKNOWN_BUT_NULL} to reject unknown JSON properties unless their
   *     value is {@code null}).
   * @param <T> The type of parameter.
   * @return Returns the parameter cast as T if available, otherwise throws exception.
   * @throws JsonRpcParameterException if the parameter is missing or fails deserialization
   */
  public <T> T required(
      final Object[] params,
      final int index,
      final Class<T> paramClass,
      final Configuration configuration)
      throws JsonRpcParameterException {
    return optional(params, index, paramClass, configuration)
        .orElseThrow(
            () ->
                new JsonRpcMissingParameterException(
                    "Missing required json rpc parameter at index " + index));
  }

  public <T> T required(final Object[] params, final int index, final Class<T> paramClass)
      throws JsonRpcParameterException {
    return required(params, index, paramClass, Configuration.DEFAULT);
  }

  /**
   * Retrieves an optional parameter at the given index interpreted as the given class. Throws
   * InvalidJsonRpcParameters if parameter is of the wrong type.
   *
   * @param params the list of objects from which to extract a typed object.
   * @param index Which index of the params array to access.
   * @param paramClass What type is expected at this index.
   * @param configuration the {@link Configuration} controlling deserialization behaviour (e.g.
   *     {@link Configuration#DEFAULT} for Jackson's default mapper, {@link
   *     Configuration#FAIL_ON_UNKNOWN_BUT_NULL} to reject unknown JSON properties unless their
   *     value is {@code null}).
   * @param <T> The type of parameter.
   * @return Returns the parameter cast as T if available.
   * @throws JsonRpcParameterException if the parameter is present but fails deserialization
   */
  public <T> Optional<T> optional(
      final Object[] params,
      final int index,
      final Class<T> paramClass,
      final Configuration configuration)
      throws JsonRpcParameterException {
    if (params == null || params.length <= index || params[index] == null) {
      return Optional.empty();
    }

    final T param;
    final Object rawParam = params[index];
    if (paramClass.isAssignableFrom(rawParam.getClass())) {
      // If we're dealing with a simple type, just cast the value
      param = paramClass.cast(rawParam);
    } else {
      try {
        param = configuration.mapper.convertValue(rawParam, paramClass);
      } catch (final Exception e) {
        throw new JsonRpcParameterException(
            String.format(
                "Invalid json rpc parameter at index %d. Supplied value was: '%s' of type: '%s' - expected type: '%s'",
                index, rawParam, rawParam.getClass().getName(), paramClass.getName()),
            e);
      }
    }

    return Optional.of(param);
  }

  public <T> Optional<T> optional(final Object[] params, final int index, final Class<T> paramClass)
      throws JsonRpcParameterException {
    return optional(params, index, paramClass, Configuration.DEFAULT);
  }

  public <T> List<T> requiredList(final Object[] params, final int index, final Class<T> listClass)
      throws JsonRpcParameterException {
    return requiredList(params, index, listClass, Configuration.DEFAULT);
  }

  public <T> List<T> requiredList(
      final Object[] params,
      final int index,
      final Class<T> listClass,
      final Configuration configuration)
      throws JsonRpcParameterException {
    final Optional<List<T>> value = optionalList(params, index, listClass, configuration);
    if (value.isPresent()) {
      return value.get();
    }

    if (params != null && params.length > index && params[index] != null) {
      final Object rawParam = params[index];
      throw new JsonRpcParameterException(
          String.format(
              "Invalid json rpc parameter at index %d. Supplied value was: '%s' of type: '%s' - expected a list of '%s'",
              index, rawParam, rawParam.getClass().getName(), listClass.getName()));
    }
    throw new JsonRpcMissingParameterException(
        "Missing required json rpc parameter at index " + index);
  }

  /**
   * Retrieves a required list parameter at the given index whose elements are interpreted as the
   * given class. Unlike {@link #requiredList}, deserialization is lenient on single elements: an
   * element that is {@code null} or fails deserialization is skipped, and does not prevent the
   * other elements from being returned. The order of the elements in the list is kept.
   *
   * @param params the list of objects from which to extract a typed object.
   * @param index Which index of the params array to access.
   * @param elementClass What element type is expected at this index.
   * @param configuration the {@link Configuration} controlling deserialization behaviour of each
   *     element.
   * @param <T> The element type of the list parameter.
   * @return the list of the elements that were successfully deserialized, possibly empty
   * @throws JsonRpcParameterException if the parameter is missing or is not a list
   */
  public <T> List<T> requiredLenientList(
      final Object[] params,
      final int index,
      final Class<T> elementClass,
      final Configuration configuration)
      throws JsonRpcParameterException {
    return requiredLenientCollection(params, index, elementClass, configuration, ArrayList::new);
  }

  /**
   * Like {@link #requiredLenientList}, but returns the elements as a set that keeps the order of
   * the elements in the list. Duplicate elements are returned once, at the position of their first
   * occurrence.
   *
   * @param params the list of objects from which to extract a typed object.
   * @param index Which index of the params array to access.
   * @param elementClass What element type is expected at this index.
   * @param configuration the {@link Configuration} controlling deserialization behaviour of each
   *     element.
   * @param <T> The element type of the list parameter.
   * @return the set of the elements that were successfully deserialized, possibly empty
   * @throws JsonRpcParameterException if the parameter is missing or is not a list
   */
  public <T> SequencedSet<T> requiredLenientSequencedSet(
      final Object[] params,
      final int index,
      final Class<T> elementClass,
      final Configuration configuration)
      throws JsonRpcParameterException {
    return requiredLenientCollection(
        params, index, elementClass, configuration, LinkedHashSet::newLinkedHashSet);
  }

  /**
   * Like {@link #requiredLenientList}, but returns the elements as a set, without any guarantee on
   * their order. Duplicate elements are returned once. Use {@link #requiredLenientSequencedSet} if
   * the order of the elements in the list must be kept.
   *
   * @param params the list of objects from which to extract a typed object.
   * @param index Which index of the params array to access.
   * @param elementClass What element type is expected at this index.
   * @param configuration the {@link Configuration} controlling deserialization behaviour of each
   *     element.
   * @param <T> The element type of the list parameter.
   * @return the set of the elements that were successfully deserialized, possibly empty
   * @throws JsonRpcParameterException if the parameter is missing or is not a list
   */
  public <T> Set<T> requiredLenientSet(
      final Object[] params,
      final int index,
      final Class<T> elementClass,
      final Configuration configuration)
      throws JsonRpcParameterException {
    return requiredLenientCollection(
        params, index, elementClass, configuration, HashSet::newHashSet);
  }

  public <T> Set<T> requiredLenientSet(
      final Object[] params,
      final int index,
      final Class<T> elementClass,
      final IntFunction<Set<T>> setFactory,
      final Configuration configuration)
      throws JsonRpcParameterException {
    return requiredLenientCollection(params, index, elementClass, configuration, setFactory);
  }

  /**
   * Deserializes the elements of the list parameter at the given index directly into the collection
   * created by the given factory, skipping the elements that are {@code null} or fail
   * deserialization.
   *
   * @param collectionFactory creates the collection to fill, given the number of raw elements
   */
  private <T, C extends Collection<T>> C requiredLenientCollection(
      final Object[] params,
      final int index,
      final Class<T> elementClass,
      final Configuration configuration,
      final IntFunction<C> collectionFactory)
      throws JsonRpcParameterException {
    if (params == null || params.length <= index || params[index] == null) {
      throw new JsonRpcMissingParameterException(
          "Missing required json rpc parameter at index " + index);
    }

    final Object rawParam = params[index];
    if (!(rawParam instanceof List<?> rawList)) {
      throw new JsonRpcParameterException(
          String.format(
              "Invalid json rpc parameter at index %d. Supplied value was: '%s' of type: '%s' - expected a list of '%s'",
              index, rawParam, rawParam.getClass().getName(), elementClass.getName()));
    }

    final C elements = collectionFactory.apply(rawList.size());
    for (int i = 0; i < rawList.size(); i++) {
      final Object rawElement = rawList.get(i);
      if (rawElement == null) {
        LOG.debug("Skipping null element {} of json rpc parameter at index {}", i, index);
        continue;
      }
      try {
        elements.add(
            elementClass.isInstance(rawElement)
                ? elementClass.cast(rawElement)
                : configuration.mapper.convertValue(rawElement, elementClass));
      } catch (final Exception e) {
        // a custom deserializer can throw any exception (e.g. an RLP decoding error)
        LOG.atDebug()
            .setMessage(
                "Skipping element {} of json rpc parameter at index {}, since it is not a valid '{}': '{}'")
            .addArgument(i)
            .addArgument(index)
            .addArgument(elementClass::getName)
            .addArgument(rawElement)
            .setCause(e)
            .log();
      }
    }
    return elements;
  }

  /**
   * Retrieves an optional list parameter at the given index whose elements are interpreted as the
   * given class.
   *
   * @param params the list of objects from which to extract a typed object.
   * @param index Which index of the params array to access.
   * @param listClass What element type is expected at this index.
   * @param configuration the {@link Configuration} controlling deserialization behaviour (e.g.
   *     {@link Configuration#DEFAULT} for Jackson's default mapper, {@link
   *     Configuration#FAIL_ON_UNKNOWN_BUT_NULL} to reject unknown JSON properties unless their
   *     value is {@code null}).
   * @param <T> The element type of the list parameter.
   * @return Returns the list cast as {@code List<T>} if available; empty when the value at index is
   *     missing/null or is not a list.
   * @throws JsonRpcParameterException if the value at index is a list but fails deserialization
   */
  public <T> Optional<List<T>> optionalList(
      final Object[] params,
      final int index,
      final Class<T> listClass,
      final Configuration configuration)
      throws JsonRpcParameterException {
    if (params == null || params.length <= index || params[index] == null) {
      return Optional.empty();
    }
    Object rawParam = params[index];
    if (List.class.isAssignableFrom(rawParam.getClass())) {
      try {
        List<T> returnedList =
            configuration.mapper.convertValue(
                rawParam,
                configuration
                    .mapper
                    .getTypeFactory()
                    .constructCollectionType(List.class, listClass));
        return Optional.of(returnedList);
      } catch (Exception e) {
        throw new JsonRpcParameterException(
            String.format(
                "Invalid json rpc parameter at index %d. Supplied value was: '%s' of type: '%s' - expected type: '%s'",
                index, rawParam, rawParam.getClass().getName(), listClass.getName()),
            e);
      }
    }
    return Optional.empty();
  }

  public <T> Optional<List<T>> optionalList(
      final Object[] params, final int index, final Class<T> listClass)
      throws JsonRpcParameterException {
    return optionalList(params, index, listClass, Configuration.DEFAULT);
  }

  public static class JsonRpcParameterException extends Exception {
    public JsonRpcParameterException(final String message) {
      super(message);
    }

    public JsonRpcParameterException(final String message, final Throwable cause) {
      super(message, cause);
    }
  }

  public static class JsonRpcMissingParameterException extends JsonRpcParameterException {
    public JsonRpcMissingParameterException(final String message) {
      super(message);
    }
  }

  /**
   * Controls how Jackson deserializes the raw parameter into the requested target class. Callers
   * pick the configuration that matches the strictness their RPC method needs.
   */
  public enum Configuration {
    /**
     * Jackson's default {@link ObjectMapper}. Classes that need to tolerate unknown JSON properties
     * must opt out individually via {@code @JsonIgnoreProperties(ignoreUnknown = true)} — without
     * that annotation, an unknown property fails deserialization regardless of its value.
     */
    DEFAULT(mapperDefault),
    /**
     * Like {@link #DEFAULT} but with {@link DeserializationFeature#FAIL_ON_UNKNOWN_PROPERTIES}
     * explicitly enabled and a handler attached: unknown JSON properties whose value is {@code
     * null} are silently dropped, while unknown properties with a non-{@code null} value still
     * cause deserialization to fail.
     */
    FAIL_ON_UNKNOWN_BUT_NULL(mapperFailOnUnknownButNull),
    /**
     * As {@link #FAIL_ON_UNKNOWN_BUT_NULL}, but empty unknown properties are dropped too. For
     * engine payloads, where an empty unknown field states nothing and so must be judged by block
     * validation rather than rejected as invalid params.
     */
    FAIL_ON_UNKNOWN_BUT_EMPTY(mapperFailOnUnknownButEmpty);

    final ObjectMapper mapper;

    Configuration(final ObjectMapper mapper) {
      this.mapper = mapper;
    }
  }
}
