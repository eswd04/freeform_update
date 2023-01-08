package android.app;

import android.app.ActivityManager.RunningTaskInfo;
import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

import java.util.List;

public interface IActivityManager extends IInterface {

    List<RunningTaskInfo> getTasks(int maxNum) throws RemoteException;

    public abstract static class Stub extends Binder implements IActivityManager {
        public Stub() {
        }

        public static IActivityManager asInterface(IBinder obj) {
            throw new RuntimeException("STUB");
        }

        public IBinder asBinder() {
            throw new RuntimeException("STUB");
        }
    }

}
