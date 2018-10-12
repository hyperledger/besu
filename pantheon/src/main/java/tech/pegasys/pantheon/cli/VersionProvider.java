package tech.pegasys.pantheon.cli;

import tech.pegasys.pantheon.PantheonInfo;

import picocli.CommandLine;

public class VersionProvider implements CommandLine.IVersionProvider {
  @Override
  public String[] getVersion() {
    return new String[] {PantheonInfo.version()};
  }
}
