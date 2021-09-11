package ru.hilariousstartups.javaskills.psplayer;

import ru.hilariousstartups.javaskills.psplayer.swagger_codegen.model.Product;
import ru.hilariousstartups.javaskills.psplayer.swagger_codegen.model.RackCell;

import java.util.Comparator;
import java.util.List;

/**
 * Выкладывает товар на полки, определяет наценки.
 * Будет ли заниматься заказом товара - пока не знаю
 */
public class Merchandaizer {
    private List<RackCell> racks;
    private List<Product> stock;

    public Merchandaizer(List<RackCell> racks, List<Product> stock) {
        this.racks = racks;
        this.stock = stock;
        this.stock.sort(Comparator.comparing(Product::getStockPrice));
    }
}
