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
    private Map<Integer, ProductInBasket> soldProducts = new HashMap<>(); // productId->product. product.inStock contains quantity

    public PerfectStorePlayer(@Value("${rs.endpoint:http://localhost:9080}") String serverUrl) {
        this.serverUrl = serverUrl;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent applicationReadyEvent) {
        ApiClient apiClient = new ApiClient();
        apiClient.setBasePath(serverUrl);

        PerfectStoreEndpointApi psApiClient = new PerfectStoreEndpointApi(apiClient);

        log.info("Игрок готов. Подключаемся к серверу..");
        awaitServer(psApiClient);

        log.info("Подключение к серверу успешно. Начинаем игру");
        try {
            CurrentWorldResponse currentWorldResponse = null;
            int cnt = 0;
            do {
                cnt += 1;
                if (cnt % 120 == 0) {
                    log.info("Пройден " + cnt + " тик");
//                    log.info("Ответ  с предыдущего шага="+currentWorldResponse);
                }

                if (currentWorldResponse == null) {
                    currentWorldResponse = psApiClient.loadWorld();
                }

                CurrentTickRequest request = createNextMove(currentWorldResponse);

                currentWorldResponse = psApiClient.tick(request);
                collectDataFromAnswer(currentWorldResponse);

            }
            while (!currentWorldResponse.isGameOver());

            // Если пришел Game Over, значит все время игры закончилось. Пора считать прибыль
            log.info("Я заработал " + (currentWorldResponse.getIncome() - currentWorldResponse.getSalaryCosts() - currentWorldResponse.getStockCosts()) + "руб.");
            log.info("Total ticks:" + cnt);
            log.info("Sold products(total" + soldProducts.size() + "):" + soldProducts);
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

        } catch (ApiException e) {
            log.error(e.getMessage(), e);
        }

    }

    private void collectDataFromAnswer(CurrentWorldResponse worldResponse) {
        var customers = worldResponse.getCustomers();
        var awaitingCheckoutCustomers = customers.stream().
                filter(it -> it.getMode().equals(Customer.ModeEnum.WAIT_CHECKOUT)).
                collect(Collectors.toList());
        var atCheckoutCustomers = customers.stream().
                filter(it -> it.getMode().equals(Customer.ModeEnum.AT_CHECKOUT)).
                collect(Collectors.toList());
        var awaitingCheckoutProducts = awaitingCheckoutCustomers.stream().
                map(it -> it.getBasket()).
                collect(ArrayList<ProductInBasket>::new, List::addAll, List::addAll);
        for (ProductInBasket product : awaitingCheckoutProducts) {
            if (soldProducts.containsKey(product.getId())) {
                var countedProduct = soldProducts.get(product.getId());
                countedProduct.setProductCount(countedProduct.getProductCount() + product.getProductCount());
            } else {
                soldProducts.put(product.getId(), product);
            }
        }
        customersOnCheckline.addAll(atCheckoutCustomers);

    }

    private CurrentTickRequest createNextMove(CurrentWorldResponse currentWorldResponse) {
        CurrentTickRequest request = new CurrentTickRequest();

        // TODO hire only if here are too many customers
//                List<HireEmployeeCommand> hireEmployeeCommands = new ArrayList<>();
//                request.setHireEmployeeCommands(hireEmployeeCommands);
        // Смотрим на каких кассах нет кассира (либо не был назначен, либо ушел с кассы отдыхать), нанимаем новых кассиров и ставим на эти кассы.
        // Нанимаем самых опытных!
//                currentWorldResponse.getCheckoutLines().stream().filter(line -> line.getEmployeeId() == null).forEach(line -> {
//                    HireEmployeeCommand hireEmployeeCommand = new HireEmployeeCommand();
//                    hireEmployeeCommand.setCheckoutLineId(line.getId());
//                    hireEmployeeCommand.setExperience(HireEmployeeCommand.ExperienceEnum.SENIOR);
//                    hireEmployeeCommands.add(hireEmployeeCommand);
//                });
//                request.setHireEmployeeCommands(hireEmployeeCommands);

        // готовимся закупать товар на склад и выставлять его на полки
        ArrayList<BuyStockCommand> buyStockCommands = new ArrayList<>();
        request.setBuyStockCommands(buyStockCommands);

        ArrayList<PutOnRackCellCommand> putOnRackCellCommands = new ArrayList<>();
        request.setPutOnRackCellCommands(putOnRackCellCommands);

        List<Product> stock = currentWorldResponse.getStock();
        List<RackCell> rackCells = currentWorldResponse.getRackCells();

        // Обходим торговый зал и смотрим какие полки пустые. Выставляем на них товар.
        currentWorldResponse.getRackCells().stream().filter(rack -> rack.getProductId() == null || rack.getProductQuantity().equals(0)).forEach(rack -> {
            Product producttoPutOnRack = null;
            if (rack.getProductId() == null) {
                List<Integer> productsOnRack = rackCells.stream().filter(r -> r.getProductId() != null).map(RackCell::getProductId).collect(Collectors.toList());
                productsOnRack.addAll(putOnRackCellCommands.stream().map(c -> c.getProductId()).collect(Collectors.toList()));
                producttoPutOnRack = stock.stream().filter(product -> !productsOnRack.contains(product.getId())).findFirst().orElse(null);
            } else {
                producttoPutOnRack = stock.stream().filter(product -> product.getId().equals(rack.getProductId())).findFirst().orElse(null);
            }

            Integer productQuantity = rack.getProductQuantity();
            if (productQuantity == null) {
                productQuantity = 0;
            }

            // Вначале закупим товар на склад. Каждый ход закупать товар накладно, но ведь это тестовый игрок.
            Integer orderQuantity = rack.getCapacity() - productQuantity;
            if (producttoPutOnRack.getInStock() < orderQuantity) {
                BuyStockCommand command = new BuyStockCommand();
                command.setProductId(producttoPutOnRack.getId());
                command.setQuantity(100);
                buyStockCommands.add(command);
            }

            // Далее разложим на полки. И сформируем цену.
            PutOnRackCellCommand command = new PutOnRackCellCommand();
            command.setProductId(producttoPutOnRack.getId());
            command.setRackCellId(rack.getId());
            command.setProductQuantity(orderQuantity);
            if (producttoPutOnRack.getSellPrice() == null) {
                final int margin = 1;
                command.setSellPrice(producttoPutOnRack.getStockPrice() + margin);
            }
            putOnRackCellCommands.add(command);

        });
        return request;
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
