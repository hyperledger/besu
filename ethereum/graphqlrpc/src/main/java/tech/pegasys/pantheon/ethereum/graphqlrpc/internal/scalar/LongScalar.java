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
package tech.pegasys.pantheon.ethereum.graphqlrpc.internal.scalar;

import tech.pegasys.pantheon.util.bytes.Bytes32;

import graphql.Internal;
import graphql.language.IntValue;
import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;

@Internal
public class LongScalar extends GraphQLScalarType {

  public LongScalar() {
    super(
        "Long",
        "Long is a 64 bit unsigned integer",
        new Coercing<Object, Object>() {
          @Override
          public Number serialize(final Object input) throws CoercingSerializeException {
            if (input instanceof Number) {
              return (Number) input;
            } else if (input instanceof String) {
              final String value = ((String) input).toLowerCase();
              if (value.startsWith("0x")) {
                return Bytes32.fromHexStringLenient(value).asUInt256().toLong();
              } else {
                return Long.parseLong(value);
              }
            }
            throw new CoercingSerializeException("Unable to serialize " + input + " as an Long");
          }

          @Override
          public Number parseValue(final Object input) throws CoercingParseValueException {
            if (input instanceof Number) {
              return (Number) input;
            } else if (input instanceof String) {
              final String value = ((String) input).toLowerCase();
              if (value.startsWith("0x")) {
                return Bytes32.fromHexStringLenient(value).asUInt256().toLong();
              } else {
                return Long.parseLong(value);
              }
            }
            throw new CoercingParseValueException(
                "Unable to parse variable value " + input + " as an Long");
          }

          @Override
          public Object parseLiteral(final Object input) throws CoercingParseLiteralException {
            try {
              if (input instanceof IntValue) {
                return ((IntValue) input).getValue().longValue();
              } else if (input instanceof StringValue) {
                final String value = ((StringValue) input).getValue().toLowerCase();
                if (value.startsWith("0x")) {
                  return Bytes32.fromHexStringLenient(value).asUInt256().toLong();
                } else {
                  return Long.parseLong(value);
                }
              }
            } catch (final NumberFormatException e) {
              // fall through
            }
            throw new CoercingParseLiteralException("Value is not any Long : '" + input + "'");
          }
        });
  }
}
