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
package org.hyperledger.besu.evmtool.fuzz;

import org.hyperledger.besu.crypto.Hash;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.NoSuchAlgorithmException;

import com.gitlab.javafuzz.core.AbstractFuzzTarget;
import com.gitlab.javafuzz.core.Corpus;
import org.apache.tuweni.bytes.Bytes;
import org.jacoco.core.data.ExecutionData;
import org.jacoco.core.data.ExecutionDataReader;
import org.jacoco.core.data.IExecutionDataVisitor;
import org.jacoco.core.data.ISessionInfoVisitor;
import org.jacoco.core.data.SessionInfo;

/** Ported from javafuzz because JaCoCo APIs changed. */
public class Fuzzer {
  private final AbstractFuzzTarget target;
  private final Corpus corpus;
  private final Object agent;
  private final Method getExecutionDataMethod;
  private long executionsInSample;
  private long lastSampleTime;
  private long totalExecutions;
  private long totalCoverage;

  /**
   * Create a new fuzzer
   *
   * @param target The target to fuzz
   * @param dirs the list of corpus dirs and files, comma separated.
   * @throws ClassNotFoundException If Jacoco RT is not found (because jacocoagent.jar is not
   *     loaded)
   * @throws NoSuchMethodException If the wrong version of Jacoco is loaded
   * @throws InvocationTargetException If the wrong version of Jacoco is loaded
   * @throws IllegalAccessException If the wrong version of Jacoco is loaded
   */
  public Fuzzer(final AbstractFuzzTarget target, final String dirs)
      throws ClassNotFoundException,
          NoSuchMethodException,
          InvocationTargetException,
          IllegalAccessException {
    this.target = target;
    this.corpus = new Corpus(dirs);
    Class<?> c = Class.forName("org.jacoco.agent.rt.RT");
    Method getAgentMethod = c.getMethod("getAgent");
    this.agent = getAgentMethod.invoke(null);
    this.getExecutionDataMethod = agent.getClass().getMethod("getExecutionData", boolean.class);
  }

  void writeCrash(final byte[] buf) {
    Bytes hash = Hash.sha256(Bytes.wrap(buf));
    String filepath = "crash-" + hash.toUnprefixedHexString();
    try (FileOutputStream fos = new FileOutputStream(filepath)) {
      fos.write(buf);
      System.out.printf("crash was written to %s%n", filepath);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  void logStats(final String type) {
    long rss =
        (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024;
    long endTime = System.currentTimeMillis();
    long execs_per_second = -1;
    if ((endTime - this.lastSampleTime) != 0) {
      execs_per_second = (this.executionsInSample / (endTime - this.lastSampleTime)) * 1000;
    }
    this.lastSampleTime = endTime;
    this.executionsInSample = 0;

    System.out.printf(
        "#%d %s     cov: %d corp: %d exec/s: %d rss: %d MB%n",
        this.totalExecutions,
        type,
        this.totalCoverage,
        this.corpus.getLength(),
        execs_per_second,
        rss);
  }

  /**
   * Runs the fuzzer until the VM is shut down
   *
   * @throws InvocationTargetException if the wrong version of jacoco is loaded
   * @throws IllegalAccessException if the wrong version of jacoco is loaded
   * @throws NoSuchAlgorithmException if our favorite hash algo is not loaded
   */
  // sonar wants loops to end, but this code should loop until interrupted
  @SuppressWarnings("java:S2189")
  public void start()
      throws InvocationTargetException, IllegalAccessException, NoSuchAlgorithmException {
    System.out.printf("#0 READ units: %d%n", this.corpus.getLength());
    this.totalCoverage = 0;
    this.totalExecutions = 0;
    this.executionsInSample = 0;
    this.lastSampleTime = System.currentTimeMillis();

    while (true) {
      byte[] buf = this.corpus.generateInput();
      // Next version will run this in a different thread.
      try {
        this.target.fuzz(buf);
      } catch (Exception e) {
        e.printStackTrace();
        this.writeCrash(buf);
        System.exit(1);
        break;
      }

      this.totalExecutions++;
      this.executionsInSample++;

      byte[] dumpData = (byte[]) this.getExecutionDataMethod.invoke(this.agent, false);
      ExecutionDataReader edr = new ExecutionDataReader(new ByteArrayInputStream(dumpData));
      HitCounter hc = new HitCounter();
      edr.setExecutionDataVisitor(hc);
      edr.setSessionInfoVisitor(hc);
      try {
        edr.read();
      } catch (IOException e) {
        e.printStackTrace();
        this.writeCrash(dumpData);
        System.exit(1);
        break;
      }

      long newCoverage = hc.getHits();
      if (newCoverage > this.totalCoverage) {
        this.totalCoverage = newCoverage;
        this.corpus.putBuffer(buf);
        this.logStats("NEW");
      } else if ((System.currentTimeMillis() - this.lastSampleTime) > 30000) {
        this.logStats("PULSE");
      }
    }
  }

  static class HitCounter implements IExecutionDataVisitor, ISessionInfoVisitor {
    long hits = 0;

    @Override
    public void visitClassExecution(final ExecutionData executionData) {
      for (boolean b : executionData.getProbes()) {
        if (b) {
          hits++;
        }
      }
    }

    public long getHits() {
      return hits;
    }

    @Override
    public void visitSessionInfo(final SessionInfo sessionInfo) {
      // nothing to do.  Data parser requires a session listener.
    }
  }
}
