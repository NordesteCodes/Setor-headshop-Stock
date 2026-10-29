package br.com.inventory;

import br.com.inventory.catalog.application.CatalogRepository;
import br.com.inventory.inventory.application.InventoryRepository;
import br.com.inventory.inventory.application.InventoryService;
import br.com.inventory.inventory.domain.StockPosition;
import br.com.inventory.shared.application.CommandExecutor;
import br.com.inventory.shared.domain.BusinessException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OpeningCostRepairTest {
    final InventoryRepository repo=mock(InventoryRepository.class);
    final InventoryService service=new InventoryService(repo,mock(CatalogRepository.class),mock(CommandExecutor.class));
    final UUID product=UUID.randomUUID(), key=UUID.randomUUID();
    final String reason="Correção do custo de compra informado", operator="local-maintenance";
    InventoryRepository.CostMovement movement(long quantity,String type) {
        return new InventoryRepository.CostMovement(UUID.randomUUID(),quantity,new BigDecimal("3.900000"),type);
    }
    @Test void correctsOpeningLotWithoutChangingQuantityOrHistoricalMovements() {
        var before=new StockPosition(550,new BigDecimal("3.900000"));
        var movements=List.of(movement(1200,"INBOUND"),movement(-250,"DELIVERY"),movement(-400,"DELIVERY"));
        when(repo.lock(product)).thenReturn(before); when(repo.costHistory(product)).thenReturn(movements);
        assertThat(service.repairOpeningCost(key,product,new BigDecimal("1.90"),reason,operator)).isEqualTo(key);
        verify(repo).recordCostRepair(new InventoryRepository.CostRepair(key,product,new BigDecimal("1.90"),reason,operator),before,movements);
        verify(repo).update(product,new StockPosition(550,new BigDecimal("1.900000")));
        assertThat(new BigDecimal("3.90").subtract(new BigDecimal("1.90")).multiply(BigDecimal.valueOf(400))).isEqualByComparingTo("800");
        verify(repo,never()).movement(any(),any(),anyLong(),anyLong(),any(),any(),any(),any(),any());
    }
    @Test void rejectsMixedLotsBeforeWritingCorrection() {
        when(repo.lock(product)).thenReturn(new StockPosition(1201,new BigDecimal("3.90")));
        when(repo.costHistory(product)).thenReturn(List.of(movement(1200,"INBOUND"),movement(1,"INBOUND")));
        assertThatThrownBy(()->service.repairOpeningCost(key,product,new BigDecimal("1.90"),reason,operator)).isInstanceOf(BusinessException.class);
        verify(repo,never()).update(any(),any()); verify(repo,never()).recordCostRepair(any(),any(),any());
    }
    @Test void retryReturnsOriginalResultAndChangedPayloadFails() {
        when(repo.costRepair(key)).thenReturn(new InventoryRepository.CostRepair(key,product,new BigDecimal("1.90"),reason,operator));
        assertThat(service.repairOpeningCost(key,product,new BigDecimal("1.90"),reason,operator)).isEqualTo(key);
        assertThatThrownBy(()->service.repairOpeningCost(key,product,new BigDecimal("2.00"),reason,operator)).isInstanceOf(BusinessException.class);
        verify(repo,never()).lock(any()); verify(repo,never()).update(any(),any());
    }
}
