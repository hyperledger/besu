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

import org.hyperledger.besu.ethereum.api.graphql.internal.Scalars;
import org.hyperledger.besu.util.bytes.Bytes32;

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
public class Bytes32ScalarTest {

  private GraphQLScalarType scalar;
  @Rule public ExpectedException thrown = ExpectedException.none();

  private final String str = "0x1234567812345678123456781234567812345678123456781234567812345678";
  private final Bytes32 value = Bytes32.fromHexString(str);
  private final StringValue strValue = StringValue.newStringValue(str).build();
  private final StringValue invalidStrValue = StringValue.newStringValue("0xgh").build();

  @Test
  public void pareValueTest() {
    final String result = (String) scalar.getCoercing().parseValue(value);
    assertThat(result).isEqualTo(str);
  }

  @Test
  public void pareValueErrorTest() {
    thrown.expect(CoercingParseValueException.class);
    scalar.getCoercing().parseValue(str);
  }

  @Test
  public void serializeTest() {
    final String result = (String) scalar.getCoercing().serialize(value);
    assertThat(result).isEqualTo(str);
  }

  @Test
  public void serializeErrorTest() {
    thrown.expect(CoercingSerializeException.class);
    scalar.getCoercing().serialize(str);
  }

  @Test
  public void pareLiteralTest() {
    final Bytes32 result = (Bytes32) scalar.getCoercing().parseLiteral(strValue);
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
    scalar = Scalars.bytes32Scalar();
  }
}
