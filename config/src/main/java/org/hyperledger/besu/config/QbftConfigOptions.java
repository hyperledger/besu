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
package org.hyperledger.besu.config;

import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;

public class QbftConfigOptions extends BftConfigOptions {
  public static final QbftConfigOptions DEFAULT =
      new QbftConfigOptions(JsonUtil.createEmptyObjectNode());

  QbftConfigOptions(final ObjectNode bftConfigRoot) {
    super(bftConfigRoot);
  }

  public Optional<String> getValidatorContractAddress() {
    return JsonUtil.getString(bftConfigRoot, "validatorcontractaddress");
  }

  @Override
  public Map<String, Object> asMap() {
    final Map<String, Object> map = super.asMap();
    final ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
    builder.putAll(map);
    builder.put("validatorcontractaddress", getValidatorContractAddress());
    return builder.build();
  }
}
