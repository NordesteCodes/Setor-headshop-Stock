package br.com.inventory.reporting.adapter.http;

import br.com.inventory.reporting.application.Queries;
import br.com.inventory.reporting.adapter.xlsx.OrderWorkbook;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController
public class OrderExportController {
    private final Queries queries;

    public OrderExportController(Queries queries) { this.queries = queries; }

    @GetMapping(value = "/api/sales/{id}/export.xlsx", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ResponseEntity<byte[]> export(@PathVariable UUID id) {
        var bytes = OrderWorkbook.create(queries.sale(id));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("pedido-" + id + ".xlsx").build().toString())
                .body(bytes);
    }
}
