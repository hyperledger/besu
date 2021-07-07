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
package org.hyperledger.besu.config.experimental;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.config.experimental.PrivacyGenesisConfigFile.fromConfig;

import org.hyperledger.besu.config.GenesisAllocation;

import java.util.Map;

import org.junit.Test;

public class PrivacyGenesisConfigFileTest {

  @Test
  public void shouldGetAllocations() {
    final PrivacyGenesisConfigFile config =
        fromConfig(
            "{"
                + "  \"alloc\": {"
                + "    \"fe3b557e8fb62b89f4916b721be55ceb828dbd73\": {"
                + "      \"balance\": \"0xad78ebc5ac6200000\""
                + "    },"
                + "    \"627306090abaB3A6e1400e9345bC60c78a8BEf57\": {"
                + "      \"balance\": \"1000\""
                + "    },"
                + "    \"f17f52151EbEF6C7334FAD080c5704D77216b732\": {"
                + "      \"balance\": \"90000000000000000000000\","
                + "        \"storage\": {"
                + "          \"0xc4c3a3f99b26e5e534b71d6f33ca6ea5c174decfb16dd7237c60eff9774ef4a4\": \"0x937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0\",\n"
                + "          \"0xc4c3a3f99b26e5e534b71d6f33ca6ea5c174decfb16dd7237c60eff9774ef4a3\": \"0x6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012\""
                + "        }"
                + "    }"
                + "  }"
                + "}");

    final Map<String, GenesisAllocation> allocations = config.getAllocations();
    assertThat(allocations.keySet())
        .containsOnly(
            "fe3b557e8fb62b89f4916b721be55ceb828dbd73",
            "627306090abab3a6e1400e9345bc60c78a8bef57",
            "f17f52151ebef6c7334fad080c5704d77216b732");
    final GenesisAllocation alloc1 = allocations.get("fe3b557e8fb62b89f4916b721be55ceb828dbd73");
    final GenesisAllocation alloc2 = allocations.get("627306090abab3a6e1400e9345bc60c78a8bef57");
    final GenesisAllocation alloc3 = allocations.get("f17f52151ebef6c7334fad080c5704d77216b732");

    assertThat(alloc1.getBalance()).isEqualTo("0xad78ebc5ac6200000");
    assertThat(alloc2.getBalance()).isEqualTo("1000");
    assertThat(alloc3.getBalance()).isEqualTo("90000000000000000000000");
    assertThat(alloc3.getStorage().size()).isEqualTo(2);
    assertThat(
            alloc3
                .getStorage()
                .get("0xc4c3a3f99b26e5e534b71d6f33ca6ea5c174decfb16dd7237c60eff9774ef4a4"))
        .isEqualTo("0x937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0");
    assertThat(
            alloc3
                .getStorage()
                .get("0xc4c3a3f99b26e5e534b71d6f33ca6ea5c174decfb16dd7237c60eff9774ef4a3"))
        .isEqualTo("0x6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012");
  }
}
