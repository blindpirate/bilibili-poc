# bilibili issue POC

WARNING: this is NOT recommended in production code.

## Explanation

See the comments on `MonitorPlugin.registerHackyListener()`. In short, instead of registering a `BuildOperationNotificationListener`,
we register a `BuildOperationListener` and convert "build operation" to "build operation notification" by ourselves.

## Steps

- Run `./gradlew monitor-plugin:publishToMavenLocal -DpublishToMavenLocal` to publish the settings plugin to maven local.
- Run `./gradlew help` to see the captured build operations when gradle enterprise plugin is applied.

You can add an extra `-DnoGradleEnterprise` to the build to not apply Gradle enterprise plugin.

