package org.firstinspires.ftc.teamcode.Location;

import static org.firstinspires.ftc.robotcore.external.navigation.AngleUnit.DEGREES;
import static org.firstinspires.ftc.robotcore.external.navigation.AxesOrder.XYZ;
import static org.firstinspires.ftc.robotcore.external.navigation.AxesReference.EXTRINSIC;

import com.qualcomm.robotcore.hardware.Gamepad;
import com.qualcomm.robotcore.hardware.HardwareMap;

import org.firstinspires.ftc.robotcore.external.ClassFactory;
import org.firstinspires.ftc.robotcore.external.Telemetry;
import org.firstinspires.ftc.robotcore.external.hardware.camera.SwitchableCamera;
import org.firstinspires.ftc.robotcore.external.hardware.camera.WebcamName;
import org.firstinspires.ftc.robotcore.external.matrices.OpenGLMatrix;
import org.firstinspires.ftc.robotcore.external.navigation.Orientation;
import org.firstinspires.ftc.robotcore.external.navigation.VuforiaLocalizer;
import org.firstinspires.ftc.robotcore.external.navigation.VuforiaTrackable;
import org.firstinspires.ftc.robotcore.external.navigation.VuforiaTrackables;
import org.firstinspires.ftc.teamcode.RobotParts.MecanumDrive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Location {
    //    private Odometry odometry = null;
    private IMU IMU = null;
    private Camera camera1 = null;
    private Camera camera2 = null;
    private Telemetry.Item telemetry = null;
    private MecanumDrive drivetrain = null;
    private boolean advanced = false;

    //Location
    final int HISTORY_LENGTH = 20;
    ArrayList<Double> historyX = new ArrayList<Double>();
    ArrayList<Double> historyY = new ArrayList<Double>();
    ArrayList<Double> historyHeading = new ArrayList<Double>();
    double x;
    double y;
    double heading;

    // Targets
    private VuforiaTrackables targets = null;
    List<VuforiaTrackable> allTrackables = new ArrayList<VuforiaTrackable>();

    // Since ImageTarget trackables use mm to specifiy their dimensions, we must use mm for all the physical dimension.
    // We will define some constants and conversions here
    private static final float mmTargetHeight = 152.4f;// the height of the center of the target image above the floor
    private static final float fieldTile = 609.6f;
    private static final float halfField = 3 * fieldTile;
    private static final float halfTile = 0.5f * fieldTile;
    private static final float oneAndHalfTile = 1.5f * fieldTile;

    // Camera
    private WebcamName webcam1, webcam2;
    private SwitchableCamera switchableCamera;
    VuforiaLocalizer.Parameters parameters = null;
    private VuforiaLocalizer vuforia;
    double[][] positionCameras = {{230, -115}, {230, 115}};
    private Camera activeCamera = null;
    double[] positionCamera = null;

    // Telemetry
    String text = "";

    public void init(HardwareMap hardwareMap, boolean advancedInit, double[] position, MecanumDrive drivetrainInit, Telemetry.Item telemetryInit, Telemetry.Item telemetryDucks) {
        advanced = advancedInit; // keep track of location
        x = position[0];
        y = position[1];
        heading = position[2];

        historyX.add(x);
        historyY.add(y);
        historyHeading.add(heading);

        //Drivetrain
        drivetrain = drivetrainInit;

        // IMU
        IMU = new IMU();
        IMU.init(hardwareMap);
        IMU.setHeading(heading);

        if (advanced) {
            initVuforia(hardwareMap);
            initTargets();

            // Camera 1
            camera1 = new Camera();
            camera1.init(allTrackables, vuforia, parameters, hardwareMap, "1", 1.32, 0.449, telemetryInit);
            camera1.setPointerPosition(x, y, heading, false);

            // Camera 2
            camera2 = new Camera();
            camera2.init(allTrackables, vuforia, parameters, hardwareMap, "2", 1.32, 1.291, telemetryInit);
            camera2.setPointerPosition(x, y, heading, false);

            // Activate active camera
            updatePointerPositions();
            updateActiveCamera();
        }

        telemetry = telemetryInit;
    }

    public void update() {

        // Update heading
        heading = IMU.getHeading();
        if (heading < -180) {
            heading += 360;
        } else if (heading >= 180) {
            heading -= 360;
        }

        // Camera
        if (advanced) {

            camera1.updateServoPosition();
            camera2.updateServoPosition();

            // Camera update
            activeCamera.update();

            // Calculate new position of robot
            if (activeCamera.targetVisible) {
                double[] locationCamera = activeCamera.getPosition();
                double[] robotCoordinates = activeCamera.calculateRobotCoordinates(positionCamera, heading);
                double robotX = robotCoordinates[0];
                double robotY = robotCoordinates[1];

                historyX.add(locationCamera[0] + robotX);
                historyY.add(locationCamera[1] + robotY);
                historyHeading.add(locationCamera[2]);
            }
        }

        // Remove part of history
        while (historyX.size() > HISTORY_LENGTH) {
            historyX.remove(0);
        }
        while (historyY.size() > HISTORY_LENGTH) {
            historyY.remove(0);
        }
        while (historyHeading.size() > HISTORY_LENGTH) {
            historyHeading.remove(0);
        }

        // Update position
        if (historyX.size() != 0) {
            x = historyX.stream().mapToDouble(a -> a).average().getAsDouble();
        }
        if (historyY.size() != 0) {
            y = historyY.stream().mapToDouble(a -> a).average().getAsDouble();
        }

        // Telemetry
        text += String.format("\nPos robot (mm) {X, Y, heading} = %.1f, %.1f %.1f",
                x, y, heading);

        // Update pointers camera
        if (advanced) {

            // Telemetry
            text += String.format("\nActive cam %s visible %b",
                    activeCamera.id, activeCamera.targetVisible);

            // Update active camera
//            updateActiveCamera();

            // Update where the camera's are looking at
            updatePointerPositions();
        }


        // Flush text
        telemetry.setValue(text);
        text = "";
    }

    public Camera getNotActiveWebcam() {
        if (activeCamera == camera1) {
            return camera2;
        } else {
            return camera1;
        }
    }

    public String getNotActiveWebcamName() {
        if (activeCamera == camera1) {
            return "Webcam 2";
        } else if (activeCamera == camera2) {
            return "Webcam 1";
        } else {
            return "";
        }
    }

    // Give feedback to user
    public void debug(Gamepad gamepad1, Gamepad gamepad2) {
        if (advanced) {
            if (activeCamera.targetVisible) {
                gamepad1.stopRumble();
            } else {
                gamepad1.rumble(1000);
            }

            if (gamepad1.a) {
                drivetrain.stopDriving();
            }

            if (gamepad1.dpad_right) {
                setActiveCamera(1);
            } else if (gamepad1.dpad_left) {
                setActiveCamera(2);
            }
        }
    }

    private void updatePointerPositions() {
        // Camera 1
        double[] robotCoordinates1 = camera1.calculateRobotCoordinates(positionCameras[0], heading);
        double camera1RobotX = robotCoordinates1[0];
        double camera1RobotY = robotCoordinates1[1];

        // Camera 2
        double[] robotCoordinates2 = camera2.calculateRobotCoordinates(positionCameras[1], heading);
        double camera2RobotX = robotCoordinates2[0];
        double camera2RobotY = robotCoordinates2[1];

        // Update pointer positions, keep this way because of pointing at variable
        boolean activeCamera1;
        boolean activeCamera2;
        if (activeCamera == camera1) {
            activeCamera1 = true;
            activeCamera2 = false;
        } else if (activeCamera == camera2) {
            activeCamera1 = false;
            activeCamera2 = true;
        } else {
            activeCamera1 = false;
            activeCamera2 = false;
        }
        camera1.setPointerPosition(x - camera1RobotX, y - camera1RobotY, heading, activeCamera1);
        camera2.setPointerPosition(x - camera2RobotX, y - camera2RobotY, heading, activeCamera2);
    }

    private void updateActiveCamera() {
        // Camera 1
        double[] robotCoordinates1 = camera1.calculateRobotCoordinates(positionCameras[0], heading);
        double camera1RobotX = robotCoordinates1[0];
        double camera1RobotY = robotCoordinates1[1];

        // Camera 2
        double[] robotCoordinates2 = camera2.calculateRobotCoordinates(positionCameras[1], heading);
        double camera2RobotX = robotCoordinates2[0];
        double camera2RobotY = robotCoordinates2[1];

        // Get score's
        boolean activeCamera1;
        boolean activeCamera2;
        if (activeCamera == camera1) {
            activeCamera1 = true;
            activeCamera2 = false;
        } else if (activeCamera == camera2) {
            activeCamera1 = false;
            activeCamera2 = true;
        } else {
            activeCamera1 = false;
            activeCamera2 = false;
        }
        double[] scoreCamera1 = camera1.getBestScore(x - camera1RobotX, y - camera1RobotY, heading, activeCamera1);
        double[] scoreCamera2 = camera2.getBestScore(x - camera2RobotX, y - camera2RobotY, heading, activeCamera2);

        // Switch camera to best score
        double scoreDifference = Math.abs(scoreCamera1[0] - scoreCamera2[0]);
        double MAX_SCORE_TO_SWITCH = 1000;
        double MIN_SCORE_TO_SWITCH = 100;
        double MIN_SCORE_DIFFERENCE = 100;
        if (scoreDifference >= MIN_SCORE_DIFFERENCE) {
            if (scoreCamera1[0] < scoreCamera2[0] && activeCamera != camera1 && scoreCamera1[0] < MAX_SCORE_TO_SWITCH && scoreCamera1[0] > MIN_SCORE_TO_SWITCH) {
                setActiveCamera(1);
            } else if (scoreCamera1[0] > scoreCamera2[0] && activeCamera != camera2 && scoreCamera2[0] < MAX_SCORE_TO_SWITCH && scoreCamera2[0] > MIN_SCORE_TO_SWITCH) {
                setActiveCamera(2);
            }
        }

        // Telemetry
        text += String.format("\ncamera 1 (%.0f, %.1f, %.1f)",
                scoreCamera1[2], scoreCamera1[0], scoreCamera1[1]);
        text += String.format("\ncamera 2 (%.0f, %.1f, %.1f)",
                scoreCamera2[2], scoreCamera2[0], scoreCamera2[1]);
    }

    private void setActiveCamera(int number) {
        drivetrain.stopDriving();

        if (number == 1) {
            activeCamera = camera1;
            positionCamera = positionCameras[0];
            switchableCamera.setActiveCamera(webcam1);
        } else if (number == 2) {
            activeCamera = camera2;
            positionCamera = positionCameras[1];
            switchableCamera.setActiveCamera(webcam2);
        } else {
            throw new java.lang.Error("Active camera number is not 1 or 2");
        }
        activeCamera.targetVisible = false;
        updatePointerPositions();
    }

    public void initVuforia(HardwareMap hardwareMap) {
        int cameraMonitorViewId = hardwareMap.appContext.getResources().getIdentifier("cameraMonitorViewId", "id", hardwareMap.appContext.getPackageName());
        parameters = new VuforiaLocalizer.Parameters(cameraMonitorViewId);
//        parameters = new VuforiaLocalizer.Parameters();

        parameters.vuforiaLicenseKey = "AYijR7b/////AAABmQXodEBY4E1gjxKsoSygzWsm4RFjj/z+nzPa0q3oo3vJNz51j477KysEdl4h1YfezCokKxkUeK3ARNjE1tH80M5gZbubu2bkdF6Ja8gINhJTAY/ZJrFkPGiiLfausXsCWAygHW7ufeu3FLIDp1DN2NHj4rzDP4vRv5z/0T0deRLucpRcv36hqUkJ1N6Duumo0se+BCmdAh7ycUW2wteJ3T1e/LxuO5sI6qtwnJW64fe2n6cehk5su76c9t45AcBfod6f0txGezdzpqY5NoHjz0G/gLvah0vAYW+/0x3yaWy8thEd64OVMVb2q37TsJ1UDjl5qupztdG7nXkRGYF5oaR8CGkm2lqPyugJuRFNMRcM";
        ;

        // Indicate that we wish to be able to switch cameras.
        webcam1 = hardwareMap.get(WebcamName.class, "Webcam 1");
        webcam2 = hardwareMap.get(WebcamName.class, "Webcam 2");
        parameters.cameraName = ClassFactory.getInstance().getCameraManager().nameForSwitchableCamera(webcam1, webcam2);

//        parameters.useExtendedTracking = false;

        //  Instantiate the Vuforia engine
        vuforia = ClassFactory.getInstance().createVuforia(parameters);

        switchableCamera = (SwitchableCamera) vuforia.getCamera();
    }

    public void initTargets() {
        // Load the data sets for the trackable objects. These particular data
        // sets are stored in the 'assets' part of our application.
        targets = vuforia.loadTrackablesFromAsset("FreightFrenzy");

        // For convenience, gather together all the trackable objects in one easily-iterable collection */
        allTrackables.addAll(targets);

        // Name and locate each trackable object
        identifyTarget(0, "Blue Storage", -halfField, oneAndHalfTile, mmTargetHeight, 90, 0, 90);
        identifyTarget(1, "Blue Alliance Wall", halfTile, halfField, mmTargetHeight, 90, 0, 0);
        identifyTarget(2, "Red Storage", -halfField, -oneAndHalfTile, mmTargetHeight, 90, 0, 90);
        identifyTarget(3, "Red Alliance Wall", halfTile, -halfField, mmTargetHeight, 90, 0, 180);


        targets.activate();
    }

    public void stop() {
        if (advanced) {
            targets.deactivate();
        }
    }

    public double getXCoordinate() {
        return x;
    }

    public double getYCoordinate() {
        return y;
    }

    public double getOrientation() {
        return heading;
    }

    public boolean goToPosition(double targetX, double targetY, double targetRotation, double power) {
        if (activeCamera.targetVisible == false) {
            drivetrain.setPowerDirection(0, 0, 0, 0);
            return false;
        }

        // Constants
        double allowableDistanceError = 40;

        // Distances
        double distanceToXTarget = targetX - getXCoordinate();
        double distanceToYTarget = targetY - getYCoordinate();
        double orientationDifference = targetRotation - getOrientation();

        // (-180; 180]
        while (orientationDifference < -180) {
            orientationDifference += 360;
        }
        ;
        while (orientationDifference >= 180) {
            orientationDifference -= 360;
        }

        double distance = Math.hypot(distanceToXTarget, distanceToYTarget);

        // slow down
        double slowDownFrom = 100;
        double minPower = Math.min(0.05, power);

        if (distance < slowDownFrom) {
            power = minPower + (power - minPower) / slowDownFrom * distance;
        }

        if (distance > allowableDistanceError) {
            double robotMovementAngle = Math.toDegrees(Math.atan2(distanceToYTarget, distanceToXTarget)) - getOrientation();
            double robotMovementXComponent = calculateX(robotMovementAngle, power);
            double robotMovementYComponent = calculateY(robotMovementAngle, power);

            double turning = 2 * orientationDifference / distance;
            drivetrain.setPowerDirection(-robotMovementXComponent, robotMovementYComponent, turning, power);
            return false;
        } else {
            drivetrain.setPowerDirection(0, 0, 0, 0);
            return true;
        }
    }

    public boolean goToCircle(double midPointX, double midPointY, double radius) {
        double currentX = getXCoordinate();
        double currentY = getYCoordinate();
        double dx = currentX - midPointX;
        double dy = currentY - midPointY;
        double distance = Math.hypot(dx, dy);
        double closestX = midPointX + dx / distance * radius;
        double closestY = midPointY + dy / distance * radius;
        double rotation = Math.toDegrees(Math.atan2(dy, dx));

        boolean targetReached = goToPosition(closestX, closestY, rotation, 1);

        return targetReached;
    }

    public void driveImu(double x, double y, double orientation, double power) {

        // Calculate orientation
        double orientationDifference = orientation - getOrientation();
        while (orientationDifference < -180) {
            orientationDifference += 360;
        }
        while (orientationDifference >= 180) {
            orientationDifference -= 360;
        }

        double turning = 0.025 * orientationDifference;
        drivetrain.setPowerDirection(x, y, turning, power);
    }

    private double calculateX(double desiredAngle, double speed) {
        return Math.sin(Math.toRadians(desiredAngle)) * speed;
    }

    private double calculateY(double desiredAngle, double speed) {
        return Math.cos(Math.toRadians(desiredAngle)) * speed;
    }

    public double getMedian(ArrayList<Double> list) {
        Collections.sort(list);
        int middle = list.size() / 2;
        middle = middle > 0 && middle % 2 == 0 ? middle - 1 : middle;
        return list.get(middle);
    }

    /***
     * Identify a target by naming it, and setting its position and orientation on the field
     * @param targetIndex
     * @param targetName
     * @param dx, dy, dz  Target offsets in x,y,z axes
     * @param rx, ry, rz  Target rotations in x,y,z axes
     */
    void identifyTarget(int targetIndex, String targetName, float dx, float dy, float dz, float rx, float ry, float rz) {
        VuforiaTrackable aTarget = targets.get(targetIndex);
        aTarget.setName(targetName);
        aTarget.setLocation(OpenGLMatrix.translation(dx, dy, dz)
                .multiplied(Orientation.getRotationMatrix(EXTRINSIC, XYZ, DEGREES, rx, ry, rz)));
    }
}