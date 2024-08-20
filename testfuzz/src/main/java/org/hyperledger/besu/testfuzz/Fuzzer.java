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

import org.hyperledger.besu.crypto.Hash;
import org.hyperledger.besu.crypto.MessageDigestFactory;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import com.gitlab.javafuzz.core.AbstractFuzzTarget;
import com.gitlab.javafuzz.core.Corpus;
import org.apache.tuweni.bytes.Bytes;
import org.jacoco.core.data.ExecutionData;
import org.jacoco.core.data.ExecutionDataReader;
import org.jacoco.core.data.IExecutionDataVisitor;
import org.jacoco.core.data.ISessionInfoVisitor;
import org.jacoco.core.data.SessionInfo;

/** Ported from javafuzz because JaCoCo APIs changed. */
@SuppressWarnings({"java:S106", "CallToPrintStackTrace"}) // we use lots the console, on purpose
public class Fuzzer {
  private final AbstractFuzzTarget target;
  private final Corpus corpus;
  private final Object agent;
  private final Method getExecutionDataMethod;
  private long executionsInSample;
  private long lastSampleTime;
  private long totalExecutions;
  private long totalCoverage;

  Supplier<String> fuzzStats;

  /**
   * Create a new fuzzer
   *
   * @param target The target to fuzz
   * @param dirs the list of corpus dirs and files, comma separated.
   * @param fuzzStats additional fuzzing data from the client
   * @throws ClassNotFoundException If Jacoco RT is not found (because jacocoagent.jar is not
   *     loaded)
   * @throws NoSuchMethodException If the wrong version of Jacoco is loaded
   * @throws InvocationTargetException If the wrong version of Jacoco is loaded
   * @throws IllegalAccessException If the wrong version of Jacoco is loaded
   * @throws NoSuchAlgorithmException If the SHA-256 crypto algo cannot be loaded.
   */
  public Fuzzer(
      final AbstractFuzzTarget target, final String dirs, final Supplier<String> fuzzStats)
      throws ClassNotFoundException,
          NoSuchMethodException,
          InvocationTargetException,
          IllegalAccessException,
          NoSuchAlgorithmException {
    this.target = target;
    this.corpus = new Corpus(dirs);
    this.fuzzStats = fuzzStats;
    Class<?> c = Class.forName("org.jacoco.agent.rt.RT");
    Method getAgentMethod = c.getMethod("getAgent");
    this.agent = getAgentMethod.invoke(null);
    this.getExecutionDataMethod = agent.getClass().getMethod("getExecutionData", boolean.class);
    fileNameForBuffer(new byte[0]);
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
      execs_per_second = (this.executionsInSample * 1000 / (endTime - this.lastSampleTime));
    }
    this.lastSampleTime = endTime;
    this.executionsInSample = 0;

    System.out.printf(
        "#%d %s     cov: %d corp: %d exec/s: %d rss: %d MB %s%n",
        this.totalExecutions,
        type,
        this.totalCoverage,
        this.corpus.getLength(),
        execs_per_second,
        rss,
        fuzzStats.get());
  }

  /**
   * Runs the fuzzer until the VM is shut down
   *
   * @throws InvocationTargetException if the wrong version of jacoco is loaded
   * @throws IllegalAccessException if the wrong version of jacoco is loaded
   * @throws NoSuchAlgorithmException if our favorite hash algo is not loaded
   */
  @SuppressWarnings("java:S2189") // the endless loop is on purpose
  public void start()
      throws InvocationTargetException, IllegalAccessException, NoSuchAlgorithmException {
    System.out.printf("#0 READ units: %d%n", this.corpus.getLength());
    this.totalCoverage = 0;
    this.totalExecutions = 0;
    this.executionsInSample = 0;
    this.lastSampleTime = System.currentTimeMillis();

    Map<String, Integer> hitMap = new HashMap<>();

    while (true) {
      byte[] buf = this.corpus.generateInput();
      // The next version will run this in a different thread.
      try {
        this.target.fuzz(buf);
      } catch (Exception e) {
        e.printStackTrace(System.out);
        this.writeCrash(buf);
        System.exit(1);
        break;
      }

      this.totalExecutions++;
      this.executionsInSample++;

      long newCoverage = getHitCount(hitMap);
      if (newCoverage > this.totalCoverage) {
        this.totalCoverage = newCoverage;
        this.corpus.putBuffer(buf);
        this.logStats("NEW");

        // If you want hex strings of new hits, uncomment the following.
        // String filename = fileNameForBuffer(buf);
        // try (var pw =
        //     new PrintWriter(
        //         new BufferedWriter(
        //             new OutputStreamWriter(new FileOutputStream(filename), UTF_8)))) {
        //   pw.println(Bytes.wrap(buf).toHexString());
        //   System.out.println(filename);
        // } catch (IOException e) {
        //   e.printStackTrace(System.out);
        // }
      } else if ((System.currentTimeMillis() - this.lastSampleTime) > 30000) {
        this.logStats("PULSE");
      }
    }
  }

  private static String fileNameForBuffer(final byte[] buf) throws NoSuchAlgorithmException {
    MessageDigest md = MessageDigestFactory.create(MessageDigestFactory.SHA256_ALG);
    md.update(buf);
    byte[] digest = md.digest();
    return String.format("./new-%064x.hex", new BigInteger(1, digest));
  }

  private long getHitCount(final Map<String, Integer> hitMap)
      throws IllegalAccessException, InvocationTargetException {
    byte[] dumpData = (byte[]) this.getExecutionDataMethod.invoke(this.agent, false);
    ExecutionDataReader edr = new ExecutionDataReader(new ByteArrayInputStream(dumpData));
    HitCounter hc = new HitCounter(hitMap);
    edr.setExecutionDataVisitor(hc);
    edr.setSessionInfoVisitor(hc);
    try {
      edr.read();
    } catch (IOException e) {
      e.printStackTrace();
      this.writeCrash(dumpData);
    }

    return hc.getHits();
  }

  static class HitCounter implements IExecutionDataVisitor, ISessionInfoVisitor {
    long hits = 0;
    Map<String, Integer> hitMap;

    public HitCounter(final Map<String, Integer> hitMap) {
      this.hitMap = hitMap;
    }

    @Override
    public void visitClassExecution(final ExecutionData executionData) {
      int hit = 0;
      for (boolean b : executionData.getProbes()) {
        if (executionData.getName().startsWith("org/hyperledger/besu/testfuzz/")
            || executionData.getName().startsWith("org/bouncycastle/")
            || executionData.getName().startsWith("com/gitlab/javafuzz/")) {
          continue;
        }
        if (b) {
          hit++;
        }
      }
      String name = executionData.getName();
      if (hitMap.containsKey(name)) {
        if (hitMap.get(name) < hit) {
          hitMap.put(name, hit);
        }
      } else {
        hitMap.put(name, hit);
      }
      hits += hit;
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
