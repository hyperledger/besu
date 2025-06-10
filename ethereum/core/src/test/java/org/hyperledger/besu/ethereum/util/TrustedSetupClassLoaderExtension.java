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
import ethereum.ckzg4844.CKZGException;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TrustedSetupClassLoaderExtension implements BeforeAllCallback {
  private static final Logger LOG = LoggerFactory.getLogger(TrustedSetupClassLoaderExtension.class);
  private static final String TRUSTED_SETUP_RESOURCE = "/kzg-trusted-setups/mainnet.txt";

  @Override
  public void beforeAll(final ExtensionContext context) {
    try {
      tearDownExistingSetup();
      loadTrustedSetup(context);
      awaitTrustedSetupLoad();
    } catch (Exception e) {
      fail("Failed to load trusted setup or native library", e);
    }
  }

  // Tear down any existing setup to ensure a clean state before loading the trusted setup.
  private void tearDownExistingSetup() {
    try {
      KZGPointEvalPrecompiledContract.tearDown();
    } catch (Throwable ignore) {
      // Ignore errors if no trusted setup was already loaded.
    }
  }

  // Load the trusted setup from the specified resource.
  private void loadTrustedSetup(final ExtensionContext context) {
    CKZG4844JNI.loadNativeLibrary();
    CKZG4844JNI.loadTrustedSetupFromResource(
        TRUSTED_SETUP_RESOURCE, context.getTestClass().orElseThrow(), 0);
  }

  // Wait for the trusted setup to be loaded by checking if we can create a KZG commitment.
  private void awaitTrustedSetupLoad() {
    boolean trustedSetupLoaded = false;
    long timeoutMillis = 5000; // 5 seconds timeout
    long pollIntervalMillis = 100; // 100 milliseconds polling interval
    long startTime = System.currentTimeMillis();

    while (System.currentTimeMillis() - startTime < timeoutMillis) {
      if (isTrustedSetupLoaded()) {
        trustedSetupLoaded = true;
        break;
      }
      try {
        Thread.sleep(pollIntervalMillis);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        fail("Thread interrupted while waiting for trusted setup to load", e);
      }
    }

    if (!trustedSetupLoaded) {
      fail("Trusted setup not loaded");
    } else {
      LOG.info("Trusted setup loaded successfully from {}", TRUSTED_SETUP_RESOURCE);
    }
  }

  // Check if the trusted setup is loaded by attempting to create a KZG commitment.
  private boolean isTrustedSetupLoaded() {
    try {
      // Attempt to create a KZG commitment with an empty blob.
      // If the trusted setup is loaded, this should throw a C_KZG_BADARGS exception.
      CKZG4844JNI.blobToKzgCommitment(new byte[0]);
      return false;
    } catch (CKZGException e) {
      // Check if the exception indicates invalid arguments (C_KZG_BADARGS),
      // which confirms the trusted setup is loaded.
      return e.getError() == CKZGException.CKZGError.C_KZG_BADARGS;
    } catch (RuntimeException e) {
      // If we encounter a RuntimeException, native library is not loaded
      return false;
    }
  }
}
