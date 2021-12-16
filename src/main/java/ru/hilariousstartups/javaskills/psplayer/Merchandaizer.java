package ru.hilariousstartups.javaskills.psplayer;

import lombok.extern.slf4j.Slf4j;
import ru.hilariousstartups.javaskills.psplayer.swagger_codegen.model.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Выкладывает товар на полки, определяет наценки.
 * Будет ли заниматься заказом товара - пока не знаю
 */
@Slf4j
public class Merchandaizer {

    public enum Margin {
        DOLLAR_ONE, PERCENT_10, PERCENT_20, PERCENT_50, PERCENT_100;

        public Margin next() {
            switch (this) {
                case DOLLAR_ONE:
                    return PERCENT_10;
                case PERCENT_10:
                    return PERCENT_20;
                case PERCENT_20:
                    return PERCENT_50;
                case PERCENT_50:
                    return PERCENT_100;
                case PERCENT_100:
                    return PERCENT_100;
                default:
                    return DOLLAR_ONE; // never happens
            }
        }
    }

    private static final Integer DEFAULT_QUANTITY = 100;
    private List<RackCell> racks;
    private List<Product> stock;
    private List<Integer> productsOnRack;
    private List<Integer> racksToPutOff;

    public Merchandaizer(List<RackCell> racks, List<Product> stock) {
        this.racks = new ArrayList<>(racks);
        this.stock = new ArrayList<>(stock);
        this.stock.sort(Comparator.comparing(Product::getStockPrice));
    }

    public List<PutOffRackCellCommand> removeSold() {
        var result = racksToPutOff.stream().map(id ->
                new PutOffRackCellCommand().rackCellId(id)
        ).collect(Collectors.toList());
        return result;
    }

    public List<PutOnRackCellCommand> inspectRacks(CurrentWorldResponse world) {
        racksToPutOff = new ArrayList<>();
        racks = world.getRackCells();
        stock = world.getStock();
        stock.sort(Comparator.comparing(Product::getStockPrice).reversed());
        productsOnRack = racks.stream()
                .filter(r -> r.getProductId() != null)
                .map(RackCell::getProductId)
                .collect(Collectors.toList());

        List<PutOnRackCellCommand> result = new ArrayList<>();

        racks.stream()
                .filter(rack -> rack.getProductId() == null || rack.getProductQuantity().equals(0))
                .forEach(rack -> {
                    PutOnRackCellCommand command;
                    if (rack.getProductId() == null) {
                        command = putNextFromStock(rack);
                    } else {
                        command = addSame(rack);
                    }
                    result.add(command);
                });
        return result;
    }

    private PutOnRackCellCommand addSame(RackCell rack) {
        Product productToPutOnRack = stock.stream()
                .filter(it -> it.getId().equals(rack.getProductId()))
                .findAny()
                .orElseThrow(() -> new IllegalStateException("Product is lost on stock:" + rack.getProductId()));

        final Integer inStock = productToPutOnRack.getInStock();
        int rackCapacity = rack.getCapacity();
        double sellPrice = definePrice(productToPutOnRack);
        return new PutOnRackCellCommand().productId(productToPutOnRack.getId())
                .rackCellId(rack.getId())
                .productQuantity(Math.min(inStock, rackCapacity))
                .sellPrice(sellPrice);
    }


    private PutOnRackCellCommand putNextFromStock(RackCell rack) {
        Product productToPutOnRack;
        productToPutOnRack = stock.stream()
                .filter(product -> !productsOnRack.contains(product.getId())
                        && product.getInStock() > 0)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Store is empty"));
        productsOnRack.add(productToPutOnRack.getId());
        racksToPutOff.add(rack.getId());
        final Integer inStock = productToPutOnRack.getInStock();
        int rackCapacity = rack.getCapacity();
        double sellPrice = definePrice(productToPutOnRack);
        return new PutOnRackCellCommand().productId(productToPutOnRack.getId())
                .rackCellId(rack.getId())
                .productQuantity(Math.min(inStock, rackCapacity))
                .sellPrice(sellPrice);
    }

    private double definePrice(Product product) {
        if (product.getSellPrice() == null) {
            return product.getStockPrice() + 1;
        }
        return product.getSellPrice() * 1.1; // add 10%
    }

    public List<BuyStockCommand> inspectStore() {
        List<BuyStockCommand> result = new ArrayList<>();
        stock.stream().filter(it -> it.getInStock().equals(0))
                .forEach(product -> result.add(new BuyStockCommand()
                        .productId(product.getId())
                        .quantity(DEFAULT_QUANTITY)
                ));
        if (result.size() > 5) {
            return result;
        } else {
            return Collections.emptyList();// избегаем мелких закупок
        }
    }

    public List<BuyStockCommand> initialBuyIn() {
        List<BuyStockCommand> buyStockCommands = new ArrayList<>(50);
        stock.forEach(it -> buyStockCommands.add(
                new BuyStockCommand().productId(it.getId()).quantity(DEFAULT_QUANTITY)
        ));
        return buyStockCommands;
    }
}
