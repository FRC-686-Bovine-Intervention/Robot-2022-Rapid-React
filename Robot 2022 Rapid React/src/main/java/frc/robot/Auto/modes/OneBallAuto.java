package frc.robot.auto.modes;

import java.util.Arrays;

import frc.robot.auto.AutoManager;
import frc.robot.auto.AutoModeEndedException;
import frc.robot.auto.actions.ParallelAction;
import frc.robot.auto.actions.PathFollowerAction;
import frc.robot.auto.actions.SetIntakeAction;
import frc.robot.auto.actions.WaitAction;
import frc.robot.command_status.RobotState;
import frc.robot.lib.util.Path;
import frc.robot.lib.util.Path.Waypoint;
import frc.robot.lib.util.PathSegment.Options;
import frc.robot.lib.util.Pose;
import frc.robot.lib.util.Vector2d;
import frc.robot.subsystems.Intake.IntakeState;

public class OneBallAuto extends AutoMode{
    public OneBallAuto(){
        initialPose = new Pose();
    }

    @Override
    protected void routine() throws AutoModeEndedException {
        
        double maxSpeed = 36;
        double accel = 36;
        double lookaheadDist = 24;
        boolean visionEnabled = false;
        Options driveOptions = new Options(maxSpeed, accel, lookaheadDist, visionEnabled);

        double shotTime = 0.5;

        Vector2d initialPos = initialPose.getPosition();
        Vector2d outsideTarmac = initialPos.add(new Vector2d(-120,0));
        
        Path reversePath = new Path();
        reversePath.add(new Waypoint(initialPos, driveOptions));
        reversePath.add(new Waypoint(outsideTarmac, driveOptions));
        reversePath.setReverseDirection();



        //================================================================
        // ONE BALL AUTO
        //================================================================
        RobotState.getInstance().reset(initialPose);
        runAction(new WaitAction(AutoManager.autoInitialDelaySec)); 

        runAction(new SetIntakeAction(IntakeState.OUTTAKE));
        runAction(new WaitAction(shotTime));
        runAction(new ParallelAction(Arrays.asList(new SetIntakeAction(IntakeState.DEFENSE), new PathFollowerAction(reversePath))));
    }
}
