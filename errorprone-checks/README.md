The creation of custom errorprone checkers was largely derived from:
* https://github.com/tbroyer/gradle-errorprone-plugin
* https://errorprone.info/docs/installation
* https://github.com/google/error-prone/wiki/Writing-a-check

To allow for debugging from within intellij, the following must be added to the VM args
in the run/debug configuration (this assumes your gradle cache is at the default location under
your home):
-Xbootclasspath/p:${HOME}/.gradle/caches/./modules-2/files-2.1/com.google.errorprone/javac/9+181-r4173-1/bdf4c0aa7d540ee1f7bf14d47447aea4bbf450c5/javac-9+181-r4173-1.jar
