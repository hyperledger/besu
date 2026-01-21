# Test plugins for Besu Acceptance Tests

## Summary

This project consists of test plugins, used for Besu acceptance tests, they are developed using the [Gradle plugin for Besu plugins](https://github.com/Consensys/besu-plugin-gradle-plugin).
This way the acceptance tests are closer to the standard way of using plugins, and it is also compatible with the plugin verification on startup implemented by Besu.

To achieve that the test-plugins subproject had to be moved to this detached project, due to circular dependencies between Besu and the Gradle plugin. The move as basically no impact to the DevUX in case you are not touching these plugins, since Gradle on Besu will take care of building this project when needed.
More in the technical details, since the Gradle plugin needs to know the version of Besu to use, and we want to use the current local development version, 
before building anything in the detached project, Besu project must publish its artifacts to mavenLocal, for this helper tasks have been added to Besu's `build.gradle` to automate that part, and normally a developer should not know about them, because the normal development workflow remains the same, unless you need to work directly on the test plugins, in that case follow to the next section.

## Test plugins development

In case you need to work on test plugins, the suggested setup is that you work on this project as it is independent, 
so for example open it in a new IDE windows.
The `besuVersion` property is automatically fetch from main Besu project, and when compiling, Besu artifacts are published to the Maven local repo,
so you should be able to see the last changes made on the Besu side.

## Troubleshooting

In case you see that some Besu dependencies are not found when building this project, this can happen on your first build, then go to the main Besu project and run `./gradlew buildDetachedTestPlugins`
