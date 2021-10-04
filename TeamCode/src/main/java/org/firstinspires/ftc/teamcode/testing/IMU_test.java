package org.firstinspires.ftc.teamcode.testing;

import com.qualcomm.hardware.bosch.BNO055IMU;
import com.qualcomm.hardware.bosch.JustLoggingAccelerationIntegrator;
import com.qualcomm.robotcore.hardware.HardwareMap;

import org.firstinspires.ftc.robotcore.external.Telemetry;
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.AxesOrder;
import org.firstinspires.ftc.robotcore.external.navigation.AxesReference;
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;
import org.firstinspires.ftc.robotcore.external.navigation.Orientation;

public class IMU_test {
    private BNO055IMU imu;
    Telemetry.Item telemetry = null;
    double curHeading;

    public void init(HardwareMap map, Telemetry.Item telemetryInit){
        //Init IMU
        BNO055IMU.Parameters parameters = new BNO055IMU.Parameters();
        parameters.angleUnit = BNO055IMU.AngleUnit.DEGREES; // set gyro angles to degrees
        parameters.accelUnit = BNO055IMU.AccelUnit.METERS_PERSEC_PERSEC; // set gyro acceleration to m/s/s
        parameters.calibrationDataFile = "AdafruitIMUCalibration.json"; // get calibration file
        parameters.loggingEnabled = true;
        parameters.loggingTag = "IMU";
        parameters.accelerationIntegrationAlgorithm = new JustLoggingAccelerationIntegrator();
        imu = map.get(BNO055IMU.class, "imu"); // retrieve gyro
        imu.initialize(parameters); // set parameters to gyro

        telemetry = telemetryInit;
    }
    public double getRotation() {
        // read the orientation of the robot
        Orientation angles = new Orientation();
        angles = imu.getAngularOrientation(AxesReference.EXTRINSIC, AxesOrder.XYZ, AngleUnit.DEGREES);
        imu.getPosition();
        // and save the heading
        curHeading = angles.thirdAngle;
        updateTelemetry();
        return curHeading;
    }

    public void updateTelemetry(){
        telemetry.setValue(curHeading);
    }

}