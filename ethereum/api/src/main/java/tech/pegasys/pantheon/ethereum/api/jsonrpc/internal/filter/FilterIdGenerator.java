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
package tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.filter;

import tech.pegasys.pantheon.crypto.SecureRandomProvider;
import tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.results.Quantity;

import java.security.SecureRandom;

public class FilterIdGenerator {

  private final SecureRandom secureRandom = SecureRandomProvider.createSecureRandom();

  public String nextId() {
    final byte[] randomBytes = new byte[16];
    secureRandom.nextBytes(randomBytes);
    return Quantity.create(randomBytes);
  }
}
