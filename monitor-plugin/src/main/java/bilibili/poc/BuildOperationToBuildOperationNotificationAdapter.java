package bilibili.poc;

import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationListener;
import org.gradle.internal.operations.OperationFinishEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.operations.OperationProgressEvent;
import org.gradle.internal.operations.OperationStartEvent;
import org.gradle.internal.operations.notify.BuildOperationFinishedNotification;
import org.gradle.internal.operations.notify.BuildOperationNotificationBridge;
import org.gradle.internal.operations.notify.BuildOperationNotificationListener;
import org.gradle.internal.operations.notify.BuildOperationProgressNotification;
import org.gradle.internal.operations.notify.BuildOperationStartedNotification;
import org.gradle.launcher.exec.RunActionRequirements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

// See BuildOperationNotificationBridge.Adapter
@SuppressWarnings("ALL")
class BuildOperationToBuildOperationNotificationAdapter implements BuildOperationListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(BuildOperationNotificationBridge.class);

    private final BuildOperationNotificationListener notificationListener;

    private final Map<OperationIdentifier, OperationIdentifier> parents;
    private final Map<OperationIdentifier, Object> active;

    private final Consumer<BuildOperationListener> allOperationsFinishedCallback;

    BuildOperationToBuildOperationNotificationAdapter(
        Object sourceAdapter,
        BuildOperationNotificationListener notificationListener,
        Consumer<BuildOperationListener> allOperationsFinishedCallback
    ) {
        parents = new ConcurrentHashMap<>((ConcurrentHashMap<OperationIdentifier, OperationIdentifier>) Utils.getFieldViaReflection(sourceAdapter, "parents"));
        active = new ConcurrentHashMap<>((ConcurrentHashMap<OperationIdentifier, Object>) Utils.getFieldViaReflection(sourceAdapter, "active"));
        this.notificationListener = notificationListener;
        this.allOperationsFinishedCallback = allOperationsFinishedCallback;
    }

    @Override
    public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
        OperationIdentifier id = buildOperation.getId();
        OperationIdentifier parentId = buildOperation.getParentId();

        if (parentId != null) {
            if (active.containsKey(parentId)) {
                parents.put(id, parentId);
            } else {
                parentId = parents.get(parentId);
                if (parentId != null) {
                    parents.put(id, parentId);
                }
            }
        }

        if (buildOperation.getDetails() == null) {
            return;
        }

        active.put(id, "");

        Started notification = new Started(startEvent.getStartTime(), id, parentId, buildOperation.getDetails());

        try {
            notificationListener.started(notification);
        } catch (Throwable e) {
            LOGGER.debug("Build operation notification listener threw an error on " + notification, e);
            maybeThrow(e);
        }
    }

    private void maybeThrow(Throwable e) {
        if (e instanceof Error && !(e instanceof LinkageError)) {
            throw (Error) e;
        }
    }

    @Override
    public void progress(OperationIdentifier buildOperationId, OperationProgressEvent progressEvent) {
        Object details = progressEvent.getDetails();
        if (details == null) {
            return;
        }

        // Find the nearest parent up that we care about and use that as the parent.
        OperationIdentifier owner = findOwner(buildOperationId);
        if (owner == null) {
            return;
        }

        notificationListener.progress(new Progress(owner, progressEvent.getTime(), details));
    }

    private OperationIdentifier findOwner(OperationIdentifier id) {
        if (active.containsKey(id)) {
            return id;
        } else {
            return parents.get(id);
        }
    }

    @Override
    public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
        OperationIdentifier id = buildOperation.getId();
        OperationIdentifier parentId = parents.remove(id);
        if (active.remove(id) == null) {
            return;
        }

        Finished notification = new Finished(finishEvent.getEndTime(), id, parentId, buildOperation.getDetails(), finishEvent.getResult(), finishEvent.getFailure());
        try {
            notificationListener.finished(notification);
        } catch (Throwable e) {
            LOGGER.debug("Build operation notification listener threw an error on " + notification, e);
            maybeThrow(e);
        } finally {
            if (parentId == null) {
                allOperationsFinishedCallback.accept(this);
            }
        }
    }

    private static class Started implements BuildOperationStartedNotification {

        private final long timestamp;

        private final OperationIdentifier id;
        private final OperationIdentifier parentId;
        private final Object details;

        private Started(long timestamp, OperationIdentifier id, OperationIdentifier parentId, Object details) {
            this.timestamp = timestamp;
            this.id = id;
            this.parentId = parentId;
            this.details = details;
        }

        @Override
        public long getNotificationOperationStartedTimestamp() {
            return timestamp;
        }

        @Override
        public Object getNotificationOperationId() {
            return id;
        }

        @Override
        public Object getNotificationOperationParentId() {
            return parentId;
        }

        @Override
        public Object getNotificationOperationDetails() {
            return details;
        }

        @Override
        public String toString() {
            return "BuildOperationStartedNotification{"
                + "id=" + id
                + ", parentId=" + parentId
                + ", timestamp=" + timestamp
                + ", details=" + details
                + '}';
        }


    }

    private static class Progress implements BuildOperationProgressNotification {

        private final OperationIdentifier id;

        private final long timestamp;
        private final Object details;

        Progress(OperationIdentifier id, long timestamp, Object details) {
            this.id = id;
            this.timestamp = timestamp;
            this.details = details;
        }

        @Override
        public Object getNotificationOperationId() {
            return id;
        }

        @Override
        public long getNotificationOperationProgressTimestamp() {
            return timestamp;
        }

        @Override
        public Object getNotificationOperationProgressDetails() {
            return details;
        }

    }

    private static class Finished implements BuildOperationFinishedNotification {

        private final long timestamp;

        private final OperationIdentifier id;
        private final OperationIdentifier parentId;
        private final Object details;
        private final Object result;
        private final Throwable failure;

        private Finished(long timestamp, OperationIdentifier id, OperationIdentifier parentId, Object details, Object result, Throwable failure) {
            this.timestamp = timestamp;
            this.id = id;
            this.parentId = parentId;
            this.details = details;
            this.result = result;
            this.failure = failure;
        }

        @Override
        public long getNotificationOperationFinishedTimestamp() {
            return timestamp;
        }

        @Override
        public Object getNotificationOperationId() {
            return id;
        }

        @Nullable
        @Override
        public Object getNotificationOperationParentId() {
            return parentId;
        }

        @Override
        public Object getNotificationOperationDetails() {
            return details;
        }

        @Override
        public Object getNotificationOperationResult() {
            return result;
        }

        @Override
        public Throwable getNotificationOperationFailure() {
            return failure;
        }

        @Override
        public String toString() {
            return "BuildOperationFinishedNotification{"
                + "id=" + id
                + ", parentId=" + parentId
                + ", timestamp=" + timestamp
                + ", details=" + details
                + ", result=" + result
                + ", failure=" + failure
                + '}';
        }
    }
}
