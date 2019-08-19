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
package tech.pegasys.pantheon.ethereum.graphql.internal.scalar;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.ethereum.graphql.internal.Scalars;

import graphql.language.StringValue;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class LongScalarTest {

  private GraphQLScalarType scalar;
  @Rule public ExpectedException thrown = ExpectedException.none();

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
  public void pareValueErrorTest() {
    thrown.expect(CoercingParseValueException.class);
    scalar.getCoercing().parseValue(invalidStrValue);
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
    thrown.expect(CoercingSerializeException.class);
    scalar.getCoercing().serialize(invalidStrValue);
  }

  @Test
  public void pareLiteralTest() {
    final Long result = (Long) scalar.getCoercing().parseLiteral(strValue);
    assertThat(result).isEqualTo(value);
  }

  @Test
  public void pareLiteralErrorTest() {
    thrown.expect(CoercingParseLiteralException.class);
    scalar.getCoercing().parseLiteral(str);
  }

  @Test
  public void pareLiteralErrorTest2() {
    thrown.expect(CoercingParseLiteralException.class);
    scalar.getCoercing().parseLiteral(invalidStrValue);
  }

  @Before
  public void before() {
    scalar = Scalars.longScalar();
  }
}
