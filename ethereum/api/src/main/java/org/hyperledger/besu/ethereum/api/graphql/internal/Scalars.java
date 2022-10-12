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

public class Scalars {

  private Scalars() {}

  private static final Coercing<Address, String> ADDRESS_COERCING =
      new Coercing<Address, String>() {
        Address convertImpl(final Object input) {
          if (input instanceof Address) {
            return (Address) input;
          } else if (input instanceof Bytes) {
            if (((Bytes) input).size() <= 20) {
              return Address.wrap((Bytes) input);
            } else {
              return null;
            }
          } else if (input instanceof StringValue) {
            return convertImpl(((StringValue) input).getValue());
          } else if (input instanceof String) {
            try {
              return Address.fromHexStringStrict((String) input);
            } catch (IllegalArgumentException iae) {
              return null;
            }
          } else {
            return null;
          }
        }

        @Override
        public String serialize(final Object input) throws CoercingSerializeException {
          Address result = convertImpl(input);
          if (result != null) {
            return result.toHexString();
          } else {
            throw new CoercingSerializeException("Unable to serialize " + input + " as an Address");
          }
        }

        @Override
        public Address parseValue(final Object input) throws CoercingParseValueException {
          Address result = convertImpl(input);
          if (result != null) {
            return result;
          } else {
            throw new CoercingParseValueException(
                "Unable to parse variable value " + input + " as an Address");
          }
        }

        @Override
        public Address parseLiteral(final Object input) throws CoercingParseLiteralException {
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
          if (input instanceof String) {
            try {
              return Bytes.fromHexStringLenient((String) input).toShortHexString();
            } catch (IllegalArgumentException iae) {
              return null;
            }
          } else if (input instanceof Bytes) {
            return ((Bytes) input).toShortHexString();
          } else if (input instanceof StringValue) {
            return convertImpl(((StringValue) input).getValue());
          } else if (input instanceof IntValue) {
            return UInt256.valueOf(((IntValue) input).getValue()).toShortHexString();
          } else {
            return null;
          }
        }

        @Override
        public String serialize(final Object input) throws CoercingSerializeException {
          var result = convertImpl(input);
          if (result != null) {
            return result;
          } else {
            throw new CoercingSerializeException("Unable to serialize " + input + " as an BigInt");
          }
        }

        @Override
        public String parseValue(final Object input) throws CoercingParseValueException {
          var result = convertImpl(input);
          if (result != null) {
            return result;
          } else {
            throw new CoercingParseValueException(
                "Unable to parse variable value " + input + " as an BigInt");
          }
        }

        @Override
        public String parseLiteral(final Object input) throws CoercingParseLiteralException {
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
          if (input instanceof Bytes) {
            return (Bytes) input;
          } else if (input instanceof StringValue) {
            return convertImpl(((StringValue) input).getValue());
          } else if (input instanceof String) {
            if (!Quantity.isValid((String) input)) {
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
        public String serialize(final Object input) throws CoercingSerializeException {
          var result = convertImpl(input);
          if (result != null) {
            return result.toHexString();
          } else {
            throw new CoercingSerializeException("Unable to serialize " + input + " as an Bytes");
          }
        }

        @Override
        public Bytes parseValue(final Object input) throws CoercingParseValueException {
          var result = convertImpl(input);
          if (result != null) {
            return result;
          } else {
            throw new CoercingParseValueException(
                "Unable to parse variable value " + input + " as an Bytes");
          }
        }

        @Override
        public Bytes parseLiteral(final Object input) throws CoercingParseLiteralException {
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
          if (input instanceof Bytes32) {
            return (Bytes32) input;
          } else if (input instanceof Bytes) {
            if (((Bytes) input).size() <= 32) {
              return Bytes32.leftPad((Bytes) input);
            } else {
              return null;
            }
          } else if (input instanceof StringValue) {
            return convertImpl((((StringValue) input).getValue()));
          } else if (input instanceof String) {
            if (!Quantity.isValid((String) input)) {
              throw new CoercingParseLiteralException(
                  "Bytes32 value '" + input + "' is not prefixed with 0x");
            } else {
              try {
                return Bytes32.fromHexStringLenient((String) input);
              } catch (IllegalArgumentException iae) {
                return null;
              }
            }
          } else {
            return null;
          }
        }

        @Override
        public String serialize(final Object input) throws CoercingSerializeException {
          var result = convertImpl(input);
          if (result == null) {
            throw new CoercingSerializeException("Unable to serialize " + input + " as an Bytes32");
          } else {
            return result.toHexString();
          }
        }

        @Override
        public Bytes32 parseValue(final Object input) throws CoercingParseValueException {
          var result = convertImpl(input);
          if (result == null) {
            throw new CoercingParseValueException(
                "Unable to parse variable value " + input + " as an Bytes32");
          } else {
            return result;
          }
        }

        @Override
        public Bytes32 parseLiteral(final Object input) throws CoercingParseLiteralException {
          var result = convertImpl(input);
          if (result == null) {
            throw new CoercingParseLiteralException("Value is not any Bytes32 : '" + input + "'");
          } else {
            return result;
          }
        }
      };

  private static final Coercing<Number, Number> LONG_COERCING =
      new Coercing<Number, Number>() {
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
        public Number parseLiteral(final Object input) throws CoercingParseLiteralException {
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
