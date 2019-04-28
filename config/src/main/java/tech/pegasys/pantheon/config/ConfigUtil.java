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
package tech.pegasys.pantheon.config;

import java.math.BigInteger;
import java.util.Optional;
import java.util.OptionalLong;

import io.vertx.core.json.JsonObject;

public class ConfigUtil {
  public static OptionalLong getOptionalLong(final JsonObject jsonObject, final String key) {
    return jsonObject.containsKey(key)
        ? OptionalLong.of(jsonObject.getLong(key))
        : OptionalLong.empty();
  }

  public static Optional<BigInteger> getOptionalBigInteger(
      final JsonObject jsonObject, final String key) {
    return jsonObject.containsKey(key)
        ? Optional.ofNullable(getBigInteger(jsonObject, key))
        : Optional.empty();
  }

  private static BigInteger getBigInteger(final JsonObject jsonObject, final String key) {
    final Number value = (Number) jsonObject.getMap().get(key);
    if (value == null) {
      return null;
    } else if (value instanceof BigInteger) {
      return (BigInteger) value;
    } else {
      return BigInteger.valueOf(value.longValue());
    }
  }
}
