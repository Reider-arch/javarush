package com.javarush.jira.bugtracking.task;

import com.javarush.jira.bugtracking.Handlers;
import com.javarush.jira.bugtracking.task.to.ActivityTo;
import com.javarush.jira.common.error.DataConflictException;
import com.javarush.jira.login.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import static com.javarush.jira.bugtracking.task.TaskUtil.getLatestValue;

@Service
@RequiredArgsConstructor
public class ActivityService {
    private final TaskRepository taskRepository;

    private final Handlers.ActivityHandler handler;

    private static void checkBelong(HasAuthorId activity) {
        if (activity.getAuthorId() != AuthUser.authId()) {
            throw new DataConflictException("Activity " + activity.getId() + " doesn't belong to " + AuthUser.get());
        }
    }

    @Transactional
    public Activity create(ActivityTo activityTo) {
        checkBelong(activityTo);
        Task task = taskRepository.getExisted(activityTo.getTaskId());
        if (activityTo.getStatusCode() != null) {
            task.checkAndSetStatusCode(activityTo.getStatusCode());
        }
        if (activityTo.getTypeCode() != null) {
            task.setTypeCode(activityTo.getTypeCode());
        }
        return handler.createFromTo(activityTo);
    }

    @Transactional
    public void update(ActivityTo activityTo, long id) {
        checkBelong(handler.getRepository().getExisted(activityTo.getId()));
        handler.updateFromTo(activityTo, id);
        updateTaskIfRequired(activityTo.getTaskId(), activityTo.getStatusCode(), activityTo.getTypeCode());
    }

    @Transactional
    public void delete(long id) {
        Activity activity = handler.getRepository().getExisted(id);
        checkBelong(activity);
        handler.delete(activity.id());
        updateTaskIfRequired(activity.getTaskId(), activity.getStatusCode(), activity.getTypeCode());
    }

    private void updateTaskIfRequired(long taskId, String activityStatus, String activityType) {
        if (activityStatus != null || activityType != null) {
            Task task = taskRepository.getExisted(taskId);
            List<Activity> activities = handler.getRepository().findAllByTaskIdOrderByUpdatedDesc(task.id());
            if (activityStatus != null) {
                String latestStatus = getLatestValue(activities, Activity::getStatusCode);
                if (latestStatus == null) {
                    throw new DataConflictException("Primary activity cannot be delete or update with null values");
                }
                task.setStatusCode(latestStatus);
            }
            if (activityType != null) {
                String latestType = getLatestValue(activities, Activity::getTypeCode);
                if (latestType == null) {
                    throw new DataConflictException("Primary activity cannot be delete or update with null values");
                }
                task.setTypeCode(latestType);
            }
        }
    }
    @Transactional(readOnly = true)
    public Duration getTimeInProgress(Task task) {
        List<Activity> activities = handler.getRepository()
                .findAllByTaskIdOrderByUpdatedDesc(task.id());

        LocalDateTime start = findStatusTime(activities, "in_progress");
        LocalDateTime end = findStatusTime(activities, "ready_for_review");

        if (start == null || end == null) return Duration.ZERO;

        return Duration.between(start, end);
    }

    /**
     * Час перебування задачі на тестуванні (ready_for_review → done)
     */
    @Transactional(readOnly = true)
    public Duration getTimeOnTesting(Task task) {
        List<Activity> activities = handler.getRepository()
                .findAllByTaskIdOrderByUpdatedDesc(task.id());

        LocalDateTime start = findStatusTime(activities, "ready_for_review");
        LocalDateTime end = findStatusTime(activities, "done");

        if (start == null || end == null) return Duration.ZERO;

        return Duration.between(start, end);
    }

    /**
     * Допоміжний метод: повертає перший timestamp для конкретного статусу
     */
    private LocalDateTime findStatusTime(List<Activity> activities, String statusCode) {
        return activities.stream()
                .filter(a -> statusCode.equals(a.getStatusCode()))
                .min(Comparator.comparing(Activity::getUpdated)) // перший по часу
                .map(Activity::getUpdated)
                .orElse(null);
    }
}
