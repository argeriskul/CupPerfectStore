package ru.hilariousstartups.javaskills.psplayer;

import lombok.extern.slf4j.Slf4j;
import ru.hilariousstartups.javaskills.psplayer.swagger_codegen.model.CheckoutLine;
import ru.hilariousstartups.javaskills.psplayer.swagger_codegen.model.Employee;

import java.util.ArrayList;
import java.util.List;

/**
 * Управляет кассирами на кассах: следит, кто когда ушел отдыхать,
 * когда может работать снова, кого именно сажать на кассу.
 * Используется в стратегии "не увольнять сразу"
 */
@Slf4j
public class CheckoutAdmin {

    private List<Employee> employees;
    private List<EmployeeWorkTable> timesheet;
    private boolean notFailed = false;

    public CheckoutAdmin(List<Employee> employees) {
        this.employees = new ArrayList<>(employees);
        init();
    }

    private void init() {
        if (employees == null || employees.isEmpty()) {
            timesheet = new ArrayList<>();
            return;
        }
        timesheet = new ArrayList<>(employees.size());
        employees.forEach(it -> timesheet.add(new EmployeeWorkTable(it)));
    }

    public Employee sendToWorkAny(int currentTime) {
        var candidate = timesheet.stream()
                .filter(it -> it.isReadyToWork(currentTime))
                .findAny().orElse(null);
        if (candidate == null) {
            if (notFailed || currentTime % 120 == 0) {
                log.warn("Not enough employees to work at=" + currentTime + ", " + timesheet);
            }
            notFailed = false;
            return null;
        }
        notFailed = true;
        candidate.startWork(currentTime);
        return candidate.getEmployee();
    }

    // TODO add tests
    // FIXME учесть уволенных
    public void considerNew(List<Employee> employeeList,
                            List<CheckoutLine> checkoutLines,
                            Integer tickCount) {
        var newWorkers = new ArrayList<>(employeeList);
        newWorkers.removeAll(employees);
        employees.addAll(newWorkers);
        for (Employee newbie : newWorkers) {
            var alreadyAssigned = checkoutLines.stream()
                    .anyMatch(line -> newbie.getId().equals(line.getEmployeeId()));
            final EmployeeWorkTable newbieWorkTable = new EmployeeWorkTable(newbie);
            if (alreadyAssigned) {
                newbieWorkTable.startWork(tickCount);
            }
            timesheet.add(newbieWorkTable);
            log.info(newbieWorkTable.toString());
        }
    }

    public List<Employee> getEmployees() {
        return employees;
    }

    public List<EmployeeWorkTable> getTimesheet() {
        return timesheet;
    }
}
