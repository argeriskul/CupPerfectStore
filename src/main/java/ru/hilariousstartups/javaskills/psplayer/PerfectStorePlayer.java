package ru.hilariousstartups.javaskills.psplayer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import ru.hilariousstartups.javaskills.psplayer.swagger_codegen.ApiClient;
import ru.hilariousstartups.javaskills.psplayer.swagger_codegen.ApiException;
import ru.hilariousstartups.javaskills.psplayer.swagger_codegen.api.PerfectStoreEndpointApi;
import ru.hilariousstartups.javaskills.psplayer.swagger_codegen.model.*;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class PerfectStorePlayer implements ApplicationListener<ApplicationReadyEvent> {

    private String serverUrl;

    private Set<Customer> customersOnCheckline = new HashSet<>();
    private Set<Customer> awaitingCustomers = new HashSet<>();
    private Map<Integer, ProductInBasket> basketProducts = new HashMap<>(); // productId->product
    private HRDepartment hrDepartment;
    private CheckoutAdmin checkoutAdmin;
    private Merchandaizer merch;

    public PerfectStorePlayer(@Value("${rs.endpoint:http://localhost:9080}") String serverUrl) {
        this.serverUrl = serverUrl;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent applicationReadyEvent) {
        ApiClient apiClient = new ApiClient();
        apiClient.setBasePath(serverUrl);

        PerfectStoreEndpointApi psApiClient = new PerfectStoreEndpointApi(apiClient);

        log.debug("Игрок готов. Подключаемся к серверу..");
        awaitServer(psApiClient);

        log.info("Подключение к серверу успешно. Начинаем игру");
        CurrentWorldResponse currentWorldResponse = null;
        try {
            int cnt = 0;
            do {
                cnt += 1;
                if (cnt % (60 * 4) == 0) {
                    log.info("Пройден " + cnt + " тик");
//                    log.info("Ответ  с предыдущего шага="+currentWorldResponse);
                }

                CurrentTickRequest request = createNextMove(currentWorldResponse);
                if (cnt > 1 && cnt <= 3) {
                    log.info(request.getBuyStockCommands().toString());
                    log.info(request.getPutOnRackCellCommands().toString());
                }

                if (currentWorldResponse == null) {
                    currentWorldResponse = psApiClient.loadWorld();
                    // logging world
                    log.debug("Total employees=" + currentWorldResponse.getEmployees().size());
                    log.debug("Кассы" + currentWorldResponse.getCheckoutLines());
                    var productAssortment = currentWorldResponse.getStock().size();
                    var productCountList = currentWorldResponse.getStock().
                            stream().sorted(Comparator.comparingDouble(Product::getStockPrice))
                            .map(Product::getInStock).collect(Collectors.toList());
                    log.debug("Видов товаров=" + productAssortment + ", штук=" + productCountList +
                            ", стоит=" + currentWorldResponse.getStockCosts());
                    log.debug("Полок=" + currentWorldResponse.getRackCells().size());
                    // end of logging

                    // init state
                    hrDepartment = new HRDepartment(currentWorldResponse.getRecruitmentAgency(),
                            currentWorldResponse.getEmployees(),
                            currentWorldResponse.getCheckoutLines().size()
                    );
                    checkoutAdmin = new CheckoutAdmin(currentWorldResponse.getEmployees());
                    merch = new Merchandaizer(currentWorldResponse.getRackCells(),
                            currentWorldResponse.getStock());

                    // generate initial commands
                    request = new CurrentTickRequest();
                    request.hireEmployeeCommands(hrDepartment.firstHire());
                    request.buyStockCommands(merch.initialBuyIn());
                }

                currentWorldResponse = psApiClient.tick(new CurrentTickRequest());
                collectDataFromAnswer(currentWorldResponse);

            }
            while (!currentWorldResponse.isGameOver());

            // Если пришел Game Over, значит все время игры закончилось. Пора считать прибыль
            log.info("Я заработал " + (currentWorldResponse.getIncome() - currentWorldResponse.getSalaryCosts() - currentWorldResponse.getStockCosts()) + "руб.");
            log.info(currentWorldResponse.getStock().toString());
            log.info(currentWorldResponse.getRackCells().toString());
            log.info("В корзинах:" + basketProducts.values());
            log.info("Sold products count=" + basketProducts.size());
            var awaitingCheckoutCustomersCount = currentWorldResponse.getCustomers().stream().
                    filter(it -> it.getMode().equals(Customer.ModeEnum.WAIT_CHECKOUT)).count();
            var atCheckoutCustomersCount = currentWorldResponse.getCustomers().stream().
                    filter(it -> it.getMode().equals(Customer.ModeEnum.AT_CHECKOUT)).count();
            var inHallCustomersCount = currentWorldResponse.getCustomers().stream().
                    filter(it -> it.getMode().equals(Customer.ModeEnum.IN_HALL)).count();
            log.info(String.format("Customers entered to checkline=%s, at checkout=%s, awaiting=%s, in hall=%s, total=%s",
                    customersOnCheckline.size(),
                    awaitingCheckoutCustomersCount,
                    atCheckoutCustomersCount,
                    inHallCustomersCount,
                    currentWorldResponse.getCustomers().size()
            ));

        } catch (ApiException | RuntimeException e) {
            log.error(e.getMessage(), e);
            closeStore(psApiClient, currentWorldResponse);
        }

    }

    private void closeStore(PerfectStoreEndpointApi psApiClient, CurrentWorldResponse currentWorldResponse) {
        CurrentTickRequest request = new CurrentTickRequest();
        do {
            try {
                currentWorldResponse = psApiClient.tick(request);
            } catch (ApiException e) {
                log.error("Failed to process empty request: " + e.getMessage());
            }
        } while (!currentWorldResponse.isGameOver());
    }

    private void collectDataFromAnswer(CurrentWorldResponse worldResponse) {
        var customers = worldResponse.getCustomers();
        var awaitingCheckoutCustomers = customers.stream().
                filter(it -> it.getMode().equals(Customer.ModeEnum.WAIT_CHECKOUT)).
                collect(Collectors.toList());
        var atCheckoutCustomers = customers.stream().
                filter(it -> it.getMode().equals(Customer.ModeEnum.AT_CHECKOUT)).
                collect(Collectors.toList());
        var newAwaitingCheckoutProducts = awaitingCheckoutCustomers.stream().
                filter(it -> !awaitingCustomers.contains(it)).
                map(it -> it.getBasket()).
                collect(ArrayList<ProductInBasket>::new, List::addAll, List::addAll);
        for (ProductInBasket product : newAwaitingCheckoutProducts) {
            if (basketProducts.containsKey(product.getId())) {
                var countedProduct = basketProducts.get(product.getId());
                countedProduct.setProductCount(countedProduct.getProductCount() + product.getProductCount());
            } else {
                basketProducts.put(product.getId(), product);
            }
        }
        awaitingCustomers.addAll(awaitingCheckoutCustomers);
        customersOnCheckline.addAll(atCheckoutCustomers);

    }

    private CurrentTickRequest createNextMove(CurrentWorldResponse worldResponse) {
        if (worldResponse == null) { // первый ход обрабатывается отдельно
            return null;
        }
        CurrentTickRequest request = new CurrentTickRequest();

        checkoutAdmin.considerNew(worldResponse.getEmployees(),
                worldResponse.getCheckoutLines(),
                worldResponse.getCurrentTick());
        inspectChecklines(worldResponse, request);

        // готовимся закупать товар на склад и выставлять его на полки
        final List<PutOnRackCellCommand> putOnRackCellCommands = merch.inspectRacks(worldResponse);
        final List<BuyStockCommand> buyStockCommands = merch.inspectStore();
        final List<PutOffRackCellCommand> putOffRackCellCommands = merch.removeSold();
        request.setPutOffRackCellCommands(putOffRackCellCommands);
        request.setPutOnRackCellCommands(putOnRackCellCommands);
        request.buyStockCommands(buyStockCommands);
        System.out.println("balbla");
        return request;
    }

    /**
     * Здесь будет вся работа по управлению кассами: слежение за свободными кассами, ротация кассиров
     *
     * @param worldResponse
     * @param request
     */
    private void inspectChecklines(CurrentWorldResponse worldResponse, CurrentTickRequest request) {
        // Стратегия "одна работающая касса", кассиры не увольняются
        var singleCheckline = worldResponse.getCheckoutLines().get(0);
        if (singleCheckline.getEmployeeId() == null) {
            var employee = checkoutAdmin.sendToWorkAny(worldResponse.getCurrentTick());
            if (employee != null) {
                request.addSetOnCheckoutLineCommandsItem(
                        new SetOnCheckoutLineCommand().checkoutLineId(singleCheckline.getId()).
                                employeeId(employee.getId())
                );
            }
        }

    }

    private void awaitServer(PerfectStoreEndpointApi psApiClient) {
        int awaitTimes = 60;
        int cnt = 0;
        boolean serverReady = false;
        do {
            try {
                cnt += 1;
                psApiClient.loadWorld();
                serverReady = true;

            } catch (ApiException e) {
                try {
                    Thread.currentThread().sleep(1000L);
                } catch (InterruptedException interruptedException) {
                    e.printStackTrace();
                }
            }
        } while (!serverReady && cnt < awaitTimes);
    }


}
