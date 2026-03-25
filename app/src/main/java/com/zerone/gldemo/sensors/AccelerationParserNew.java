package com.zerone.gldemo.sensors;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;

public class AccelerationParserNew extends GenericParser {
   private float alpha = 0.8f; // 滤波系数
   private float[] gravity = new float[3];

   public AccelerationParserNew(Context context) {
      super(context);
   }

   @Override
   public Sensor[] getSensors() {
      return new Sensor[]{getSensorManager().getDefaultSensor(Sensor.TYPE_ACCELEROMETER)};
   }

   @Override
   public double[] parse(SensorEvent event) {
      if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
         // 低通滤波提取重力分量
         gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0];
         gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1];
         gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2];
         // 计算俯仰角和横滚角
         return calculatePitchAndRoll(gravity);
      }
      return new double[]{0, 0, 0};
   }

   @Override
   protected void reset() {

   }

   private double[] calculatePitchAndRoll(float[] gravity) {
      double pitch = 0;
      double roll = 0;

      // 计算俯仰角
      pitch = Math.toDegrees(Math.atan2(gravity[1], Math.sqrt(gravity[0] * gravity[0] + gravity[2] * gravity[2])));

      // 计算横滚角
      roll = Math.toDegrees(Math.atan2(gravity[0], Math.sqrt(gravity[1] * gravity[1] + gravity[2] * gravity[2])));

      return new double[]{pitch, roll,gravity[2]};
   }

}
