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

import java.io.*;
import java.math.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.stream.*;

public class Application {
  public static void main(String[] args) throws IOException {
    final int numOfSplits = Integer.parseInt(args[0]);
    final int outputSplitIdx = Integer.parseInt(args[1]);
    final var currentTests = parseCurrentTests(Path.of(args[2]));
    final var testDurationsByModule = parseTiming(Path.of(args[3]), currentTests);

    final var totalDuration = testDurationsByModule.values().stream()
        .flatMap(List::stream)
        .map(TestDuration::duration).reduce(Duration.ZERO, Duration::plus);

    final var idealSplitDuration = totalDuration.dividedBy(numOfSplits);

    System.err.println("Total duration: %s, ideal split duration: %s".formatted(totalDuration, idealSplitDuration));

    final var splits = IntStream.range(0, numOfSplits).mapToObj(i -> new Split()).toArray(Split[]::new);

    for (int i = 0; i < numOfSplits; i++) {
      final var currSplit = splits[i];
      for (final var e : testDurationsByModule.entrySet()) {
        final var currModuleSplit = new ModuleSplit();
        final var moduleName = e.getKey();
        System.err.println("Module name: " + moduleName);
        final var testDurations = e.getValue();
        final var itTestDurations = e.getValue().iterator();
        while (itTestDurations.hasNext()) {
          final var td = itTestDurations.next();
          final var newDuration = currSplit.duration().plus(currModuleSplit.duration().plus(td.duration()));
          System.err.println("currSplit: %s, currModuleSplit: %s, td: %s, newDuration: %s".formatted(currSplit.duration(), currModuleSplit.duration(), td.duration(), newDuration));
          if(newDuration.compareTo(idealSplitDuration) < 0) {
            currModuleSplit.addTestDuration(td);
            itTestDurations.remove();
            System.err.println("added %s, currModuleSplitDuration: %s".formatted(td, currModuleSplit.duration()));
          }
        }

        currSplit.addModuleSplit(moduleName, currModuleSplit);
        System.err.println("added %s, SplitDuration: %s".formatted(moduleName, currSplit.duration()));
        if(currSplit.duration().compareTo(idealSplitDuration) > 0) {
          break;
        }
      }
    }

    final var outputSplit = splits[outputSplitIdx];
    System.out.println(outputSplit);

    for (int i = 0; i < numOfSplits; i++) {
      final var split = splits[i];
      final var modules = split.moduleSplits();
      System.err.println("Split: " + i + " duration:[" + split.duration() + "]");
      for (final var e : modules.entrySet()) {
        final var moduleName = e.getKey();
        final var msm = e.getValue();
        System.err.println("\tduration:[" + msm.duration() + "] " + moduleName);
      }
    }
  }

  static Map<String, ModuleSplit[]> splitModuleTests(final Map<String, Set<TestDuration>> testDurationsByModule, final int numOfSplits) {
    final var modules = new HashMap<String, ModuleSplit[]>();

    for (final var e : testDurationsByModule.entrySet()) {
      final var moduleSplits = IntStream.range(0, numOfSplits).mapToObj(i -> new ModuleSplit()).toArray(ModuleSplit[]::new);
      for (final var td : e.getValue()) {
        addToShorterModuleSplit(moduleSplits, td);
      }
      modules.put(e.getKey(), moduleSplits);
    }
    return modules;
  }

  static void addToShorterModuleSplit(final ModuleSplit[] moduleSplits, final TestDuration td) {
    ModuleSplit shorter = null;
    Duration shorterDuration = Duration.ofDays(1);
    for (final ModuleSplit ms : moduleSplits) {
      if (ms.duration().compareTo(shorterDuration) < 0) {
        shorter = ms;
        shorterDuration = ms.duration();
      }
    }

    shorter.addTestDuration(td);
  }

  static Map<String, List<TestDuration>> parseTiming(final Path timingsFile, final Set<Test> currentTests) throws IOException {
    try (final BufferedReader br = new BufferedReader(new FileReader(timingsFile.toFile()))) {
      final var timings = br.lines().map(line -> line.split(" "))
          .collect(Collectors.toMap(
              parts -> new Test(parts[1], parts[2]),
              parts -> Duration.ofMillis(new BigDecimal(parts[0]).multiply(BigDecimal.valueOf(1000)).longValue()),
              (d1, d2) -> d1.compareTo(d2) > 0 ? d1 : d2));

      // remove removed tests
      final var it = timings.entrySet().iterator();
      while (it.hasNext()) {
        final var e = it.next();
        if (currentTests.contains(e.getKey())) {
          currentTests.remove(e.getKey());
        } else {
          System.err.println("Removed test: " + e.getKey());
          it.remove();
        }
      }

      // any new test?
      currentTests.stream()
          .peek(newTest -> System.err.println("New test: " + newTest))
          .forEach(newTest -> timings.put(newTest, Duration.ZERO));


      return timings.entrySet().stream()
          .sorted((e1,e2) -> -1 * e1.getValue().compareTo(e2.getValue()))
          .collect(
              Collectors.groupingBy(
                  e -> e.getKey().module(),
                  Collectors.mapping(
                      e -> new TestDuration(e.getKey().test(), e.getValue()),
                      Collectors.toList())));
    }
  }

  static Set<Test> parseCurrentTests(final Path currentTestsFile) throws IOException {
    try (final BufferedReader br = new BufferedReader(new FileReader(currentTestsFile.toFile()))) {
      return br.lines().map(line -> line.split(" ")).map(parts -> new Test(parts[0], parts[1])).collect(Collectors.toCollection(HashSet::new));
    }
  }
}

record Test(String test, String module) {
}

record TestDuration(String test, Duration duration) {
}

class ModuleSplit {
  final List<String> tests = new ArrayList<>();
  Duration duration = Duration.ZERO;

  void addTestDuration(TestDuration td) {
    tests.add(td.test());
    duration = duration.plus(td.duration());
  }

  Duration duration() {
    return duration;
  }

  List<String> tests() {
    return tests;
  }

  ModuleSplit merge(ModuleSplit other) {
    this.tests.addAll(other.tests);
    this.duration = duration.plus(other.duration());
    return this;
  }
}

class Split {
  final Map<String, ModuleSplit> moduleSplits = new HashMap<>();
  Duration duration = Duration.ZERO;

  Duration duration() {
    return duration;
  }

  public Map<String, ModuleSplit> moduleSplits() {
    return moduleSplits;
  }

  void addModuleSplit(String moduleName, ModuleSplit ms) {
    if (!ms.tests().isEmpty()) {
      moduleSplits.merge(moduleName, ms, ModuleSplit::merge);
      duration = duration.plus(ms.duration());
    }
  }

  @Override
  public String toString() {
    final var sb = new StringBuilder();
    for (var e : moduleSplits.entrySet()) {
      sb
          .append(":")
          .append(e.getKey().replace('/', ':'))
          .append(":test")
          .append(e.getValue().tests().stream().collect(Collectors.joining(" --tests ", " --tests ", "")))
          .append("\n");
    }
    return sb.toString();
  }
}
