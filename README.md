# bilibili issue POC

WARNING: this is NOT recommended in production code.

## Explanation

If there is already a `BuildOperationNotificationListener` applied, replace it with `CompositeBuildOperationNotificationListener`
via reflection. See the code here: https://github.com/blindpirate/bilibili-poc/blob/main/monitor-plugin/src/main/java/bilibili/poc/MonitorPlugin.java

## Steps

- Run `./gradlew monitor-plugin:publishToMavenLocal` to publish the settings plugin to maven local.
- Run `./gradlew help` to see the captured build operations when gradle enterprise plugin is applied.

