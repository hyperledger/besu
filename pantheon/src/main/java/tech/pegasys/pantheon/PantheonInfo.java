package tech.pegasys.pantheon;

import tech.pegasys.pantheon.util.PlatformDetector;

// This file is generated via a gradle task and should not be edited directly.
public final class PantheonInfo {
  private static final String CLIENT_IDENTITY = "pantheon";
  private static final String VERSION =
      CLIENT_IDENTITY
          + "/v"
          + PantheonInfo.class.getPackage().getImplementationVersion()
          + "/"
          + PlatformDetector.getOS()
          + "/"
          + PlatformDetector.getVM();

  private PantheonInfo() {}

  public static String clientIdentity() {
    return CLIENT_IDENTITY;
  }

  public static String version() {
    return VERSION;
  }
}
