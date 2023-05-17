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

package org.hyperledger.besu.components;

import org.hyperledger.besu.Besu;
import org.hyperledger.besu.RunnerBuilder;
import org.hyperledger.besu.chainexport.RlpBlockExporter;
import org.hyperledger.besu.chainimport.JsonBlockImporter;
import org.hyperledger.besu.chainimport.RlpBlockImporter;
import org.hyperledger.besu.cli.BesuCommand;
import org.hyperledger.besu.controller.BesuController;
import org.hyperledger.besu.metrics.prometheus.MetricsConfiguration;

import java.util.Optional;
import javax.inject.Named;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import org.slf4j.Logger;

/**
 * A dagger module that know how to create the BesuCommand, which collects all configuration
 * settings.
 */
@Module
public class BesuCommandModule {

  @Provides
  @Singleton
  BesuCommand provideBesuCommand(final BesuComponent besuComponent) {
    final BesuCommand besuCommand =
        new BesuCommand(
            besuComponent,
            RlpBlockImporter::new,
            JsonBlockImporter::new,
            RlpBlockExporter::new,
            new RunnerBuilder(),
            new BesuController.Builder(),
            Optional.ofNullable(besuComponent.getBesuPluginContext()).orElse(null),
            System.getenv());
    besuCommand.toCommandLine();
    return besuCommand;
  }

  @Provides
  @Singleton
  MetricsConfiguration provideMetricsConfiguration(final BesuCommand provideFrom) {
    return provideFrom.metricsConfiguration();
  }

  @Provides
  @Named("besuCommandLogger")
  @Singleton
  Logger provideBesuCommandLogger() {
    return Besu.getFirstLogger();
  }
}
