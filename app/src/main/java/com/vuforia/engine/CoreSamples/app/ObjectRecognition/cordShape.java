package com.vuforia.engine.CoreSamples.app.ObjectRecognition;

import java.util.ArrayList;

public class cordShape {
    private ArrayList<float[]> shape = new ArrayList<>();
    private float[] total_offset = new float[3];
    public void addCord(float[] cord, float[] offset){
        total_offset[0] += offset[0];
        total_offset[1] += offset[1];
        total_offset[2] += offset[2];
        cord[0] += total_offset[0];
        cord[1] += total_offset[1];
        cord[2] += total_offset[2];
        shape.add(cord);
    }

    public ArrayList<float[]> getShape() {
        return shape;
    }
}
