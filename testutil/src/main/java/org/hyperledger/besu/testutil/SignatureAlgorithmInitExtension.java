/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.testutil;

import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * JUnit 5 extension that ensures SignatureAlgorithmFactory is initialized early in the test
 * lifecycle. This improves test performance by avoiding repeated initialization when
 * ProtocolScheduleBuilder creates schedules for multiple forks.
 *
 * <p>This extension is automatically registered via junit-platform.properties when auto-detection
 * is enabled.
 */
public class SignatureAlgorithmInitExtension implements BeforeAllCallback {
  private static volatile boolean initialized = false;

  /** Instantiates a new Signature algorithm init extension. */
  public SignatureAlgorithmInitExtension() {}

  @Override
  public void beforeAll(final ExtensionContext context) {
    if (!initialized) {
      synchronized (SignatureAlgorithmInitExtension.class) {
        if (!initialized) {
          SignatureAlgorithmFactory.setDefaultInstance();
          initialized = true;
        }
      }
    }
  }
}
