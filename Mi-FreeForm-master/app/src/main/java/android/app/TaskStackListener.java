package android.app;

import android.app.ActivityManager.RunningTaskInfo;
import android.os.RemoteException;

public abstract class TaskStackListener extends ITaskStackListener.Stub {


    public TaskStackListener() {
    }
    public void onTaskRemovalStarted(RunningTaskInfo taskInfo) throws RemoteException {
    }

    public void onTaskRequestedOrientationChanged(int taskId, int requestedOrientation) {
    }

    public void onTaskDisplayChanged(int taskId, int newDisplayId) throws RemoteException {
    }

    public void onActivityLaunchOnSecondaryDisplayRerouted(RunningTaskInfo taskInfo, int requestedDisplayId) throws RemoteException {
    }
}


