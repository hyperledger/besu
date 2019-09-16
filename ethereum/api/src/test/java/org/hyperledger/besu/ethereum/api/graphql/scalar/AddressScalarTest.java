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
package org.hyperledger.besu.ethereum.api.graphql.scalar;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.ethereum.api.graphql.internal.Scalars;
import org.hyperledger.besu.ethereum.core.Address;

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
public class AddressScalarTest {

  private GraphQLScalarType scalar;
  @Rule public ExpectedException thrown = ExpectedException.none();

  private final String addrStr = "0x6295ee1b4f6dd65047762f924ecd367c17eabf8f";
  private final String invalidAddrStr = "0x295ee1b4f6dd65047762f924ecd367c17eabf8f";
  private final Address addr = Address.fromHexString(addrStr);
  private final StringValue addrValue = StringValue.newStringValue(addrStr).build();
  private final StringValue invalidAddrValue = StringValue.newStringValue(invalidAddrStr).build();

  @Test
  public void pareValueTest() {
    final String result = (String) scalar.getCoercing().parseValue(addr);
    assertThat(result).isEqualTo(addrStr);
  }

  @Test
  public void pareValueErrorTest() {

    thrown.expect(CoercingParseValueException.class);
    scalar.getCoercing().parseValue(addrStr);
  }

  @Test
  public void serializeTest() {

    final String result = (String) scalar.getCoercing().serialize(addr);
    assertThat(result).isEqualTo(addrStr);
  }

  @Test
  public void serializeErrorTest() {

    thrown.expect(CoercingSerializeException.class);
    scalar.getCoercing().serialize(addrStr);
  }

  @Test
  public void pareLiteralTest() {
    final Address result = (Address) scalar.getCoercing().parseLiteral(addrValue);
    assertThat(result).isEqualTo(addr);
  }

  @Test
  public void pareLiteralErrorTest() {
    thrown.expect(CoercingParseLiteralException.class);
    scalar.getCoercing().parseLiteral(addrStr);
  }

  @Test
  public void pareLiteralErrorTest2() {
    thrown.expect(CoercingParseLiteralException.class);
    scalar.getCoercing().parseLiteral(invalidAddrValue);
  }

  @Before
  public void before() {
    scalar = Scalars.addressScalar();
  }
}
