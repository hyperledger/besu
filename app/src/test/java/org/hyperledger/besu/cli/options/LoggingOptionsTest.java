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
package org.hyperledger.besu.cli.options;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;

import java.util.Arrays;

import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;

class LoggingOptionsTest {

  private LoggingOptions loggingOptions;

  @BeforeEach
  void setUp() {
    loggingOptions = LoggingOptions.create();
  }

  // --- Level tests ---

  @Test
  void fatalLevelEqualsToError() {
    loggingOptions.setLogLevel("fatal");
    assertThat(loggingOptions.getLogLevel()).isEqualTo("ERROR");
  }

  @Test
  void setsExpectedLevels() {
    Arrays.stream(Level.values())
        .filter(level -> !Level.FATAL.equals(level))
        .forEach(
            level -> {
              loggingOptions.setLogLevel(level.name());
              assertThat(loggingOptions.getLogLevel()).isEqualTo(level.name());
            });
  }

  @Test
  void failsOnUnknownLevel() {
    loggingOptions.spec = Mockito.mock(CommandSpec.class, RETURNS_DEEP_STUBS);
    assertThatThrownBy(() -> loggingOptions.setLogLevel("unknown"))
        .isInstanceOf(ParameterException.class);
  }

  // --- Format tests ---

  @Test
  void shouldDefaultToPlainFormat() {
    assertThat(loggingOptions.getLoggingFormat()).isEqualTo(LoggingFormat.PLAIN);
  }

  @Test
  void shouldReturnPlainAsDefault() {
    assertThat(LoggingFormat.PLAIN.isJsonFormat()).isFalse();
    assertThat(LoggingFormat.PLAIN.getEventTemplateUri()).isNull();
  }

  @ParameterizedTest
  @EnumSource(
      value = LoggingFormat.class,
      names = {"ECS", "GCP", "LOGSTASH", "GELF"})
  void shouldIdentifyJsonFormats(final LoggingFormat format) {
    assertThat(format.isJsonFormat()).isTrue();
    assertThat(format.getEventTemplateUri()).isNotNull();
    assertThat(format.getEventTemplateUri()).startsWith("classpath:");
  }

  @Test
  void shouldHaveCorrectTemplateUris() {
    assertThat(LoggingFormat.ECS.getEventTemplateUri()).isEqualTo("classpath:EcsLayout.json");
    assertThat(LoggingFormat.GCP.getEventTemplateUri()).isEqualTo("classpath:GcpLayout.json");
    assertThat(LoggingFormat.LOGSTASH.getEventTemplateUri())
        .isEqualTo("classpath:LogstashJsonEventLayoutV1.json");
    assertThat(LoggingFormat.GELF.getEventTemplateUri()).isEqualTo("classpath:GelfLayout.json");
  }
}
