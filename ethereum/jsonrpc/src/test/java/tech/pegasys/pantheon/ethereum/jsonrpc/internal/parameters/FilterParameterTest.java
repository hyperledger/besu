/*
 * Copyright 2018 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;

import java.util.Arrays;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

public class FilterParameterTest {

  private final JsonRpcParameter parameters = new JsonRpcParameter();

  @Test
  public void jsonWithArrayOfAddressesShouldSerializeSuccessfully() throws Exception {
    final String jsonWithAddressArray =
        "{\"jsonrpc\":\"2.0\",\"method\":\"eth_getLogs\",\"params\":[{\"address\":[\"0x0\",\"0x1\"]}],\"id\":1}";
    final JsonRpcRequest request = readJsonAsJsonRpcRequest(jsonWithAddressArray);
    final FilterParameter expectedFilterParameter = filterParameterWithAddresses("0x0", "0x1");

    final FilterParameter parsedFilterParameter =
        parameters.required(request.getParams(), 0, FilterParameter.class);

    assertThat(parsedFilterParameter)
        .isEqualToComparingFieldByFieldRecursively(expectedFilterParameter);
  }

  @Test
  public void jsonWithSingleAddressShouldSerializeSuccessfully() throws Exception {
    final String jsonWithSingleAddress =
        "{\"jsonrpc\":\"2.0\",\"method\":\"eth_getLogs\",\"params\":[{\"address\":\"0x0\"}],\"id\":1}";
    final JsonRpcRequest request = readJsonAsJsonRpcRequest(jsonWithSingleAddress);
    final FilterParameter expectedFilterParameter = filterParameterWithAddresses("0x0");

    final FilterParameter parsedFilterParameter =
        parameters.required(request.getParams(), 0, FilterParameter.class);

    assertThat(parsedFilterParameter)
        .isEqualToComparingFieldByFieldRecursively(expectedFilterParameter);
  }

  private FilterParameter filterParameterWithAddresses(final String... addresses) {
    return new FilterParameter("latest", "latest", Arrays.asList(addresses), null, null);
  }

  private JsonRpcRequest readJsonAsJsonRpcRequest(final String jsonWithSingleAddress)
      throws java.io.IOException {
    return new ObjectMapper().readValue(jsonWithSingleAddress, JsonRpcRequest.class);
  }
}
