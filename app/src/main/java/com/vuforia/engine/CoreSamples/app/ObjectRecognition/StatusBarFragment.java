package com.vuforia.engine.CoreSamples.app.ObjectRecognition;

import android.app.Fragment;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.vuforia.engine.CoreSamples.R;

import java.util.Locale;

public class StatusBarFragment extends Fragment {
    private static final String tag = StatusBarFragment.class.getSimpleName();

    // Text views for real-time output
    private TextView textViewXAxis;
    private TextView textViewYAxis;
    private TextView textViewZAxis;
    private TextView textViewHzFrequency;

    private Handler handler;
    private Runnable runnable;

    private float[] acceleration;

    @Override
    public void onActivityCreated (Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        handler = new Handler();
        runnable = new Runnable()
        {
            @Override
            public void run()
            {
                handler.postDelayed(this, 20);
                updateAccelerationText();
            }
        };

        acceleration = new float[4];
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_status_bar, container, false);

        textViewXAxis = view.findViewById(R.id.value_x_axis);
        textViewYAxis = view.findViewById(R.id.value_y_axis);
        textViewZAxis = view.findViewById(R.id.value_z_axis);
        textViewHzFrequency = view.findViewById(R.id.value_hz_frequency);

        return view;
    }

    @Override
    public void onPause() {
        handler.removeCallbacks(runnable);
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        initViewModel();
        handler.post(runnable);
    }

    private void updateAccelerationText()
    {
        if(acceleration.length == 4) {
            // Update the acceleration data
            textViewXAxis.setText(String.format(Locale.getDefault(), "%.2f", acceleration[0]));
            textViewYAxis.setText(String.format(Locale.getDefault(), "%.2f", acceleration[1]));
            textViewZAxis.setText(String.format(Locale.getDefault(), "%.2f", acceleration[2]));
            textViewHzFrequency.setText(String.format(Locale.getDefault(), "%.0f", acceleration[3]));
        }
    }

    private void initViewModel() {
        SensorViewModel model = ViewModelProviders.of((FragmentActivity) getActivity()).get(SensorViewModel.class);

        model.getComplimentaryLinearAccelerationSensorLiveData().removeObservers(this);

        model.getComplimentaryLinearAccelerationSensorLiveData().observe(this, new Observer<float[]>() {
            @Override
            public void onChanged(@Nullable float[] floats) {
                acceleration = floats;
            }
        });
    }
}
