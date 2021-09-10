package ru.hilariousstartups.javaskills.psplayer;

import lombok.extern.slf4j.Slf4j;
import ru.hilariousstartups.javaskills.psplayer.swagger_codegen.model.Employee;
import ru.hilariousstartups.javaskills.psplayer.swagger_codegen.model.EmployeeRecruitmentOffer;
import ru.hilariousstartups.javaskills.psplayer.swagger_codegen.model.HireEmployeeCommand;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Отвечает за найим и увольнение
 */
@Slf4j
public class HRDepartment {

    private List<EmployeeRecruitmentOffer> recruitmentAgency;
    private List<Employee> employees;
    private int checkoutCount; // сколько всего касс

    public HRDepartment(List<EmployeeRecruitmentOffer> recruitmentAgency, List<Employee> employees, int checkoutCount) {
        this.recruitmentAgency = recruitmentAgency;
        this.employees = employees;
        this.checkoutCount = checkoutCount;
    }


    public List<EmployeeRecruitmentOffer> getRecruitmentAgency() {
        return recruitmentAgency;
    }

    public List<HireEmployeeCommand> firstHire() {
        List<HireEmployeeCommand> result = new ArrayList<>(checkoutCount);
        result.add(new HireEmployeeCommand()
                .experience(HireEmployeeCommand.ExperienceEnum.MIDDLE)
                // TODO получать реальный id
                .checkoutLineId(1)
        );
        log.info("Всего предложений:" + recruitmentAgency.size());
        AtomicInteger juniorsCount = new AtomicInteger();
        AtomicInteger middleCount = new AtomicInteger();
        AtomicInteger senjorCount = new AtomicInteger();
        recruitmentAgency.stream().forEach(it -> {
            if (it.getEmployeeType().equalsIgnoreCase("junior")) {
                juniorsCount.getAndIncrement();
            }
            if (it.getEmployeeType().equalsIgnoreCase("middle")) {
                middleCount.getAndIncrement();
            }
            if (it.getEmployeeType().equalsIgnoreCase("senior")) {
                senjorCount.getAndIncrement();
            }
        });
        log.info("Джунов=" + juniorsCount + ", midlde=" + middleCount + ", senior=" + senjorCount);
        return result;
    }
}
