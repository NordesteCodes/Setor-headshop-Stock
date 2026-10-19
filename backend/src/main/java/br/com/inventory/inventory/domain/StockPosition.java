package br.com.inventory.inventory.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import static br.com.inventory.shared.domain.Rules.*;

public record StockPosition(long quantity, BigDecimal averageCost) {
    public StockPosition {
        require(quantity >= 0 && averageCost != null && averageCost.signum() >= 0, "Saldo inválido.");
    }
    public StockPosition receive(long amount, BigDecimal cost) {
        require(amount > 0, "Quantidade deve ser positiva.");
        cost = money(cost);
        long next = Math.addExact(quantity, amount);
        BigDecimal value = averageCost.multiply(BigDecimal.valueOf(quantity)).add(cost.multiply(BigDecimal.valueOf(amount)));
        return new StockPosition(next, value.divide(BigDecimal.valueOf(next), 6, RoundingMode.HALF_UP));
    }
    public StockPosition deduct(long amount) {
        require(amount > 0, "Quantidade deve ser positiva.");
        require(amount <= quantity, "Estoque insuficiente.");
        return new StockPosition(quantity - amount, averageCost);
    }
}
