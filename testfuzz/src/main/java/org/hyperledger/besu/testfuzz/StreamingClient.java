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
package org.hyperledger.besu.testfuzz;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

class StreamingClient implements FuzzingClient {
  final String name;
  final BufferedReader reader;
  final PrintWriter writer;

  public StreamingClient(final String clientName, final String... command) {
    try {
      Process p = new ProcessBuilder().redirectErrorStream(true).command(command).start();
      this.name = clientName;
      this.reader = new BufferedReader(p.inputReader(StandardCharsets.UTF_8));
      this.writer = new PrintWriter(p.getOutputStream(), true, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String differentialFuzz(final String data) {
    try {
      writer.println(data);
      return reader.readLine();
    } catch (IOException ioe) {
      throw new RuntimeException(ioe);
    }
  }
}
