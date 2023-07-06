/*
 * Copyright Hyperledger Besu.
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
import org.junit.Before;
import org.junit.Test;

public class TraceCallManyParameterTest {
  static final String emptyParamsJson = "[]";
  static final String invalidJson = "[[\"invalid\"],[\"invalid\"]]";
  static final String requestParamsJson =
      "[ [ {\n"
          + "      \"from\" : \"0xfe3b557e8fb62b89f4916b721be55ceb828dbd73\",\n"
          + "      \"value\" : \"0x0\",\n"
          + "      \"to\" : \"0x0010000000000000000000000000000000000000\",\n"
          + "      \"gas\" : \"0xfffff2\",\n"
          + "      \"gasPrice\" : \"0xef\",\n"
          + "      \"data\" : \"0x0000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000002\"\n"
          + "    }, [ \"trace\" ] ], [ {\n"
          + "      \"from\" : \"0x627306090abab3a6e1400e9345bc60c78a8bef57\",\n"
          + "      \"value\" : \"0x0\",\n"
          + "      \"to\" : \"0x0010000000000000000000000000000000000000\",\n"
          + "      \"gas\" : \"0xfffff2\",\n"
          + "      \"gasPrice\" : \"0xef\",\n"
          + "      \"data\" : \"0x0000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000300000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000004\"\n"
          + "    }, [ \"trace\" ] ], [ {\n"
          + "      \"from\" : \"0x627306090abab3a6e1400e9345bc60c78a8bef57\",\n"
          + "      \"value\" : \"0x0\",\n"
          + "      \"to\" : \"0x0010000000000000000000000000000000000000\",\n"
          + "      \"gas\" : \"0xfffff2\",\n"
          + "      \"gasPrice\" : \"0xef\",\n"
          + "      \"data\" : \"0x0000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000300000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000000\"\n"
          + "    }, [ \"trace\" ] ] ]";

  private ObjectMapper mapper;

  @Before
  public void setup() {
    mapper = new ObjectMapper();
  }

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
    assertThat(parameter[0].getTuple().getJsonCallParameter()).isNotNull();
    assertThat(parameter[0].getTuple().getJsonCallParameter().getFrom())
        .isEqualTo(Address.fromHexString("0xfe3b557e8fb62b89f4916b721be55ceb828dbd73"));
    assertThat(parameter[2].getTuple().getJsonCallParameter().getTo())
        .isEqualTo(Address.fromHexString("0x0010000000000000000000000000000000000000"));
    assertThat(parameter[1].getTuple().getTraceTypeParameter().getTraceTypes()).hasSize(1);
    assertThat(parameter[1].getTuple().getTraceTypeParameter().getTraceTypes())
        .contains(TraceTypeParameter.TraceType.TRACE);
  }

  @Test
  public void testInvalidJsonDoesNotParse() throws IOException {
    assertThatExceptionOfType(MismatchedInputException.class)
        .isThrownBy(() -> mapper.readValue(invalidJson, TraceCallManyParameter[].class));
  }
}
