/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.enclave.testutil;

import java.security.SecureRandom;

/**
 * The Secure random provider. Errorprone checks are in place to enforce only this class is used
 * wherever SecureRandom instance is required.
 */
public class SecureRandomProvider {

  /**
   * Create secure random.
   *
   * @return the secure random
   */
  public static SecureRandom createSecureRandom() {
    return secureRandom();
  }

  @SuppressWarnings("DoNotCreateSecureRandomDirectly")
  private static SecureRandom secureRandom() {
    return new SecureRandom();
  }
}
