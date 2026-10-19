package br.com.inventory.sales.domain;

import br.com.inventory.customers.domain.Customer.Address;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static br.com.inventory.shared.domain.Rules.*;

public record Sale(UUID id, UUID customerId, String customerName, Address address, BigDecimal total,
                   BigDecimal paid, Instant deliveredAt, List<Line> lines) {
    public Sale { lines = List.copyOf(lines); }
    public void validatePayment(BigDecimal amount) {
        money(amount); require(amount.signum()>0,"Recebimento deve ser positivo.");
        require(paid.add(amount).compareTo(total)<=0,"Recebimento excede o saldo devedor.");
    }
    public void validateDelivery() { require(deliveredAt==null,"Pedido já entregue."); }
    public record Line(UUID productId,String productName,long quantity,BigDecimal price,BigDecimal deliveredCost) {
        public Line { require(productId!=null && quantity>0 && quantity<=1_000_000_000L,"Quantidade inválida."); price=money(price); }
        public BigDecimal total() { return price.multiply(BigDecimal.valueOf(quantity)); }
    }
}
