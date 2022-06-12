package bilibili.poc;

import org.gradle.api.Plugin;
import org.gradle.api.initialization.Settings;
import org.gradle.api.internal.GradleInternal;
import org.gradle.initialization.DefaultSettings;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.BuildOperationListenerManager;
import org.gradle.internal.operations.notify.BuildOperationFinishedNotification;
import org.gradle.internal.operations.notify.BuildOperationNotificationListener;
import org.gradle.internal.operations.notify.BuildOperationNotificationListenerRegistrar;
import org.gradle.internal.operations.notify.BuildOperationProgressNotification;
import org.gradle.internal.operations.notify.BuildOperationStartedNotification;

import java.util.Queue;
import java.util.concurrent.locks.Lock;

import static bilibili.poc.Utils.getFieldViaReflection;

public class MonitorPlugin implements Plugin<Settings> {
    @Override
    public void apply(Settings settings) {
        if (settings instanceof DefaultSettings) {
            BuildOperationNotificationListenerRegistrar registrar = ((GradleInternal) settings.getGradle()).getServices().get(BuildOperationNotificationListenerRegistrar.class);
            BuildOperationNotificationListener listener = new MonitorBuildOperationNotificationListener();
            if (System.getProperty("noGradleEnterprise") != null) {
                registrar.register(listener);
            } else {
                if (!registrar.getClass().getSimpleName().equals("BuildOperationNotificationBridge")) {
                    throw new IllegalStateException("This hack only works with BuildOperationNotificationBridge class!");
                }
                registerHackyListener(registrar, listener);
            }
        }
    }

    /*
     This is a bit tricky. In Gradle, "build operation notification" comes from "build operation" (See https://github.com/gradle/gradle/blob/fe98989182eddc66d20ee7fea82c1532287d5090/subprojects/core/src/main/java/org/gradle/internal/operations/notify/BuildOperationNotificationBridge.java#L143).
     The received build operation notifications are not sent to `BuildOperationNotificationListener` immediately, because the `BuildOperationNotificationListener`
     may not be registered yet when the build starts. To resolve this issue, `BuildOperationNotificationBridge.state.replayAndAttachListener` records the events in memory
     and sends all "storedEvents" to the first `BuildOperationNotificationListener` registered, that's why only one `BuildOperationNotificationListener` is supported.

     In our case, we want to get build operation notifications without interfering the `BuildOperationNotificationListener` registered by Gradle Enterprise,
     so instead of registering a `BuildOperationNotificationListener`, we register a `BuildOperationListener` and convert "BuildOperation" to "BuildOperationNotification"
     by ourselves. These code are mostly copied from `BuildOperationNotificationBridge`, with two changes:
     1. We need to copy some internal state from `BuildOperationNotificationBridge$Adapter`: `parents` and `active`.
     2. We need to remove the listener when the build finishes: `BuildOperationToBuildOperationNotificationAdapter.allOperationsFinishedCallback`.
     */
    private void registerHackyListener(BuildOperationNotificationListenerRegistrar registrar, BuildOperationNotificationListener listener) {
        Object state = getFieldViaReflection(registrar, "state");

        BuildOperationNotificationListener existingListener = (BuildOperationNotificationListener) getFieldViaReflection(state, "notificationListener");

        if (existingListener == null) {
            BuildOperationListenerManager buildOperationListenerManager = (BuildOperationListenerManager) getFieldViaReflection(registrar, "buildOperationListenerManager");

            Object replayAndAttachListener = getFieldViaReflection(state, "replayAndAttachListener");
            Object recordingListener = getFieldViaReflection(replayAndAttachListener, "recordingListener");
            Queue<Object> storedEvents = (Queue<Object>) getFieldViaReflection(recordingListener, "storedEvents");

            BuildOperationListener adapter = new BuildOperationToBuildOperationNotificationAdapter(
                getFieldViaReflection(state, "buildOperationListener"),
                listener,
                buildOperationListenerManager::removeListener
            );

            for (Object storedEvent : storedEvents) {
                if (storedEvent instanceof BuildOperationStartedNotification) {
                    listener.started((BuildOperationStartedNotification) storedEvent);
                } else if (storedEvent instanceof BuildOperationProgressNotification) {
                    listener.progress((BuildOperationProgressNotification) storedEvent);
                } else if (storedEvent instanceof BuildOperationFinishedNotification) {
                    listener.finished((BuildOperationFinishedNotification) storedEvent);
                }
            }
            buildOperationListenerManager.addListener(adapter);
        } else {
            throw new IllegalStateException("This plugin must be applied before gradle enterprise plugin!");
        }
    }

    public static class MonitorBuildOperationNotificationListener implements BuildOperationNotificationListener {
        @Override
        public void started(BuildOperationStartedNotification notification) {
            System.out.println("Started: " + notification);
        }

        @Override
        public void progress(BuildOperationProgressNotification notification) {
//            System.out.println("Progress: " + notification);
        }

        @Override
        public void finished(BuildOperationFinishedNotification notification) {
            System.out.println("Finished: " + notification);
        }
    }
}
