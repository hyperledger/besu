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
import org.hyperledger.besu.datatypes.VersionedHash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.results.Quantity;

import java.math.BigInteger;
import java.util.Locale;

import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.IntValue;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

/**
 * The Scalars class provides methods for creating GraphQLScalarType objects. These objects
 * represent the scalar types used in GraphQL, such as Address, BigInt, Bytes, Bytes32, and Long.
 * Each method in this class returns a GraphQLScalarType object that has been configured with a
 * specific Coercing implementation. The Coercing implementation defines how that type is
 * serialized, deserialized and validated.
 */
public class Scalars {

  private Scalars() {}

  private static final Coercing<Address, String> ADDRESS_COERCING =
      new Coercing<Address, String>() {
        Address convertImpl(final Object input) {
          if (input instanceof Address address) {
            return address;
          } else if (input instanceof Bytes bytes) {
            if (((Bytes) input).size() <= 20) {
              return Address.wrap(bytes);
            } else {
              return null;
            }
          } else if (input instanceof StringValue stringValue) {
            return convertImpl(stringValue.getValue());
          } else if (input instanceof String string) {
            try {
              return Address.fromHexStringStrict(string);
            } catch (IllegalArgumentException iae) {
              return null;
            }
          } else {
            return null;
          }
        }

        @Override
        public String serialize(
            final Object input, final GraphQLContext graphQLContext, final Locale locale)
            throws CoercingSerializeException {
          Address result = convertImpl(input);
          if (result != null) {
            return result.toHexString();
          } else {
            throw new CoercingSerializeException("Unable to serialize " + input + " as an Address");
          }
        }

        @Override
        public Address parseValue(
            final Object input, final GraphQLContext graphQLContext, final Locale locale)
            throws CoercingParseValueException {
          Address result = convertImpl(input);
          if (result != null) {
            return result;
          } else {
            throw new CoercingParseValueException(
                "Unable to parse variable value " + input + " as an Address");
          }
        }

        @Override
        public Address parseLiteral(
            final Value<?> input,
            final CoercedVariables variables,
            final GraphQLContext graphQLContext,
            final Locale locale)
            throws CoercingParseLiteralException {
          Address result = convertImpl(input);
          if (result != null) {
            return result;
          } else {
            throw new CoercingParseLiteralException("Value is not any Address : '" + input + "'");
          }
        }
      };

  private static final Coercing<String, String> BIG_INT_COERCING =
      new Coercing<String, String>() {

        String convertImpl(final Object input) {
          if (input instanceof String string) {
            try {
              return Bytes.fromHexStringLenient(string).toShortHexString();
            } catch (IllegalArgumentException iae) {
              return null;
            }
          } else if (input instanceof Bytes bytes) {
            return bytes.toShortHexString();
          } else if (input instanceof StringValue stringValue) {
            return convertImpl(stringValue.getValue());
          } else if (input instanceof IntValue intValue) {
            return UInt256.valueOf(intValue.getValue()).toShortHexString();
          } else if (input instanceof BigInteger bigInteger) {
            return "0x" + bigInteger.toString(16);
          } else {
            return null;
          }
        }

        @Override
        public String serialize(
            final Object input, final GraphQLContext graphQLContext, final Locale locale)
            throws CoercingSerializeException {
          var result = convertImpl(input);
          if (result != null) {
            return result;
          } else {
            throw new CoercingSerializeException("Unable to serialize " + input + " as an BigInt");
          }
        }

        @Override
        public String parseValue(
            final Object input, final GraphQLContext graphQLContext, final Locale locale)
            throws CoercingParseValueException {
          var result = convertImpl(input);
          if (result != null) {
            return result;
          } else {
            throw new CoercingParseValueException(
                "Unable to parse variable value " + input + " as an BigInt");
          }
        }

        @Override
        public String parseLiteral(
            final Value<?> input,
            final CoercedVariables variables,
            final GraphQLContext graphQLContext,
            final Locale locale)
            throws CoercingParseLiteralException {
          var result = convertImpl(input);
          if (result != null) {
            return result;
          } else {
            throw new CoercingParseLiteralException("Value is not any BigInt : '" + input + "'");
          }
        }
      };

  private static final Coercing<Bytes, String> BYTES_COERCING =
      new Coercing<Bytes, String>() {

        Bytes convertImpl(final Object input) {
          if (input instanceof Bytes bytes) {
            return bytes;
          } else if (input instanceof StringValue stringValue) {
            return convertImpl(stringValue.getValue());
          } else if (input instanceof String string) {
            if (!Quantity.isValid(string)) {
              throw new CoercingParseLiteralException(
                  "Bytes value '" + input + "' is not prefixed with 0x");
            }
            try {
              return Bytes.fromHexStringLenient((String) input);
            } catch (IllegalArgumentException iae) {
              return null;
            }
          } else {
            return null;
          }
        }

        @Override
        public String serialize(
            final Object input, final GraphQLContext graphQLContext, final Locale locale)
            throws CoercingSerializeException {
          var result = convertImpl(input);
          if (result != null) {
            return result.toHexString();
          } else {
            throw new CoercingSerializeException("Unable to serialize " + input + " as an Bytes");
          }
        }

        @Override
        public Bytes parseValue(
            final Object input, final GraphQLContext graphQLContext, final Locale locale)
            throws CoercingParseValueException {
          var result = convertImpl(input);
          if (result != null) {
            return result;
          } else {
            throw new CoercingParseValueException(
                "Unable to parse variable value " + input + " as an Bytes");
          }
        }

        @Override
        public Bytes parseLiteral(
            final Value<?> input,
            final CoercedVariables variables,
            final GraphQLContext graphQLContext,
            final Locale locale)
            throws CoercingParseLiteralException {
          var result = convertImpl(input);
          if (result != null) {
            return result;
          } else {
            throw new CoercingParseLiteralException("Value is not any Bytes : '" + input + "'");
          }
        }
      };

  private static final Coercing<Bytes32, String> BYTES32_COERCING =
      new Coercing<Bytes32, String>() {

        Bytes32 convertImpl(final Object input) {
          if (input instanceof Bytes32 bytes32) {
            return bytes32;
          } else if (input instanceof Bytes bytes) {
            if (bytes.size() <= 32) {
              return Bytes32.leftPad((Bytes) input);
            } else {
              return null;
            }
          } else if (input instanceof StringValue stringValue) {
            return convertImpl(stringValue.getValue());
          } else if (input instanceof String string) {
            if (!Quantity.isValid(string)) {
              throw new CoercingParseLiteralException(
                  "Bytes32 value '" + input + "' is not prefixed with 0x");
            } else {
              try {
                return Bytes32.fromHexStringLenient((String) input);
              } catch (IllegalArgumentException iae) {
                return null;
              }
            }
          } else if (input instanceof VersionedHash versionedHash) {
            return versionedHash.toBytes();
          } else {
            return null;
          }
        }

        @Override
        public String serialize(
            final Object input, final GraphQLContext graphQLContext, final Locale locale)
            throws CoercingSerializeException {
          var result = convertImpl(input);
          if (result == null) {
            throw new CoercingSerializeException("Unable to serialize " + input + " as an Bytes32");
          } else {
            return result.toHexString();
          }
        }

        @Override
        public Bytes32 parseValue(
            final Object input, final GraphQLContext graphQLContext, final Locale locale)
            throws CoercingParseValueException {
          var result = convertImpl(input);
          if (result == null) {
            throw new CoercingParseValueException(
                "Unable to parse variable value " + input + " as an Bytes32");
          } else {
            return result;
          }
        }

        @Override
        public Bytes32 parseLiteral(
            final Value<?> input,
            final CoercedVariables variables,
            final GraphQLContext graphQLContext,
            final Locale locale)
            throws CoercingParseLiteralException {
          var result = convertImpl(input);
          if (result == null) {
            throw new CoercingParseLiteralException("Value is not any Bytes32 : '" + input + "'");
          } else {
            return result;
          }
        }
      };

  private static final Coercing<Number, String> LONG_COERCING =
      new Coercing<>() {
        @Override
        public String serialize(
            final Object input, final GraphQLContext graphQLContext, final Locale locale)
            throws CoercingSerializeException {
          if (input instanceof Number number) {
            return Bytes.ofUnsignedLong(number.longValue()).toQuantityHexString();
          } else if (input instanceof String string) {
            if (string.startsWith("0x")) {
              return string;
            } else {
              return "0x" + string;
            }
          }
          throw new CoercingSerializeException("Unable to serialize " + input + " as an Long");
        }

        @Override
        public Number parseValue(
            final Object input, final GraphQLContext graphQLContext, final Locale locale)
            throws CoercingParseValueException {
          if (input instanceof Number number) {
            return number;
          } else if (input instanceof String string) {
            final String value = string.toLowerCase(Locale.ROOT);
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
        public Number parseLiteral(
            final Value<?> input,
            final CoercedVariables variables,
            final GraphQLContext graphQLContext,
            final Locale locale)
            throws CoercingParseLiteralException {
          try {
            if (input instanceof IntValue intValue) {
              return intValue.getValue().longValue();
            } else if (input instanceof StringValue stringValue) {
              final String value = stringValue.getValue().toLowerCase(Locale.ROOT);
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

  /**
   * Creates a new GraphQLScalarType object for an Address.
   *
   * <p>The object is configured with a specific Coercing implementation that defines how the
   * Address type is serialized, deserialized and validated.
   *
   * @return a GraphQLScalarType object for an Address.
   */
  public static GraphQLScalarType addressScalar() {
    return GraphQLScalarType.newScalar()
        .name("Address")
        .description("Address scalar")
        .coercing(ADDRESS_COERCING)
        .build();
  }

  /**
   * Creates a new GraphQLScalarType object for a BigInt.
   *
   * <p>The object is configured with a specific Coercing implementation that defines how the BigInt
   * type is serialized, deserialized and validated.
   *
   * @return a GraphQLScalarType object for a BigInt.
   */
  public static GraphQLScalarType bigIntScalar() {
    return GraphQLScalarType.newScalar()
        .name("BigInt")
        .description("A BigInt (UInt256) scalar")
        .coercing(BIG_INT_COERCING)
        .build();
  }

  /**
   * Creates a new GraphQLScalarType object for Bytes.
   *
   * <p>The object is configured with a specific Coercing implementation that defines how the Bytes
   * type is serialized, deserialized and validated.
   *
   * @return a GraphQLScalarType object for Bytes.
   */
  public static GraphQLScalarType bytesScalar() {
    return GraphQLScalarType.newScalar()
        .name("Bytes")
        .description("A Bytes scalar")
        .coercing(BYTES_COERCING)
        .build();
  }

  /**
   * Creates a new GraphQLScalarType object for Bytes32.
   *
   * <p>The object is configured with a specific Coercing implementation that defines how the
   * Bytes32 type is serialized, deserialized and validated.
   *
   * @return a GraphQLScalarType object for Bytes32.
   */
  public static GraphQLScalarType bytes32Scalar() {
    return GraphQLScalarType.newScalar()
        .name("Bytes32")
        .description("A Bytes32 scalar")
        .coercing(BYTES32_COERCING)
        .build();
  }

  /**
   * Creates a new GraphQLScalarType object for a Long.
   *
   * <p>The object is configured with a specific Coercing implementation that defines how the Long
   * type is serialized, deserialized and validated.
   *
   * @return a GraphQLScalarType object for a Long.
   */
  public static GraphQLScalarType longScalar() {
    return GraphQLScalarType.newScalar()
        .name("Long")
        .description("A Long (UInt64) scalar")
        .coercing(LONG_COERCING)
        .build();
  }
}
