package com.vuforia.engine.CoreSamples.app.ObjectRecognition;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;

public class SensorViewModel extends AndroidViewModel {
    private ComplimentaryLinearAccelerationSensorLiveData complimentaryLinearAccelerationSensorLiveData;


    public SensorViewModel(Application application) {
        super(application);

        this.complimentaryLinearAccelerationSensorLiveData = new ComplimentaryLinearAccelerationSensorLiveData(application);
    }

    public ComplimentaryLinearAccelerationSensorLiveData getComplimentaryLinearAccelerationSensorLiveData() {
        return complimentaryLinearAccelerationSensorLiveData;
    }
}
