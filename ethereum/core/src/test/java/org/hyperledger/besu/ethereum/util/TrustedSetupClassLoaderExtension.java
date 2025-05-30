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
package org.hyperledger.besu.ethereum.util;

import static org.junit.jupiter.api.Assertions.fail;

import org.hyperledger.besu.evm.precompile.KZGPointEvalPrecompiledContract;

import ethereum.ckzg4844.CKZG4844JNI;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/** JUnit extension to load the native library and trusted setup for the entire test class. */
public class TrustedSetupClassLoaderExtension implements BeforeAllCallback {
  private static final String TRUSTED_SETUP_RESOURCE = "/kzg-trusted-setups/mainnet.txt";

  @Override
  public void beforeAll(final ExtensionContext context) {
    try {
      // Optimistically tear down any previously loaded trusted setup.
      try {
        KZGPointEvalPrecompiledContract.tearDown();
      } catch (Throwable ignore) {
        // Ignore errors if no trusted setup was already loaded.
      }
      CKZG4844JNI.loadNativeLibrary();
      CKZG4844JNI.loadTrustedSetupFromResource(
          TRUSTED_SETUP_RESOURCE, context.getTestClass().orElseThrow(), 0);
    } catch (Exception e) {
      fail("Failed to load trusted setup or native library", e);
    }
  }
}
