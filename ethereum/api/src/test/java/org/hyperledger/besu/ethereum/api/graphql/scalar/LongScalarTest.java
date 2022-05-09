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
package org.hyperledger.besu.ethereum.api.graphql.scalar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hyperledger.besu.ethereum.api.graphql.internal.Scalars;

import graphql.language.StringValue;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class LongScalarTest {

  private GraphQLScalarType scalar;

  private final String str = "0xf4240";
  private final Long value = Long.decode(str);
  private final StringValue strValue = StringValue.newStringValue(str).build();
  private final StringValue invalidStrValue = StringValue.newStringValue("gh").build();

  @Test
  public void parseLongValueTest() {
    assertThat(scalar.getCoercing().parseValue(value)).isEqualTo(value);
  }

  @Test
  public void parseStringValueTest() {
    assertThat(scalar.getCoercing().parseValue(str)).isEqualTo(value);
  }

  @Test
  public void parseValueErrorTest() {
    assertThatThrownBy(() -> scalar.getCoercing().parseValue(invalidStrValue))
        .isInstanceOf(CoercingParseValueException.class);
  }

  @Test
  public void serializeLongTest() {
    assertThat(scalar.getCoercing().serialize(value)).isEqualTo(value);
  }

  @Test
  public void serializeStringTest() {
    assertThat(scalar.getCoercing().serialize(str)).isEqualTo(value);
  }

  @Test
  public void serializeErrorTest() {
    assertThatThrownBy(() -> scalar.getCoercing().serialize(invalidStrValue))
        .isInstanceOf(CoercingSerializeException.class);
  }

  @Test
  public void parseLiteralTest() {
    final Long result = (Long) scalar.getCoercing().parseLiteral(strValue);
    assertThat(result).isEqualTo(value);
  }

  @Test
  public void parseLiteralErrorTest() {
    assertThatThrownBy(() -> scalar.getCoercing().parseLiteral(str))
        .isInstanceOf(CoercingParseLiteralException.class);
  }

  @Test
  public void parseLiteralErrorTest2() {
    assertThatThrownBy(() -> scalar.getCoercing().parseLiteral(invalidStrValue))
        .isInstanceOf(CoercingParseLiteralException.class);
  }

  @Before
  public void before() {
    scalar = Scalars.longScalar();
  }
}
