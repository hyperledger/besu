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

/** Supported structured logging formats for the --logging-format CLI option. */
public enum LoggingFormat {
  /** Traditional pattern-based console logging (default). */
  PLAIN(null),

  /** Elastic Common Schema JSON format. */
  ECS("classpath:EcsLayout.json"),

  /** Google Cloud Platform JSON format. */
  GCP("classpath:GcpLayout.json"),

  /** Logstash JSON Event Layout V1. */
  LOGSTASH("classpath:LogstashJsonEventLayoutV1.json"),

  /** Graylog Extended Log Format. */
  GELF("classpath:GelfLayout.json");

  private final String eventTemplateUri;

  LoggingFormat(final String eventTemplateUri) {
    this.eventTemplateUri = eventTemplateUri;
  }

  /**
   * Gets the Log4j2 JsonTemplateLayout event template URI.
   *
   * @return the event template URI, or null for PLAIN format
   */
  public String getEventTemplateUri() {
    return eventTemplateUri;
  }

  /**
   * Whether this format uses structured JSON output.
   *
   * @return true if this is a JSON-based format
   */
  public boolean isJsonFormat() {
    return this != PLAIN;
  }
}
