package android.app;

import android.content.ComponentName;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.List;

public class ActivityManager {



    public List<RunningServiceInfo> getRunningServices(int maxNum) throws SecurityException {
        throw new RuntimeException("Stub!");
    }
    public static class RunningServiceInfo implements Parcelable{
        public ComponentName service;

        protected RunningServiceInfo(Parcel in) {
        }

        @Deprecated
        public static final Creator<RunningServiceInfo> CREATOR = new Creator<RunningServiceInfo>() {
            public RunningServiceInfo createFromParcel(Parcel source) {
                return new RunningServiceInfo(source);
            }
            public RunningServiceInfo[] newArray(int size) {
                return new RunningServiceInfo[size];
            }
        };

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel parcel, int i) {
        }
    }

    public static class RunningTaskInfo extends TaskInfo implements Parcelable {

        protected RunningTaskInfo(Parcel in) {
        }

        public static final Creator<RunningTaskInfo> CREATOR = new Creator<RunningTaskInfo>() {
            public RunningTaskInfo createFromParcel(Parcel source) {
                return new RunningTaskInfo(source);
            }
            public RunningTaskInfo[] newArray(int size) {
                return new RunningTaskInfo[size];
            }
        };

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel parcel, int i) {
        }
    }
}
