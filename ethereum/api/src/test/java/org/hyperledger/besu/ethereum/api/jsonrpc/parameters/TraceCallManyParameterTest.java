/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.api.jsonrpc.parameters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.TraceCallManyParameter;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters.TraceTypeParameter;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.junit.jupiter.api.Test;

public class TraceCallManyParameterTest {
  static final String emptyParamsJson = "[]";
  static final String invalidJson = "[[\"invalid\"],[\"invalid\"]]";
  static final String requestParamsJson =
      """
          [
            [
              {
                "from" : "0xfe3b557e8fb62b89f4916b721be55ceb828dbd73",
                "value" : "0x0",
                "to" : "0x0010000000000000000000000000000000000000",
                "gas" : "0xfffff2",
                "gasPrice" : "0xef",
                "data" : "0x0000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000002"
              },
              [ "trace" ]
            ],
            [
              {
                "from" : "0x627306090abab3a6e1400e9345bc60c78a8bef57",
                "value" : "0x0",
                "to" : "0x0010000000000000000000000000000000000000",
                "gas" : "0xfffff2",
                "gasPrice" : "0xef",
                "data" : "0x0000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000300000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000004"
              },
              [ "trace" ]
            ],
            [
              {
                "from" : "0x627306090abab3a6e1400e9345bc60c78a8bef57",
                "value" : "0x0",
                "to" : "0x0010000000000000000000000000000000000000",
                "gas" : "0xfffff2",
                "gasPrice" : "0xef",
                "data" : "0x0000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000300000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000000"
              },
              [ "trace" ]
            ]
          ]""";

  private final ObjectMapper mapper = new ObjectMapper().registerModule(new Jdk8Module());

  @Test
  public void testEmptyParamJsonParsesCorrectly() throws IOException {
    final TraceCallManyParameter[] parameter =
        mapper.readValue(emptyParamsJson, TraceCallManyParameter[].class);

    assertThat(parameter).isNullOrEmpty();
  }

  @Test
  public void testRequestParameterJsonParsesCorrectly() throws IOException {
    final TraceCallManyParameter[] parameter =
        mapper.readValue(requestParamsJson, TraceCallManyParameter[].class);

    assertThat(parameter[0].getTuple()).isNotNull();
    assertThat(parameter[0].getTuple().getCallParameter()).isNotNull();
    assertThat(parameter[0].getTuple().getCallParameter().getSender())
        .contains(Address.fromHexString("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73"));
    assertThat(parameter[2].getTuple().getCallParameter().getTo())
        .contains(Address.fromHexString("0x0010000000000000000000000000000000000000"));
    assertThat(parameter[1].getTuple().getTraceTypeParameter().getTraceTypes()).hasSize(1);
    assertThat(parameter[1].getTuple().getTraceTypeParameter().getTraceTypes())
        .contains(TraceTypeParameter.TraceType.TRACE);
  }

  @Test
  public void testInvalidJsonDoesNotParse() {
    assertThatExceptionOfType(MismatchedInputException.class)
        .isThrownBy(() -> mapper.readValue(invalidJson, TraceCallManyParameter[].class));
  }
}
