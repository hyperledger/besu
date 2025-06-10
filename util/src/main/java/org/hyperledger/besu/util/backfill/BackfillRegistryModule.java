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
package org.hyperledger.besu.util.backfill;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

/** Dagger module for providing the {@link BackfillRegistry} instance. */
@Module
public class BackfillRegistryModule {

  /** Default constructor */
  public BackfillRegistryModule() {}

  /**
   * Provides a singleton instance of {@link BackfillRegistry}.
   *
   * @return a new instance of {@link BackfillRegistry}
   */
  @Provides
  @Singleton
  public BackfillRegistry provideBackfillRegistry() {
    return new BackfillRegistry();
  }
}
