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
package org.hyperledger.besu.config;

import org.hyperledger.besu.util.number.PositiveNumber;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class JsonUtil {

  /**
   * Converts all the object keys (but none of the string values) to lowercase for easier lookup.
   * This is useful in cases such as the 'genesis.json' file where all keys are assumed to be case
   * insensitive.
   *
   * @param objectNode The ObjectNode to be normalized
   * @return a copy of the json object with all keys in lower case.
   */
  public static ObjectNode normalizeKeys(final ObjectNode objectNode) {
    final ObjectNode normalized = JsonUtil.createEmptyObjectNode();
    objectNode
        .fields()
        .forEachRemaining(
            entry -> {
              final String key = entry.getKey();
              final JsonNode value = entry.getValue();
              final String normalizedKey = key.toLowerCase(Locale.US);
              if (value instanceof ObjectNode) {
                normalized.set(normalizedKey, normalizeKeys((ObjectNode) value));
              } else if (value instanceof ArrayNode) {
                normalized.set(normalizedKey, normalizeKeysInArray((ArrayNode) value));
              } else {
                normalized.set(normalizedKey, value);
              }
            });
    return normalized;
  }

  private static ArrayNode normalizeKeysInArray(final ArrayNode arrayNode) {
    final ArrayNode normalizedArray = JsonUtil.createEmptyArrayNode();
    arrayNode.forEach(
        value -> {
          if (value instanceof ObjectNode) {
            normalizedArray.add(normalizeKeys((ObjectNode) value));
          } else if (value instanceof ArrayNode) {
            normalizedArray.add(normalizeKeysInArray((ArrayNode) value));
          } else {
            normalizedArray.add(value);
          }
        });
    return normalizedArray;
  }

  /**
   * Get the string representation of the value at {@code key}. For example, a numeric value like 5
   * will be returned as "5".
   *
   * @param node The {@code ObjectNode} from which the value will be extracted.
   * @param key The key corresponding to the value to extract.
   * @return The value at the given key as a string if it exists.
   */
  public static Optional<String> getValueAsString(final ObjectNode node, final String key) {
    return getValue(node, key).map(JsonNode::asText);
  }

  /**
   * Get the string representation of the value at {@code key}. For example, a numeric value like 5
   * will be returned as "5".
   *
   * @param node The {@code ObjectNode} from which the value will be extracted.
   * @param key The key corresponding to the value to extract.
   * @param defaultValue The value to return if no value is found at {@code key}.
   * @return The value at the given key as a string if it exists, otherwise {@code defaultValue}
   */
  public static String getValueAsString(
      final ObjectNode node, final String key, final String defaultValue) {
    return getValueAsString(node, key).orElse(defaultValue);
  }

  /**
   * Checks whether an {@code ObjectNode} contains the given key.
   *
   * @param node The {@code ObjectNode} to inspect.
   * @param key The key to check.
   * @return Returns true if the given key is set.
   */
  public static boolean hasKey(final ObjectNode node, final String key) {
    return node.has(key);
  }

  /**
   * Returns textual (string) value at {@code key}. See {@link #getValueAsString} for retrieving
   * non-textual values in string form.
   *
   * @param node The {@code ObjectNode} from which the value will be extracted.
   * @param key The key corresponding to the value to extract.
   * @return The textual value at {@code key} if it exists.
   */
  public static Optional<String> getString(final ObjectNode node, final String key) {
    return getValue(node, key)
        .filter(jsonNode -> validateType(jsonNode, JsonNodeType.STRING))
        .map(JsonNode::asText);
  }

  /**
   * Returns textual (string) value at {@code key}. See {@link #getValueAsString} for retrieving
   * non-textual values in string form.
   *
   * @param node The {@code ObjectNode} from which the value will be extracted.
   * @param key The key corresponding to the value to extract.
   * @param defaultValue The value to return if no value is found at {@code key}.
   * @return The textual value at {@code key} if it exists, otherwise {@code defaultValue}
   */
  public static String getString(
      final ObjectNode node, final String key, final String defaultValue) {
    return getString(node, key).orElse(defaultValue);
  }

  public static OptionalInt getInt(final ObjectNode node, final String key) {
    return getValue(node, key)
        .filter(jsonNode -> validateType(jsonNode, JsonNodeType.NUMBER))
        .filter(JsonUtil::validateInt)
        .map(JsonNode::asInt)
        .map(OptionalInt::of)
        .orElse(OptionalInt.empty());
  }

  public static int getInt(final ObjectNode node, final String key, final int defaultValue) {
    return getInt(node, key).orElse(defaultValue);
  }

  public static OptionalInt getPositiveInt(final ObjectNode node, final String key) {
    return getValueAsString(node, key)
        .map(v -> OptionalInt.of(parsePositiveInt(key, v)))
        .orElse(OptionalInt.empty());
  }

  public static int getPositiveInt(
      final ObjectNode node, final String key, final int defaultValue) {
    final String value = getValueAsString(node, key, String.valueOf(defaultValue));
    return parsePositiveInt(key, value);
  }

  private static int parsePositiveInt(final String key, final String value) {
    try {
      return PositiveNumber.fromString(value).getValue();
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Invalid property value, " + key + " should be a positive integer: " + value);
    }
  }

  public static OptionalLong getLong(final ObjectNode json, final String key) {
    return getValue(json, key)
        .filter(jsonNode -> validateType(jsonNode, JsonNodeType.NUMBER))
        .filter(JsonUtil::validateLong)
        .map(JsonNode::asLong)
        .map(OptionalLong::of)
        .orElse(OptionalLong.empty());
  }

  public static long getLong(final ObjectNode json, final String key, final long defaultValue) {
    return getLong(json, key).orElse(defaultValue);
  }

  public static Optional<Boolean> getBoolean(final ObjectNode node, final String key) {
    return getValue(node, key)
        .filter(jsonNode -> validateType(jsonNode, JsonNodeType.BOOLEAN))
        .map(JsonNode::asBoolean);
  }

  public static boolean getBoolean(
      final ObjectNode node, final String key, final boolean defaultValue) {
    return getBoolean(node, key).orElse(defaultValue);
  }

  public static ObjectNode createEmptyObjectNode() {
    final ObjectMapper mapper = getObjectMapper();
    return mapper.createObjectNode();
  }

  public static ArrayNode createEmptyArrayNode() {
    final ObjectMapper mapper = getObjectMapper();
    return mapper.createArrayNode();
  }

  public static ObjectNode objectNodeFromMap(final Map<String, Object> map) {
    return (ObjectNode) getObjectMapper().valueToTree(map);
  }

  public static ObjectNode objectNodeFromString(final String jsonData) {
    return objectNodeFromString(jsonData, false);
  }

  public static ObjectNode objectNodeFromString(
      final String jsonData, final boolean allowComments) {
    final ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.configure(Feature.ALLOW_COMMENTS, allowComments);
    try {
      final JsonNode jsonNode = objectMapper.readTree(jsonData);
      validateType(jsonNode, JsonNodeType.OBJECT);
      return (ObjectNode) jsonNode;
    } catch (final IOException e) {
      // Reading directly from a string should not raise an IOException, just catch and rethrow
      throw new RuntimeException(e);
    }
  }

  public static String getJson(final Object objectNode) throws JsonProcessingException {
    return getJson(objectNode, true);
  }

  public static String getJson(final Object objectNode, final boolean prettyPrint)
      throws JsonProcessingException {
    final ObjectMapper mapper = getObjectMapper();
    if (prettyPrint) {
      return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectNode);
    } else {
      return mapper.writeValueAsString(objectNode);
    }
  }

  public static ObjectMapper getObjectMapper() {
    return new ObjectMapper();
  }

  public static Optional<ObjectNode> getObjectNode(final ObjectNode json, final String fieldKey) {
    return getObjectNode(json, fieldKey, true);
  }

  public static Optional<ObjectNode> getObjectNode(
      final ObjectNode json, final String fieldKey, final boolean strict) {
    final JsonNode obj = json.get(fieldKey);
    if (obj == null || obj.isNull()) {
      return Optional.empty();
    }

    if (!obj.isObject()) {
      if (strict) {
        validateType(obj, JsonNodeType.OBJECT);
      } else {
        return Optional.empty();
      }
    }

    return Optional.of((ObjectNode) obj);
  }

  public static Optional<ArrayNode> getArrayNode(final ObjectNode json, final String fieldKey) {
    return getArrayNode(json, fieldKey, true);
  }

  public static Optional<ArrayNode> getArrayNode(
      final ObjectNode json, final String fieldKey, final boolean strict) {
    final JsonNode obj = json.get(fieldKey);
    if (obj == null || obj.isNull()) {
      return Optional.empty();
    }

    if (!obj.isArray()) {
      if (strict) {
        validateType(obj, JsonNodeType.ARRAY);
      } else {
        return Optional.empty();
      }
    }

    return Optional.of((ArrayNode) obj);
  }

  private static Optional<JsonNode> getValue(final ObjectNode node, final String key) {
    final JsonNode jsonNode = node.get(key);
    if (jsonNode == null || jsonNode.isNull()) {
      return Optional.empty();
    }
    return Optional.of(jsonNode);
  }

  private static boolean validateType(final JsonNode node, final JsonNodeType expectedType) {
    if (node.getNodeType() != expectedType) {
      final String errorMessage =
          String.format(
              "Expected %s value but got %s",
              expectedType.toString().toLowerCase(), node.getNodeType().toString().toLowerCase());
      throw new IllegalArgumentException(errorMessage);
    }
    return true;
  }

  private static boolean validateLong(final JsonNode node) {
    if (!node.canConvertToLong()) {
      throw new IllegalArgumentException("Cannot convert value to long: " + node.toString());
    }
    return true;
  }

  private static boolean validateInt(final JsonNode node) {
    if (!node.canConvertToInt()) {
      throw new IllegalArgumentException("Cannot convert value to integer: " + node.toString());
    }
    return true;
  }
}
