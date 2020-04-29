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
 *
 */

package org.hyperledger.besu.evmtool;

import javax.inject.Named;

import dagger.Module;
import dagger.Provides;
import picocli.CommandLine.Option;

@SuppressWarnings("WeakerAccess")
@Module
public class EvmToolCommandOptionsModule {

  @Option(
      names = {"--revert-reason-enabled"},
      paramLabel = "<Boolean>",
      description = "Should revert reasons be persisted. (default: ${FALLBACK-VALUE})",
      arity = "0..1",
      fallbackValue = "true")
  final Boolean revertReasonEnabled = true;

  @Provides
  @Named("RevertReasonEnabled")
  boolean isRevertReasonEnabled() {
    return revertReasonEnabled;
  }
}
