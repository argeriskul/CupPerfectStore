package ru.hilariousstartups.javaskills.psplayer;

import lombok.extern.slf4j.Slf4j;
import ru.hilariousstartups.javaskills.psplayer.swagger_codegen.model.Employee;
import ru.hilariousstartups.javaskills.psplayer.swagger_codegen.model.EmployeeRecruitmentOffer;
import ru.hilariousstartups.javaskills.psplayer.swagger_codegen.model.HireEmployeeCommand;

import java.util.ArrayList;
import java.util.List;

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
        List<HireEmployeeCommand> result = new ArrayList<>(recruitmentAgency.size());
        for (int i = 0; i < 3 && i < recruitmentAgency.size(); i++) {
            var offer = recruitmentAgency.get(i);
            result.add(new HireEmployeeCommand()
                    .experience(HireEmployeeCommand.ExperienceEnum.fromValue(offer.getEmployeeType()))

            );

        }
        // TODO получать реальный id
        result.get(0).checkoutLineId(1);
        log.debug("Всего предложений:" + recruitmentAgency.size());
        log.debug(recruitmentAgency.toString());
        log.debug(result.toString());
        return result;
    }
}
