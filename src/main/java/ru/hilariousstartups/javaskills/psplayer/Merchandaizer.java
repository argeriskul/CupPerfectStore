package ru.hilariousstartups.javaskills.psplayer;

import lombok.extern.slf4j.Slf4j;
import ru.hilariousstartups.javaskills.psplayer.swagger_codegen.model.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Выкладывает товар на полки, определяет наценки.
 * Будет ли заниматься заказом товара - пока не знаю
 */
@Slf4j
public class Merchandaizer {
    private static final Integer DEFAULT_QUANTITY = 500;
    private List<RackCell> racks;
    private List<Product> stock;
    private List<Integer> productsOnRack;

    public Merchandaizer(List<RackCell> racks, List<Product> stock) {
        this.racks = new ArrayList<>(racks);
        this.stock = new ArrayList<>(stock);
        log.info(stock.toString());
        this.stock.sort(Comparator.comparing(Product::getStockPrice));
    }

    public List<PutOnRackCellCommand> inspectRacks(CurrentWorldResponse world) {

        racks = world.getRackCells();
        stock = world.getStock();
        stock.sort(Comparator.comparing(Product::getStockPrice));
        productsOnRack = racks.stream()
                .filter(r -> r.getProductId() != null)
                .map(RackCell::getProductId)
                .collect(Collectors.toList());

        List<PutOnRackCellCommand> result = new ArrayList<>();

        racks.stream()
                .filter(rack -> rack.getProductId() == null || rack.getProductQuantity().equals(0))
                .forEach(rack -> {
                    final PutOnRackCellCommand command = putNextFromStock(rack);
                    result.add(command);
                });

        return result;
    }

    public List<BuyStockCommand> inspectStore() {
        List<BuyStockCommand> result = new ArrayList<>();
        stock.stream().filter(it -> it.getInStock().equals(0))
                .forEach(product -> result.add(new BuyStockCommand()
                        .productId(product.getId())
                        .quantity(DEFAULT_QUANTITY)
                ));
        return result;
    }

    private PutOnRackCellCommand putNextFromStock(RackCell rack) {
        Product productToPutOnRack;
        productToPutOnRack = stock.stream()
                .filter(product -> !productsOnRack.contains(product.getId())
                        && product.getInStock() > 0)
                .findFirst().orElse(null);
        productsOnRack.add(productToPutOnRack.getId());
        final Integer inStock = productToPutOnRack.getInStock();
        int rackCapacity = rack.getCapacity();
        return new PutOnRackCellCommand().productId(productToPutOnRack.getId())
                .rackCellId(rack.getId())
                .productQuantity(Math.min(inStock, rackCapacity));
    }

    public List<BuyStockCommand> initialBuyIn() {
        List<BuyStockCommand> buyStockCommands = new ArrayList<>(50);
        stock.forEach(it -> buyStockCommands.add(
                new BuyStockCommand().productId(it.getId()).quantity(DEFAULT_QUANTITY)
        ));
        return buyStockCommands;
    }
}
