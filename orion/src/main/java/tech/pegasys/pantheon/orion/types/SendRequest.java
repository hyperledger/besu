/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.orion.types;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.List;

public class SendRequest {
  private byte[] payload;
  private String from;
  private List<String> to;

  public SendRequest(final String payload, final String from, final List<String> to) {
    this.payload = payload.getBytes(UTF_8);
    this.from = from;
    this.to = to;
  }

  public byte[] getPayload() {
    return payload;
  }

  public void setPayload(final String payload) {
    this.payload = payload.getBytes(UTF_8);
  }

  public String getFrom() {
    return from;
  }

  public void setFrom(final String from) {
    this.from = from;
  }

  public List<String> getTo() {
    return to;
  }

  public void setTo(final List<String> to) {
    this.to = to;
  }
}
