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
package tech.pegasys.pantheon.consensus.clique;

import static org.assertj.core.api.Java6Assertions.assertThat;

import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSpec;

import io.vertx.core.json.JsonObject;
import org.junit.Test;

public class CliqueProtocolScheduleTest {

  @Test
  public void protocolSpecsAreCreatedAtBlockDefinedInJson() {
    final String jsonInput =
        "{\"chainId\": 4,\n"
            + "\"homesteadBlock\": 1,\n"
            + "\"eip150Block\": 2,\n"
            + "\"eip155Block\": 3,\n"
            + "\"eip158Block\": 3,\n"
            + "\"byzantiumBlock\": 1035301}";

    final JsonObject jsonObject = new JsonObject(jsonInput);

    final ProtocolSchedule<CliqueContext> protocolSchedule =
        CliqueProtocolSchedule.create(jsonObject, KeyPair.generate());

    final ProtocolSpec<CliqueContext> homesteadSpec = protocolSchedule.getByBlockNumber(1);
    final ProtocolSpec<CliqueContext> tangerineWhistleSpec = protocolSchedule.getByBlockNumber(2);
    final ProtocolSpec<CliqueContext> spuriousDragonSpec = protocolSchedule.getByBlockNumber(3);
    final ProtocolSpec<CliqueContext> byzantiumSpec = protocolSchedule.getByBlockNumber(1035301);

    assertThat(homesteadSpec.equals(tangerineWhistleSpec)).isFalse();
    assertThat(tangerineWhistleSpec.equals(spuriousDragonSpec)).isFalse();
    assertThat(spuriousDragonSpec.equals(byzantiumSpec)).isFalse();
  }
}
