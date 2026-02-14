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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class LoggingFormatOptionTest {

  @Test
  void shouldDefaultToPlainFormat() {
    final LoggingFormatOption option = LoggingFormatOption.create();
    assertThat(option.getLoggingFormat()).isEqualTo(LoggingFormat.PLAIN);
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
