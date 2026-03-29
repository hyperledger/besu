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
package org.hyperledger.besu.ethereum.api.graphql.scalar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hyperledger.besu.datatypes.LogTopic;
import org.hyperledger.besu.ethereum.api.graphql.internal.Scalars;

import java.util.Locale;

import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.FloatValue;
import graphql.language.StringValue;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class LogTopicScalarTest {
  private GraphQLScalarType scalar;

  private final LogTopic logTopic =
      LogTopic.wrap(
          Bytes32.fromHexString(
              "0x1234567812345678123456781234567812345678123456781234567812345678"));
  private final StringValue strValue =
      StringValue.newStringValue(logTopic.getBytes().toHexString()).build();
  private final StringValue invalidStrValue = StringValue.newStringValue("0xgh").build();

  @Test
  public void parseValueTest() {
    final var result =
        scalar
            .getCoercing()
            .parseValue(strValue.getValue(), GraphQLContext.newContext().build(), Locale.ENGLISH);
    assertThat(result).isEqualTo(logTopic);
  }

  @Test
  public void parseValueTestInvalidString() {
    assertThatThrownBy(
            () ->
                scalar
                    .getCoercing()
                    .parseValue(
                        "not_hexadecimal", GraphQLContext.newContext().build(), Locale.ENGLISH))
        .isInstanceOf(CoercingParseLiteralException.class);
  }

  @Test
  public void parseValueErrorTest() {
    assertThatThrownBy(
            () ->
                scalar
                    .getCoercing()
                    .parseValue(3.2f, GraphQLContext.newContext().build(), Locale.ENGLISH))
        .isInstanceOf(CoercingParseValueException.class);
  }

  @Test
  public void serializeTest() {
    final String result =
        (String)
            scalar
                .getCoercing()
                .serialize(logTopic, GraphQLContext.newContext().build(), Locale.ENGLISH);
    assertThat(result).isEqualTo(strValue.getValue());
  }

  @Test
  public void serializeErrorTest() {
    assertThatThrownBy(
            () ->
                scalar
                    .getCoercing()
                    .serialize(3.2f, GraphQLContext.newContext().build(), Locale.ENGLISH))
        .isInstanceOf(CoercingSerializeException.class);
  }

  @Test
  public void parseLiteralTest() {
    final LogTopic result =
        (LogTopic)
            scalar
                .getCoercing()
                .parseLiteral(
                    strValue,
                    CoercedVariables.emptyVariables(),
                    GraphQLContext.newContext().build(),
                    Locale.ENGLISH);
    assertThat(result).isEqualTo(logTopic);
  }

  @Test
  public void parseLiteralErrorTest() {
    assertThatThrownBy(
            () ->
                scalar
                    .getCoercing()
                    .parseLiteral(
                        FloatValue.of(3.2f),
                        CoercedVariables.emptyVariables(),
                        GraphQLContext.newContext().build(),
                        Locale.ENGLISH))
        .isInstanceOf(CoercingParseLiteralException.class);
  }

  @Test
  public void parseLiteralErrorTest2() {
    assertThatThrownBy(
            () ->
                scalar
                    .getCoercing()
                    .parseLiteral(
                        invalidStrValue,
                        CoercedVariables.emptyVariables(),
                        GraphQLContext.newContext().build(),
                        Locale.ENGLISH))
        .isInstanceOf(CoercingParseLiteralException.class);
  }

  @BeforeEach
  public void before() {
    scalar = Scalars.logTopicScalar();
  }
}
