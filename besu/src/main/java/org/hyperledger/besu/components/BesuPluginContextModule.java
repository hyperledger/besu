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
 *
 */
package org.hyperledger.besu.components;

import org.hyperledger.besu.services.BesuPluginContextImpl;

import javax.inject.Named;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

/** A dagger module that know how to create the BesuPluginContextImpl singleton. */
@Module
public class BesuPluginContextModule {

  /**
   * Creates a BesuPluginContextImpl, used for plugin service discovery.
   *
   * @return the BesuPluginContext
   */
  @Provides
  @Named("besuPluginContext")
  @Singleton
  public BesuPluginContextImpl provideBesuPluginContext() {
    return new BesuPluginContextImpl();
  }
}
