package bilibili.poc;

import org.gradle.api.Plugin;
import org.gradle.api.initialization.Settings;
import org.gradle.api.internal.GradleInternal;
import org.gradle.initialization.DefaultSettings;
import org.gradle.internal.operations.notify.BuildOperationFinishedNotification;
import org.gradle.internal.operations.notify.BuildOperationNotificationListener;
import org.gradle.internal.operations.notify.BuildOperationNotificationListenerRegistrar;
import org.gradle.internal.operations.notify.BuildOperationProgressNotification;
import org.gradle.internal.operations.notify.BuildOperationStartedNotification;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

public class MonitorPlugin implements Plugin<Settings> {
    @Override
    public void apply(Settings settings) {
        try {
            if (settings instanceof DefaultSettings) {
                BuildOperationNotificationListenerRegistrar registrar = ((GradleInternal) settings.getGradle()).getServices().get(BuildOperationNotificationListenerRegistrar.class);
                if (registrar.getClass().getSimpleName().equals("BuildOperationNotificationBridge")) {
                    Field stateField = registrar.getClass().getDeclaredField("state");
                    stateField.setAccessible(true);
                    Object state = stateField.get(registrar);

                    Field notificationListenerField = state.getClass().getDeclaredField("notificationListener");
                    notificationListenerField.setAccessible(true);
                    BuildOperationNotificationListener existingListener = (BuildOperationNotificationListener) notificationListenerField.get(state);

                    if (existingListener != null) {
                        System.out.println("Replace existing " + existingListener + " to composite listener!");

                        CompositeBuildOperationNotificationListener compositeListener = new CompositeBuildOperationNotificationListener(
                            existingListener, new MonitorBuildOperationNotificationListener());

                        // Replace BuildOperationNotificationBridge.state.notificationListener
                        notificationListenerField.set(state, compositeListener);

                        Field replayAndAttachListenerField = state.getClass().getDeclaredField("replayAndAttachListener");
                        replayAndAttachListenerField.setAccessible(true);
                        Object replayAndAttachListener = replayAndAttachListenerField.get(state);

                        Field listenerFieldInReplayAndAttachListener = replayAndAttachListener.getClass().getDeclaredField("listener");
                        listenerFieldInReplayAndAttachListener.setAccessible(true);
                        // Replace BuildOperationNotificationBridge.state.replayAndAttachListener.listener
                        listenerFieldInReplayAndAttachListener.set(replayAndAttachListener, compositeListener);

                        System.out.println("Finished replacing existing " + existingListener + " to composite listener!");
                    }
                } else {
                    registrar.register(new MonitorBuildOperationNotificationListener());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static class CompositeBuildOperationNotificationListener implements BuildOperationNotificationListener {
        private final List<BuildOperationNotificationListener> delegates;

        public CompositeBuildOperationNotificationListener(BuildOperationNotificationListener... delegates) {
            this.delegates = Arrays.asList(delegates);
        }

        @Override
        public void started(BuildOperationStartedNotification notification) {
            delegates.forEach((delegate) -> delegate.started(notification));
        }

        @Override
        public void progress(BuildOperationProgressNotification notification) {
            delegates.forEach((delegate) -> delegate.progress(notification));
        }

        @Override
        public void finished(BuildOperationFinishedNotification notification) {
            delegates.forEach((delegate) -> delegate.finished(notification));
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
