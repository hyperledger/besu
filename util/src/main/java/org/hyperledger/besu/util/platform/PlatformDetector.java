/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.util.platform;

import java.util.Locale;

/**
 * Detects OS and VMs.
 *
 * <p>Derived from Detector.java https://github.com/trustin/os-maven-plugin/ version 59fd029 on 21
 * Apr 2018, Copyright 2014 Trustin Heuiseung Lee.
 */
public class PlatformDetector {

  private static String _os;
  private static String _osType;
  private static String _vm;
  private static String _arch;

  public static String getOSType() {
    if (_osType == null) {
      detect();
    }
    return _osType;
  }

  public static String getOS() {
    if (_os == null) {
      detect();
    }
    return _os;
  }

  public static String getArch() {
    if (_arch == null) {
      detect();
    }
    return _arch;
  }

  public static String getVM() {
    if (_vm == null) {
      detect();
    }
    return _vm;
  }

  private static final String UNKNOWN = "unknown";

  private static void detect() {
    final String detectedOS = normalizeOS(normalize("os.name"));
    final String detectedArch = normalizeArch(normalize("os.arch"));
    final String detectedVM = normalizeVM(normalize("java.vendor"), normalize("java.vm.name"));
    final String detectedJavaVersion = normalizeJavaVersion("java.specification.version");

    _os = detectedOS + '-' + detectedArch;
    _arch = detectedArch;
    _osType = detectedOS;
    _vm = detectedVM + "-java-" + detectedJavaVersion;
  }

  private static String normalizeOS(final String osName) {
    if (osName.startsWith("aix")) {
      return "aix";
    }
    if (osName.startsWith("hpux")) {
      return "hpux";
    }
    if (osName.startsWith("os400")) {
      // Avoid the names such as os4000
      if (osName.length() <= 5 || !Character.isDigit(osName.charAt(5))) {
        return "os400";
      }
    }
    if (osName.startsWith("linux")) {
      return "linux";
    }
    if (osName.startsWith("macosx") || osName.startsWith("osx")) {
      return "osx";
    }
    if (osName.startsWith("freebsd")) {
      return "freebsd";
    }
    if (osName.startsWith("openbsd")) {
      return "openbsd";
    }
    if (osName.startsWith("netbsd")) {
      return "netbsd";
    }
    if (osName.startsWith("solaris") || osName.startsWith("sunos")) {
      return "sunos";
    }
    if (osName.startsWith("windows")) {
      return "windows";
    }

    return UNKNOWN;
  }

  private static String normalizeArch(final String osArch) {
    if (osArch.matches("^(x8664|amd64|ia32e|em64t|x64)$")) {
      return "x86_64";
    }
    if (osArch.matches("^(x8632|x86|i[3-6]86|ia32|x32)$")) {
      return "x86_32";
    }
    if (osArch.matches("^(ia64w?|itanium64)$")) {
      return "itanium_64";
    }
    if ("ia64n".equals(osArch)) {
      return "itanium_32";
    }
    if (osArch.matches("^(sparc|sparc32)$")) {
      return "sparc_32";
    }
    if (osArch.matches("^(sparcv9|sparc64)$")) {
      return "sparc_64";
    }
    if (osArch.matches("^(arm|arm32)$")) {
      return "arm_32";
    }
    if ("aarch64".equals(osArch)) {
      return "aarch_64";
    }
    if (osArch.matches("^(mips|mips32)$")) {
      return "mips_32";
    }
    if (osArch.matches("^(mipsel|mips32el)$")) {
      return "mipsel_32";
    }
    if ("mips64".equals(osArch)) {
      return "mips_64";
    }
    if ("mips64el".equals(osArch)) {
      return "mipsel_64";
    }
    if (osArch.matches("^(ppc|ppc32)$")) {
      return "ppc_32";
    }
    if (osArch.matches("^(ppcle|ppc32le)$")) {
      return "ppcle_32";
    }
    if ("ppc64".equals(osArch)) {
      return "ppc_64";
    }
    if ("ppc64le".equals(osArch)) {
      return "ppcle_64";
    }
    if ("s390".equals(osArch)) {
      return "s390_32";
    }
    if ("s390x".equals(osArch)) {
      return "s390_64";
    }

    return UNKNOWN;
  }

  static String normalizeVM(final String javaVendor, final String javaVmName) {
    if (javaVmName.contains("graalvm") || javaVendor.contains("graalvm")) {
      return "graalvm";
    }
    if (javaVendor.contains("oracle")) {
      if (javaVmName.contains("openjdk")) {
        return "oracle_openjdk";
      } else {
        return "oracle";
      }
    }
    if (javaVendor.contains("adoptopenjdk")) {
      return "adoptopenjdk";
    }
    if (javaVendor.contains("openj9")) {
      return "openj9";
    }
    if (javaVendor.contains("azul")) {
      if (javaVmName.contains("zing")) {
        return "zing";
      } else {
        return "zulu";
      }
    }
    if (javaVendor.contains("amazoncominc")) {
      return "corretto";
    }
    if (javaVmName.contains("openjdk")) {
      return "openjdk";
    }

    return "-" + javaVendor + "-" + javaVmName;
  }

  static String normalizeJavaVersion(final String javaVersion) {
    // These are already normalized.
    return System.getProperty(javaVersion);
  }

  private static String normalize(final String value) {
    if (value == null) {
      return "";
    }
    return System.getProperty(value).toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", "");
  }
}
