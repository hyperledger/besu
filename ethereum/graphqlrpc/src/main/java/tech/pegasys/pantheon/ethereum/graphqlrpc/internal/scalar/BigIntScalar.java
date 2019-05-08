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

import tech.pegasys.pantheon.util.uint.UInt256;

import graphql.Internal;
import graphql.language.IntValue;
import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;

@Internal
public class BigIntScalar extends GraphQLScalarType {

  public BigIntScalar() {
    super(
        "BigInt",
        "A BigInt scalar",
        new Coercing<Object, Object>() {
          @Override
          public String serialize(final Object input) throws CoercingSerializeException {
            if (input instanceof UInt256) {
              return ((UInt256) input).toShortHexString();
            }
            throw new CoercingSerializeException("Unable to serialize " + input + " as an BigInt");
          }

          @Override
          public String parseValue(final Object input) throws CoercingParseValueException {
            if (input instanceof UInt256) {
              return ((UInt256) input).toShortHexString();
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
                return UInt256.of(((IntValue) input).getValue());
              }
            } catch (final IllegalArgumentException e) {
              // fall through
            }
            throw new CoercingParseLiteralException("Value is not any BigInt : '" + input + "'");
          }
        });
  }
}
