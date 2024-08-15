/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.components;

import org.hyperledger.besu.enclave.EnclaveFactory;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.privacy.storage.PrivacyStorageProvider;

import java.net.URI;
import java.net.URISyntaxException;

import dagger.Module;
import dagger.Provides;
import io.vertx.core.Vertx;

/** Provides a general use PrivacyParameters instance for testing. */
@Module
public class PrivacyParametersModule {

  @Provides
  PrivacyParameters providePrivacyParameters(
      final PrivacyStorageProvider storageProvider, final Vertx vertx) {
    try {
      return new PrivacyParameters.Builder()
          .setEnabled(true)
          .setEnclaveUrl(new URI("http://127.0.0.1:8000"))
          .setStorageProvider(storageProvider)
          .setEnclaveFactory(new EnclaveFactory(vertx))
          .setFlexiblePrivacyGroupsEnabled(false)
          .build();
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }
}
