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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings({"java:S106", "CallToPrintStackTrace"}) // we use lots the console, on purpose
class SingleQueryClient implements FuzzingClient {
  final String name;
  String[] command;
  Pattern okRegexp;
  String okRegexpStr;
  int okGroup;
  Pattern failRegexp;
  int failGroup;
  String failRegexpStr;

  public SingleQueryClient(
      final String clientName,
      final String okRegexp,
      final int okGroup,
      final String errorRegexp,
      final int failGroup,
      final String... command) {
    this.name = clientName;
    this.okRegexp = Pattern.compile(okRegexp);
    this.okRegexpStr = okRegexp;
    this.okGroup = okGroup;
    this.failRegexp = Pattern.compile(errorRegexp);
    this.failGroup = failGroup;
    this.failRegexpStr = errorRegexp;
    this.command = command;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  @SuppressWarnings("java:S2142")
  public String differentialFuzz(final String data) {
    if (!data.startsWith("0xef")) {
      return "err: <harness> invalid_magic";
    }
    try {
      List<String> localCommand = new ArrayList<>(command.length + 1);
      localCommand.addAll(Arrays.asList(command));
      localCommand.add(data);
      Process p = new ProcessBuilder().command(localCommand).redirectErrorStream(true).start();
      if (!p.waitFor(1, TimeUnit.SECONDS)) {
        System.out.println("Process Hang for " + name);
        return "fail: process took more than 1 sec " + p.pid();
      }
      String s = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      Matcher m = okRegexp.matcher(s);
      if (m.find()) {
        return "OK " + m.group(okGroup);
      }
      m = failRegexp.matcher(s);
      if (m.find()) {
        return "err: " + m.group(failGroup);
      }
      return "fail: SingleClientQuery failed to get data";
    } catch (InterruptedException | IOException e) {
      e.printStackTrace();
      return "fail: " + e.getMessage();
    }
  }
}
