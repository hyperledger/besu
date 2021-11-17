/*
 * Copyright Hyperledger Besu Contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 *  the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.besu.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.config.JsonQbftConfigOptions.VALIDATOR_CONTRACT_ADDRESS;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Test;

public class JsonQbftConfigOptionsTest {

  final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  public void getValidatorContractAddressNormalization() {
    final ObjectNode objectNode =
        objectMapper.createObjectNode().put(VALIDATOR_CONTRACT_ADDRESS, "0xABC");

    final JsonQbftConfigOptions configOptions = new JsonQbftConfigOptions(objectNode);

    assertThat(configOptions.getValidatorContractAddress()).hasValue("0xabc");
  }

  @Test
  public void asMapDoesNotIncludeEmptyOptionalFields() {
    final ObjectNode objectNode = objectMapper.createObjectNode();
    final JsonQbftConfigOptions configOptions = new JsonQbftConfigOptions(objectNode);

    assertThat(configOptions.asMap()).isEmpty();
  }
}
