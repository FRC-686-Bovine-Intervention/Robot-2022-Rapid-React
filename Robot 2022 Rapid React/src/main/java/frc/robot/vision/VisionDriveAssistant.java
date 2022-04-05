package frc.robot.vision;

import java.util.Optional;

import edu.wpi.first.wpilibj.Timer;
import frc.robot.Constants;
import frc.robot.command_status.DriveCommand;
import frc.robot.command_status.GoalStates;
import frc.robot.command_status.GoalStates.GoalState;
import frc.robot.command_status.RobotState;
import frc.robot.lib.util.DataLogger;
import frc.robot.lib.util.Kinematics;
import frc.robot.lib.util.Kinematics.WheelSpeed;
import frc.robot.lib.util.Pose;
import frc.robot.lib.util.Vector2d;

public class VisionDriveAssistant
{
	private static VisionDriveAssistant instance = new VisionDriveAssistant();	
	public static VisionDriveAssistant getInstance() { return instance; }

    // configuration parameters
    public static boolean allowSpeedControl = false;
    public static double kLookaheadDist = 48.0;   // inches
    public static double kFullThrottleSpeed = 100.0; // inches/sec
    public static double kMaxSpeed =      100.0; // inches/sec
    public static double kMaxAccel =      50.0; // inches/sec^2	

    // members
    public GoalStates goalStates = GoalStates.getInstance();
    public boolean enabled;
    public boolean haveGoal;
    public double distanceToTargetInches;
	public double bearingToTarget;
	public double lookaheadDist;
    public double curvature;
    public double joystickSpeed;    // speed set by driver (manual)
    public double approachSpeed;    // speed set by driver or auto calculated
    public double maxSpeed;         // speed limit based on distance from target
    public WheelSpeed wheelSpeed = new WheelSpeed();

    public Optional<Vector2d> currentFieldToGoal = Optional.empty();
    double kTargetDistanceThresholdFromCenterInches;

    public VisionDriveAssistant() {}

    public DriveCommand assist(DriveCommand _driveCmd, boolean _enable) 
    {
        DriveCommand driveCmd = _driveCmd;
        enabled = _enable;
        double currentTime = Timer.getFPGATimestamp();
 
        // update currentGoalState based on whether target is currently seen, and if button is being pressed
        Optional<GoalState> visionGoalState = goalStates.getBestVisionTarget();
        if (visionGoalState.isPresent())
        {
            currentFieldToGoal = Optional.of( visionGoalState.get().getPosition() );
        }
        else
        {
            if (enabled)
            {
                // target not currently seen, but button is still pressed
                // --> keep same currentFieldToGoal
            }
            else
            {
                // target not currently seen, button not pressed
                currentFieldToGoal = Optional.empty();
            }
        }

        haveGoal = currentFieldToGoal.isPresent();

        // if we don't see a target, continue under driver control
        if (haveGoal)
        {
            // Get range and angle to target
            Vector2d robotToGoal = currentFieldToGoal.get();
	    	double distanceToGoal = robotToGoal.length();
			double bearingToGoal = -robotToGoal.angle(); 	// bearing relative to shooter's heading

            distanceToTargetInches = distanceToGoal - Constants.kCenterToIntake;   // distance from camera
            bearingToTarget = bearingToGoal;

            // Calculate motor settings to turn towards target
            lookaheadDist = Math.min(kLookaheadDist, distanceToTargetInches);	// length of chord <= kLookaheadDist
            curvature     = 2 * Math.sin(bearingToTarget) / lookaheadDist;		// curvature = 1/radius of circle (positive: turn left, negative: turn right)

            // get speed limit based on distance            
            double remainingDistance = Math.max(distanceToTargetInches, 0.0);   // keep maxSpeed = 0 when we pass the target
            maxSpeed = calcSpeedLimit(currentTime, remainingDistance, kMaxSpeed, kMaxAccel);
            maxSpeed = maxSpeed / kFullThrottleSpeed;   // convert from inches/sec to percentage
            maxSpeed = Math.max(maxSpeed, 0.2);

            // Get forward speed
            joystickSpeed = driveCmd.getSpeed(); // in percentage
            approachSpeed = joystickSpeed;

            if (allowSpeedControl)
            {
                // automatically reduce speed to stop in front of target
                approachSpeed = Math.signum(approachSpeed)*Math.min(Math.abs(approachSpeed), Math.abs(maxSpeed));
            }   

            // keep on target even when backing up
            if (approachSpeed < 0)
            {
                curvature = -curvature;
            }

            // calculate left/right motor speeds for this approach speed & curvature
            wheelSpeed = Kinematics.inverseKinematicsFromSpeedCurvature(approachSpeed, curvature);

            if (enabled)
            {
                // adjust drive command
                driveCmd.setMotors(wheelSpeed);
            }
        }
        else
        {
            // set some default values for logging
            distanceToTargetInches = 0.0;
            bearingToTarget = 0.0;
            lookaheadDist = kLookaheadDist;	
            curvature     = 0.0;		
            maxSpeed = 0.0;   
            joystickSpeed = driveCmd.getSpeed(); 
            approachSpeed = joystickSpeed;
        }

		//---------------------------------------------------
		// Output: Send drive control
		//---------------------------------------------------
        return driveCmd;
    }
    

    double prevTime = 0.0;
    double prevSpeed = 0.0;
    // keep speed within acceleration limits
	public double calcSpeedLimit(double _currentTime, double _remainingDistance, double _maxSpeed, double _maxAccel)
	{
		//---------------------------------------------------
		// Apply speed control
		//---------------------------------------------------
		double speed = _maxSpeed;
		
		double dt = _currentTime - prevTime;
		
		// apply acceleration limits
		double accel = (speed - prevSpeed) / dt;
		if (accel > _maxAccel)
			speed = prevSpeed + _maxAccel * dt;
		else if (accel < -_maxAccel)
			speed = prevSpeed - _maxAccel * dt;

		// apply braking distance limits
		// vf^2 = v^2 + 2*a*d   Solve for v, given vf=0, configured a, and measured d
		double stoppingDistance = _remainingDistance;
		double maxBrakingSpeed = Math.sqrt(2.0 * _maxAccel * stoppingDistance);
		if (Math.abs(speed) > maxBrakingSpeed)
			speed = Math.signum(speed) * maxBrakingSpeed;

		// apply minimum velocity limit (so driver can slowly move past endpoint)
		final double kMinSpeed = 6.0;
		if (Math.abs(speed) < kMinSpeed) 
			speed = Math.signum(speed) * kMinSpeed;

		// store for next time through loop	
		prevTime = _currentTime;
        prevSpeed = speed;
        
        return speed;
    }
    

    private final DataLogger logger = new DataLogger()
	{
		@Override
		public void log()
		{
            put("VisionDriveAssist/enabled", enabled);
            put("VisionDriveAssist/foundTarget", haveGoal);
            put("VisionDriveAssist/kTargetDistanceThresholdFromCameraInches", kTargetDistanceThresholdFromCenterInches);
            put("VisionDriveAssist/distanceToTargetInches", distanceToTargetInches);
            put("VisionDriveAssist/bearingToTarget", bearingToTarget);
            put("VisionDriveAssist/lookaheadDist", lookaheadDist);
            put("VisionDriveAssist/curvature", curvature);
            put("VisionDriveAssist/joystickSpeed", joystickSpeed);
            put("VisionDriveAssist/approachSpeed", approachSpeed);
            put("VisionDriveAssist/maxSpeed", maxSpeed);
            put("VisionDriveAssist/leftWheelSpeed", wheelSpeed.left);
            put("VisionDriveAssist/rightWheelSpeed", wheelSpeed.right);

			if (currentFieldToGoal.isPresent())
			{
				Vector2d target = currentFieldToGoal.get();
                put("VisionDriveAssist/currentGoal/X", target.getX());
                put("VisionDriveAssist/currentGoal/Y", target.getY());
            }
			else
			{
                put("VisionDriveAssist/currentGoal/X", -999);
                put("VisionDriveAssist/currentGoal/Y", -999);
            }

        }
    };

	public DataLogger getLogger()
	{
		return logger;
	}
}