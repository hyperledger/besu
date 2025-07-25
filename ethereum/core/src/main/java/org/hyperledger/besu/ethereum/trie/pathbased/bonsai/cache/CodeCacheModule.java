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
package org.hyperledger.besu.ethereum.trie.pathbased.bonsai.cache;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

/**
 * Module for providing the CodeCache instance. This module is used to inject a singleton instance
 * of CodeCache into the application.
 */
@Module
public class CodeCacheModule {

  /** Creates a new instance of CodeCacheModule. */
  public CodeCacheModule() {
    // Default constructor
  }

  /**
   * Provides a singleton instance of CodeCache.
   *
   * @return a new instance of CodeCache
   */
  @Provides
  @Singleton
  public CodeCache provideCodeCache() {
    return new CodeCache();
  }
}
