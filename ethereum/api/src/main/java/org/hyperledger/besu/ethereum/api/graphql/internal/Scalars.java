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
package org.hyperledger.besu.ethereum.api.graphql.internal;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.Quantity;

import graphql.language.IntValue;
import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.apache.tuweni.units.bigints.UInt256Value;

public class Scalars {

  private static final Coercing<Object, Object> ADDRESS_COERCING =
      new Coercing<Object, Object>() {
        @Override
        public String serialize(final Object input) throws CoercingSerializeException {
          if (input instanceof Address) {
            return input.toString();
          }
          throw new CoercingSerializeException("Unable to serialize " + input + " as an Address");
        }

        @Override
        public String parseValue(final Object input) throws CoercingParseValueException {
          if (input instanceof Address) {
            return input.toString();
          }
          throw new CoercingParseValueException(
              "Unable to parse variable value " + input + " as an Address");
        }

        @Override
        public Address parseLiteral(final Object input) throws CoercingParseLiteralException {
          if (!(input instanceof StringValue)) {
            throw new CoercingParseLiteralException("Value is not any Address : '" + input + "'");
          }
          String inputValue = ((StringValue) input).getValue();
          try {
            return Address.fromHexStringStrict(inputValue);
          } catch (final IllegalArgumentException e) {
            throw new CoercingParseLiteralException("Value is not any Address : '" + input + "'");
          }
        }
      };

  private static final Coercing<Object, Object> BIG_INT_COERCING =
      new Coercing<Object, Object>() {
        @Override
        public String serialize(final Object input) throws CoercingSerializeException {
          if (input instanceof UInt256Value) {
            return ((UInt256Value) input).toShortHexString();
          }
          throw new CoercingSerializeException("Unable to serialize " + input + " as an BigInt");
        }

        @Override
        public String parseValue(final Object input) throws CoercingParseValueException {
          if (input instanceof UInt256Value) {
            return ((UInt256Value) input).toShortHexString();
          }
          throw new CoercingParseValueException(
              "Unable to parse variable value " + input + " as an BigInt");
        }

        @Override
        public UInt256 parseLiteral(final Object input) throws CoercingParseLiteralException {
          try {
            if (input instanceof StringValue) {
              return UInt256.fromHexString(((StringValue) input).getValue());
            } else if (input instanceof IntValue) {
              return UInt256.valueOf(((IntValue) input).getValue());
            }
          } catch (final IllegalArgumentException e) {
            // fall through
          }
          throw new CoercingParseLiteralException("Value is not any BigInt : '" + input + "'");
        }
      };

  private static final Coercing<Object, Object> BYTES_COERCING =
      new Coercing<Object, Object>() {
        @Override
        public String serialize(final Object input) throws CoercingSerializeException {
          if (input instanceof Bytes) {
            return input.toString();
          }
          throw new CoercingSerializeException("Unable to serialize " + input + " as an Bytes");
        }

        @Override
        public String parseValue(final Object input) throws CoercingParseValueException {
          if (input instanceof Bytes) {
            return input.toString();
          }
          throw new CoercingParseValueException(
              "Unable to parse variable value " + input + " as an Bytes");
        }

        @Override
        public Bytes parseLiteral(final Object input) throws CoercingParseLiteralException {
          if (!(input instanceof StringValue)) {
            throw new CoercingParseLiteralException("Value is not any Bytes : '" + input + "'");
          }
          String inputValue = ((StringValue) input).getValue();
          if (!Quantity.isValid(inputValue)) {
            throw new CoercingParseLiteralException(
                "Bytes value '" + inputValue + "' is not prefixed with 0x");
          }
          try {
            return Bytes.fromHexStringLenient(inputValue);
          } catch (final IllegalArgumentException e) {
            throw new CoercingParseLiteralException("Value is not any Bytes : '" + input + "'");
          }
        }
      };

  private static final Coercing<Object, Object> BYTES32_COERCING =
      new Coercing<Object, Object>() {
        @Override
        public String serialize(final Object input) throws CoercingSerializeException {
          if (input instanceof Hash) {
            return ((Hash) input).toString();
          }
          if (input instanceof Bytes32) {
            return input.toString();
          }
          throw new CoercingSerializeException("Unable to serialize " + input + " as an Bytes32");
        }

        @Override
        public String parseValue(final Object input) throws CoercingParseValueException {
          if (input instanceof Bytes32) {
            return input.toString();
          }
          throw new CoercingParseValueException(
              "Unable to parse variable value " + input + " as an Bytes32");
        }

        @Override
        public Bytes32 parseLiteral(final Object input) throws CoercingParseLiteralException {
          if (!(input instanceof StringValue)) {
            throw new CoercingParseLiteralException("Value is not any Bytes32 : '" + input + "'");
          }
          String inputValue = ((StringValue) input).getValue();
          if (!Quantity.isValid(inputValue)) {
            throw new CoercingParseLiteralException(
                "Bytes32 value '" + inputValue + "' is not prefixed with 0x");
          }
          try {
            return Bytes32.fromHexStringLenient(inputValue);
          } catch (final IllegalArgumentException e) {
            throw new CoercingParseLiteralException("Value is not any Bytes32 : '" + input + "'");
          }
        }
      };

  private static final Coercing<Object, Object> LONG_COERCING =
      new Coercing<Object, Object>() {
        @Override
        public Number serialize(final Object input) throws CoercingSerializeException {
          if (input instanceof Number) {
            return (Number) input;
          } else if (input instanceof String) {
            final String value = ((String) input).toLowerCase();
            if (value.startsWith("0x")) {
              return Bytes.fromHexStringLenient(value).toLong();
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
              return Bytes.fromHexStringLenient(value).toLong();
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
                return Bytes.fromHexStringLenient(value).toLong();
              } else {
                return Long.parseLong(value);
              }
            }
          } catch (final NumberFormatException e) {
            // fall through
          }
          throw new CoercingParseLiteralException("Value is not any Long : '" + input + "'");
        }
      };

  public static GraphQLScalarType addressScalar() {
    return GraphQLScalarType.newScalar()
        .name("Address")
        .description("Address scalar")
        .coercing(ADDRESS_COERCING)
        .build();
  }

  public static GraphQLScalarType bigIntScalar() {
    return GraphQLScalarType.newScalar()
        .name("BigInt")
        .description("A BigInt (UInt256) scalar")
        .coercing(BIG_INT_COERCING)
        .build();
  }

  public static GraphQLScalarType bytesScalar() {
    return GraphQLScalarType.newScalar()
        .name("Bytes")
        .description("A Bytes scalar")
        .coercing(BYTES_COERCING)
        .build();
  }

  public static GraphQLScalarType bytes32Scalar() {
    return GraphQLScalarType.newScalar()
        .name("Bytes32")
        .description("A Bytes32 scalar")
        .coercing(BYTES32_COERCING)
        .build();
  }

  public static GraphQLScalarType longScalar() {
    return GraphQLScalarType.newScalar()
        .name("Long")
        .description("A Long (UInt64) scalar")
        .coercing(LONG_COERCING)
        .build();
  }
}
