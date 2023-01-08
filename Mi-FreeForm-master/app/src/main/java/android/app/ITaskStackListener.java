package android.app;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;

public interface ITaskStackListener extends IInterface {

    public abstract static class Stub extends Binder implements ITaskStackListener {


        public static ITaskStackListener asInterface(IBinder obj) {
            throw new UnsupportedOperationException();
        }

        public IBinder asBinder() {
            throw new RuntimeException("Stub");
        }
    }


}
