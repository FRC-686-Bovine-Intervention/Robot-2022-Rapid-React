package frc.robot.Subsystems.Subsystems;

import com.ctre.phoenix.motorcontrol.NeutralMode;
import com.ctre.phoenix.motorcontrol.TalonFXControlMode;
import com.ctre.phoenix.motorcontrol.TalonFXInvertType;
import com.ctre.phoenix.motorcontrol.VictorSPXControlMode;
import com.ctre.phoenix.motorcontrol.can.TalonFX;
import com.ctre.phoenix.motorcontrol.can.VictorSPX;

import edu.wpi.first.networktables.NetworkTableEntry;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.shuffleboard.BuiltInWidgets;
import edu.wpi.first.wpilibj.shuffleboard.Shuffleboard;
import edu.wpi.first.wpilibj.shuffleboard.ShuffleboardTab;
import edu.wpi.first.wpilibj.smartdashboard.SendableChooser;
import frc.robot.Constants;
import frc.robot.Subsystems.Subsystem;

/**<h4>Contains all code for the Intake subsystem</h4>*/
public class Intake extends Subsystem {
    private static Intake instance = null;
    public static Intake getInstance() {if(instance == null){instance = new Intake();}return instance;}
    
    private TalonFX ArmMotor;
    private VictorSPX RollerMotor;


    //Constants for controlling the arm. consider tuning these for your particular robot
    final double armHoldUp = 0.08;
    final double armHoldDown = 0.13;
    final double armTravel = 0.5;

    final double armTimeUp = 0.5;
    final double armTimeDown = 0.35;

    //Varibles needed for the code
    boolean burstMode = false;
    double lastBurstTime = 0;


    private Intake()
    {
        ArmMotor = new TalonFX(Constants.kArmMotorID);
        RollerMotor = new VictorSPX(Constants.kRollerMotorID);
    
        ArmMotor.configFactoryDefault();
        ArmMotor.setInverted(TalonFXInvertType.CounterClockwise);
        ArmMotor.setNeutralMode(NeutralMode.Brake);
    
        intakeStatus = IntakeState.DEFENSE;
        calibrated = false;
    }
    
    public enum ArmPosEnum {
        LOWERED(0),
        RAISED(35236);

        public final double angleDeg;
        ArmPosEnum(double angleDeg) {this.angleDeg = angleDeg;}
    }

    public ArmPosEnum targetPos = ArmPosEnum.RAISED;
    public double currentPos;

    public enum IntakeState {
        DEFENSE,
        INTAKE,
        OUTTAKE,
        CLIMBING,
        CALIBRATING
    }
    public IntakeState intakeStatus;

    @Override
    public void run()
    {
        if(autoCalibrate && !calibrated) {changeState(IntakeState.CALIBRATING);}
        switch (intakeStatus)
        {
            case DEFENSE: default:
                RollerMotor.set(VictorSPXControlMode.PercentOutput, 0);
                if(targetPos != ArmPosEnum.RAISED)
                {
                    lastBurstTime = Timer.getFPGATimestamp();
                    setTargetPos(ArmPosEnum.RAISED);
                }
            break;
            case INTAKE:
                if (isAtPos(ArmPosEnum.LOWERED)) {RollerMotor.set(VictorSPXControlMode.PercentOutput, 0.7);}
                    if(targetPos != ArmPosEnum.LOWERED)
                    {
                        lastBurstTime = Timer.getFPGATimestamp();
                        setTargetPos(ArmPosEnum.LOWERED);
                    }
            break;
            case OUTTAKE:
                if (isAtPos(ArmPosEnum.RAISED)) {RollerMotor.set(VictorSPXControlMode.PercentOutput, -0.9);}
                    if(targetPos != ArmPosEnum.RAISED)
                    {
                        lastBurstTime = Timer.getFPGATimestamp();
                        setTargetPos(ArmPosEnum.RAISED);
                    }
            break;
            case CLIMBING: break;
            case CALIBRATING: calibrated = true; changeState(IntakeState.DEFENSE);/*
                if (ArmMotor.getStatorCurrent() > 5)
                {
                    ArmMotor.set(TalonFXControlMode.PercentOutput, 0);
                    changeState(IntakeState.DEFENSE);
                    calibrated = true;
                    break;
                }
                ArmMotor.set(TalonFXControlMode.PercentOutput, 0.2);*/
            break;
        }
        if (intakeStatus != IntakeState.CALIBRATING) setPos(targetPos);
    }

    @Override
    public void runTestMode()
    {
        intakeStatus = stateChooser.getSelected();
        if (stateChooser.getSelected() == IntakeState.CALIBRATING || calibrateEntry.getBoolean(false))
        {
            runCalibration();
            calibrateEntry.setBoolean(false);
        }
        autoCalibrate = false;
        run();
        autoCalibrate = true;
    }
    @Override
    public void runCalibration()
    {
        calibrated = false;
        changeState(IntakeState.CALIBRATING);
    }

    /**
     * @param pos is the desired pos
     * @param error is the radius of error
     * @return if the arm is within the radius of error from the desired pos
     */
    public boolean isAtPos(ArmPosEnum pos, double error) {return ((currentPos >= pos.angleDeg - error)&&(currentPos <= pos.angleDeg + error));}
    public boolean isAtPos(ArmPosEnum pos) {return true;/*return isAtPos(pos, 4);*/}

    public void setTargetPos(ArmPosEnum pos) {targetPos = pos;}

    private void setPos(ArmPosEnum pos)
    {
        if(pos == ArmPosEnum.RAISED){
            if(Timer.getFPGATimestamp() - lastBurstTime < armTimeUp){
                ArmMotor.set(TalonFXControlMode.PercentOutput, armTravel);
            }
            else{
                ArmMotor.set(TalonFXControlMode.PercentOutput, armHoldUp);
            }
        }
        else{
            if(Timer.getFPGATimestamp() - lastBurstTime < armTimeDown){
                ArmMotor.set(TalonFXControlMode.PercentOutput, -armTravel);
            }
            else{
                ArmMotor.set(TalonFXControlMode.PercentOutput, -armHoldDown);
            }
        }
    }

    public void changeState(IntakeState newState)
    {
        intakeStatus = newState;
    }

    private ShuffleboardTab tab = Shuffleboard.getTab("Intake");
    private NetworkTableEntry calibrateEntry = tab.add("Calibrate", false).withWidget(BuiltInWidgets.kToggleButton).getEntry();
    private NetworkTableEntry enableEntry = tab.add("Enable", true).withWidget(BuiltInWidgets.kToggleSwitch).getEntry();
    private NetworkTableEntry statusEntry = tab.add("Status", "not updating what").withWidget(BuiltInWidgets.kTextView).getEntry();
    private NetworkTableEntry armposEntry = tab.add("Arm position", "not updating what").withWidget(BuiltInWidgets.kTextView).getEntry();
    private SendableChooser<IntakeState> stateChooser = new SendableChooser<>();

    @Override
    public void updateShuffleboard()
    {
        Enabled = enableEntry.getBoolean(true);
        enableEntry.setBoolean(Enabled);
        statusEntry.setString(intakeStatus.name());
        armposEntry.setString(targetPos.name());
    }
}