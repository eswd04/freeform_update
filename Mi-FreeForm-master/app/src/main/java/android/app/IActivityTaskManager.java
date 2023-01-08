package android.app;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

import androidx.annotation.RequiresApi;

public interface IActivityTaskManager extends IInterface {

    void registerTaskStackListener(ITaskStackListener listener) throws RemoteException;
    void unregisterTaskStackListener(ITaskStackListener listener) throws RemoteException;
    public void moveStackToDisplay(int stackId, int displayId) throws RemoteException;
    @RequiresApi(31)
    void moveRootTaskToDisplay(int taskId, int displayId);
    public abstract static class Stub extends Binder implements IActivityTaskManager {
        public Stub() {
        }

        public static IActivityTaskManager asInterface(IBinder obj) {
            throw new UnsupportedOperationException();
        }
    }

}
