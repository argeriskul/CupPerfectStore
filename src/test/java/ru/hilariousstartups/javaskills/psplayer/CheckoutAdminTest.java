package ru.hilariousstartups.javaskills.psplayer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.hilariousstartups.javaskills.psplayer.swagger_codegen.model.CheckoutLine;
import ru.hilariousstartups.javaskills.psplayer.swagger_codegen.model.Employee;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static ru.hilariousstartups.javaskills.psplayer.EmployeeWorkTable.getRestTimeInTicks;
import static ru.hilariousstartups.javaskills.psplayer.EmployeeWorkTable.getWorkShiftInTicks;

public class CheckoutAdminTest {

    private final static List<Employee> EMPLOYEE_LIST = Arrays.asList(
            new Employee().id(1).experience(10).firstName("E1").salary(100),
            new Employee().id(2).experience(40).firstName("E2").salary(300),
            new Employee().id(3).experience(70).firstName("E3").salary(560)

    );

    private CheckoutAdmin service = new CheckoutAdmin(EMPLOYEE_LIST);

    @BeforeEach
    public void setup() {
        System.out.println("---next test------");
    }

    @Test
    public void addNewEmployee() {
        var newbie = new Employee().id(10).experience(15).firstName("new").salary(250);
        var employees = new ArrayList<>(EMPLOYEE_LIST);
        employees.add(newbie);
        service.considerNew(employees, Collections.emptyList(), 1);
        final int elementsCount = employees.size();
        assertEquals(elementsCount, service.getEmployees().size());
        assertEquals(elementsCount, service.getTimesheet().size());
        final EmployeeWorkTable actual = service.getTimesheet().get(elementsCount - 1);
        assertEquals(newbie, actual.getEmployee());
        assertNull(actual.getStartedWork());
        assertNull(actual.getFinishedWork());
        assertNull(actual.getReadyToWork());
    }

    @Test
    public void addWorkingEmployee() {
        final int employeeId = 10;
        var newbie = new Employee().id(employeeId).experience(15)
                .firstName("new").salary(250);
        var employees = new ArrayList<>(EMPLOYEE_LIST);
        employees.add(newbie);
        List<CheckoutLine> checkouts = Arrays.asList(new CheckoutLine().employeeId(employeeId));
        final int hireTime = 1;
        service.considerNew(employees, checkouts, hireTime);
        final int elementsCount = employees.size();
        assertEquals(elementsCount, service.getEmployees().size());
        assertEquals(elementsCount, service.getTimesheet().size());
        final EmployeeWorkTable actual = service.getTimesheet().get(elementsCount - 1);
        assertEquals(newbie, actual.getEmployee());
        assertEquals(hireTime, actual.getStartedWork());
        assertEquals(hireTime + getWorkShiftInTicks(), actual.getFinishedWork());
        assertEquals(hireTime + getWorkShiftInTicks() + getRestTimeInTicks(),
                actual.getReadyToWork());
    }

    @Test
    public void workersPerDay() {
        int minutesInDay = 60 * 24;
        assertEquals(minutesInDay, getWorkShiftInTicks() + getRestTimeInTicks());
    }

    @Test
    void assignNextEmployeeToWork() {
        final int firstShift = 2;
        final int secondShift = firstShift + getWorkShiftInTicks();
        final int thirdShift = firstShift + 2 * getWorkShiftInTicks();
        final int fourthShift = firstShift + 3 * getWorkShiftInTicks();
        var first = service.sendToWorkAny(firstShift);
        System.out.println("after first " + service.getTimesheet());
        var second = service.sendToWorkAny(secondShift);
        System.out.println("after second = " + service.getTimesheet());
        var third = service.sendToWorkAny(thirdShift);
        System.out.println("after third = " + service.getTimesheet());

        var fourth = service.sendToWorkAny(fourthShift);
        assertEquals(first, fourth);
    }
}
