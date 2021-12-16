package ru.hilariousstartups.javaskills.psplayer;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import ru.hilariousstartups.javaskills.psplayer.swagger_codegen.model.Employee;

class EmployeeWorkTable {
    private final static int HOUR_IN_TICKS = 60;
    private final static int WORK_SHIFT_IN_TICKS = 8 * HOUR_IN_TICKS;
    private final static int REST_TIME_IN_TICKS = 16 * HOUR_IN_TICKS;

    @NonNull
    private Employee employee;
    private Integer startedWork = null;

    public EmployeeWorkTable(Employee employee) {
        this.employee = employee;
    }

    // probably change return type
    public boolean startWork(int currentTime) {
        if (isReadyToWork(currentTime)) {
            startedWork = currentTime;
            return true;
        }
        return false;
    }

    /**
     * @return time (in ticks) when this employee started his last work shift.
     * Null if it never works.
     */
    @Nullable
    public Integer getStartedWork() {
        return startedWork;
    }

    @NonNull
    public Employee getEmployee() {
        return employee;
    }

    /**
     * @return time (in ticks) when this employee will finish his work shift.
     * Null if it never works
     */
    @Nullable
    public Integer getFinishedWork() {
        if (startedWork == null) {
            return null;
        }
        return startedWork + WORK_SHIFT_IN_TICKS;
    }

    /**
     * @return time (in ticks) when this employee could start to work.
     * Null if it never works and could start immediately
     */
    @Nullable
    public Integer getReadyToWork() {
        if (startedWork == null) {
            return null;
        }
        return getFinishedWork() + REST_TIME_IN_TICKS;
    }

    public boolean isReadyToWork(int currentTime) {
        if (startedWork == null) {
            return true;
        }
        return getReadyToWork() <= currentTime;
    }

    public static int getWorkShiftInTicks() {
        return WORK_SHIFT_IN_TICKS;
    }

    public static int getRestTimeInTicks() {
        return REST_TIME_IN_TICKS;
    }

    public static int getHourInTicks() {
        return HOUR_IN_TICKS;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("EmployeeWorkTable{");
        sb.append("employee(").append(employee.getId()).append(")");
        sb.append(" ").append(employee.getFirstName());
        sb.append(", exp=").append(employee.getExperience());
        sb.append(", $").append(employee.getSalary());
        sb.append(", started=").append(startedWork);
        sb.append('}');
        return sb.toString();
    }
}
