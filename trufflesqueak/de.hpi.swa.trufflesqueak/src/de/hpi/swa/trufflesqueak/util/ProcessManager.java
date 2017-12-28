package de.hpi.swa.trufflesqueak.util;

import de.hpi.swa.trufflesqueak.SqueakImageContext;
import de.hpi.swa.trufflesqueak.exceptions.PrimitiveFailed;
import de.hpi.swa.trufflesqueak.exceptions.ProcessSwitch;
import de.hpi.swa.trufflesqueak.model.BaseSqueakObject;
import de.hpi.swa.trufflesqueak.model.ContextObject;
import de.hpi.swa.trufflesqueak.model.PointersObject;
import de.hpi.swa.trufflesqueak.util.Constants.ASSOCIATION;
import de.hpi.swa.trufflesqueak.util.Constants.LINK;
import de.hpi.swa.trufflesqueak.util.Constants.LINKED_LIST;
import de.hpi.swa.trufflesqueak.util.Constants.PROCESS;
import de.hpi.swa.trufflesqueak.util.Constants.PROCESS_SCHEDULER;
import de.hpi.swa.trufflesqueak.util.Constants.SPECIAL_OBJECT_INDEX;

public class ProcessManager {
    private static ProcessManager instance;

    public static ProcessManager getInstance() {
        return instance;
    }

    public static void initialize(SqueakImageContext img) {
        instance = new ProcessManager(img);
    }

    private final SqueakImageContext image;

    private ProcessManager(SqueakImageContext image) {
        this.image = image;
    }

    public boolean isEmptyList(BaseSqueakObject list) {
        return list.at0(LINKED_LIST.FIRST_LINK).equals(image.nil);
    }

    public BaseSqueakObject removeFirstLinkOfList(BaseSqueakObject list) {
        // Remove the first process from the given linked list.
        BaseSqueakObject first = (BaseSqueakObject) list.at0(LINKED_LIST.FIRST_LINK);
        BaseSqueakObject last = (BaseSqueakObject) list.at0(LINKED_LIST.LAST_LINK);
        if (first.equals(last)) {
            list.atput0(LINKED_LIST.FIRST_LINK, image.nil);
            list.atput0(LINKED_LIST.LAST_LINK, image.nil);
        } else {
            list.atput0(LINKED_LIST.FIRST_LINK, first.at0(LINK.NEXT_LINK));
        }
        first.atput0(LINK.NEXT_LINK, image.nil);
        return first;
    }

    public void resumeProcess(BaseSqueakObject newProcess) {
        BaseSqueakObject activeProcess = activeProcess();
        int activePriority = (int) activeProcess.at0(PROCESS.PRIORITY);
        int newPriority = (int) newProcess.at0(PROCESS.PRIORITY);
        if (newPriority > activePriority) {
            this.putToSleep(activeProcess);
            this.transferTo(newProcess);
        } else {
            this.putToSleep(newProcess);
        }
    }

    public PointersObject activeProcess() {
        return (PointersObject) getScheduler().at0(PROCESS_SCHEDULER.ACTIVE_PROCESS);
    }

    public PointersObject getScheduler() {
        PointersObject association = (PointersObject) image.specialObjectsArray.at0(SPECIAL_OBJECT_INDEX.SchedulerAssociation);
        return (PointersObject) association.at0(ASSOCIATION.VALUE);
    }

    public void putToSleep(BaseSqueakObject process) {
        // Save the given process on the scheduler process list for its priority.
        int priority = (int) process.at0(PROCESS.PRIORITY);
        PointersObject processLists = (PointersObject) this.getScheduler().at0(PROCESS_SCHEDULER.PROCESS_LISTS);
        PointersObject processList = (PointersObject) processLists.at0(priority - 1);
        linkProcessToList(process, processList);
    }

    public void transferTo(BaseSqueakObject newProcess) {
        // Record a process to be awakened on the next interpreter cycle.
        PointersObject sched = getScheduler();
        PointersObject activeProcess = (PointersObject) sched.at0(PROCESS_SCHEDULER.ACTIVE_PROCESS);
        sched.atput0(PROCESS_SCHEDULER.ACTIVE_PROCESS, newProcess);
        activeProcess.atput0(PROCESS.SUSPENDED_CONTEXT, activeProcess.at0(PROCESS.SUSPENDED_CONTEXT));
        newProcess.atput0(PROCESS.SUSPENDED_CONTEXT, image.nil);
        throw new ProcessSwitch((ContextObject) newProcess.at0(PROCESS.SUSPENDED_CONTEXT));
    }

    public BaseSqueakObject wakeHighestPriority() {
        // Return the highest priority process that is ready to run.
        // Note: It is a fatal VM error if there is no runnable process.
        PointersObject schedLists = (PointersObject) this.getScheduler().at0(PROCESS_SCHEDULER.PROCESS_LISTS);
        int p = schedLists.size() - 1;  // index of last indexable field
        BaseSqueakObject processList;
        do {
            if (p < 0) {
                throw new RuntimeException("scheduler could not find a runnable process");
            }
            processList = (BaseSqueakObject) schedLists.at0(p--);
        } while (this.isEmptyList(processList));
        return removeFirstLinkOfList(processList);
    }

    public void linkProcessToList(BaseSqueakObject process, PointersObject list) {
        // Add the given process to the given linked list and set the backpointer
        // of process to its new list.
        if (isEmptyList(list)) {
            list.atput0(LINKED_LIST.FIRST_LINK, process);
        } else {
            PointersObject lastLink = (PointersObject) list.at0(LINKED_LIST.LAST_LINK);
            lastLink.atput0(LINK.NEXT_LINK, process);
        }
        list.atput0(LINKED_LIST.LAST_LINK, process);
        process.atput0(PROCESS.LIST, list);
    }

    public void removeProcessFromList(BaseSqueakObject process, BaseSqueakObject list) {
        BaseSqueakObject first = (BaseSqueakObject) list.at0(LINKED_LIST.FIRST_LINK);
        BaseSqueakObject last = (BaseSqueakObject) list.at0(LINKED_LIST.LAST_LINK);
        if (process.equals(first)) {
            Object next = process.at0(LINK.NEXT_LINK);
            list.atput0(LINKED_LIST.FIRST_LINK, next);
            if (process.equals(last)) {
                list.atput0(LINKED_LIST.LAST_LINK, image.nil);
            }
        } else {
            BaseSqueakObject temp = first;
            BaseSqueakObject next;
            while (true) {
                if (temp.equals(image.nil)) {
                    throw new PrimitiveFailed();
                }
                next = (BaseSqueakObject) temp.at0(LINK.NEXT_LINK);
                if (next.equals(process)) {
                    break;
                }
                temp = next;
            }
            next = (BaseSqueakObject) process.at0(LINK.NEXT_LINK);
            temp.atput0(LINK.NEXT_LINK, next);
            if (process.equals(last)) {
                list.atput0(LINKED_LIST.LAST_LINK, temp);
            }
        }
        process.atput0(LINK.NEXT_LINK, image.nil);
    }
}
