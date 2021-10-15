package org.firstinspires.ftc.teamcode.Location;

import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.HardwareMap;

import org.firstinspires.ftc.robotcore.external.Telemetry;

public class Location {
    private Odometry odometry = null;
    private IMU IMU = null;
    private Telemetry.Item telemetry = null;

    public void init(HardwareMap hardwareMap, Telemetry.Item telemetryInit){
        odometry = new Odometry(
                hardwareMap.get(DcMotor.class, "leftFront"),
                hardwareMap.get(DcMotor.class, "rightFront"),
                hardwareMap.get(DcMotor.class, "leftBack"),
                307.699557);
        IMU = new IMU();
        IMU.init(hardwareMap);
        telemetry = telemetryInit;
    }

    public void update(){
        odometry.globalCoordinatePositionUpdate();
        telemetry.setValue(odometry.getDisplay()+"\n"+IMU.getDisplay());
    }

    public double getRotation(){
        return IMU.getRotation();
    }
}
