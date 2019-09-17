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
 */
package org.hyperledger.besu.cli.util;

import static java.util.Arrays.asList;

import java.util.List;

import picocli.CommandLine.IDefaultValueProvider;
import picocli.CommandLine.Model.ArgSpec;

public class CascadingDefaultProvider implements IDefaultValueProvider {

  private final List<IDefaultValueProvider> defaultValueProviders;

  public CascadingDefaultProvider(final IDefaultValueProvider... defaultValueProviders) {
    this.defaultValueProviders = asList(defaultValueProviders);
  }

  @Override
  public String defaultValue(final ArgSpec argSpec) throws Exception {
    for (final IDefaultValueProvider provider : defaultValueProviders) {
      final String defaultValue = provider.defaultValue(argSpec);
      if (defaultValue != null) {
        return defaultValue;
      }
    }
    return null;
  }
}
