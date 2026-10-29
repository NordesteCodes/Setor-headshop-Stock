package br.com.inventory.reporting.adapter.xlsx;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/** Presentation adapter: exports the same immutable order snapshot shown by the API. */
public final class OrderWorkbook {
    private OrderWorkbook() {}
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("America/Sao_Paulo"));

    public static byte[] create(Map<String, Object> sale) {
        try (var book = new XSSFWorkbook(); var bytes = new ByteArrayOutputStream()) {
            var heading = book.createCellStyle();
            heading.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());
            heading.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            var font = book.createFont(); font.setBold(true); font.setColor(IndexedColors.WHITE.getIndex());
            heading.setFont(font);
            var currency = book.createCellStyle();
            currency.setDataFormat(book.createDataFormat().getFormat("\"R$\" #,##0.00;[Red]-\"R$\" #,##0.00"));
            var integer = book.createCellStyle();
            integer.setDataFormat(book.createDataFormat().getFormat("#,##0"));

            var summary = book.createSheet("Pedido");
            summary.setColumnWidth(0, 28 * 256); summary.setColumnWidth(1, 72 * 256);
            row(summary, 0, heading, "DETALHES DO PEDIDO", "");
            summary.addMergedRegion(new CellRangeAddress(0, 0, 0, 1));
            row(summary, 2, null, "Pedido", sale.get("id"));
            row(summary, 3, null, "Cliente", sale.get("customer_name"));
            row(summary, 4, null, "Criado em", date(sale.get("created_at")));
            var total = decimal(sale.get("total")); var paid = decimal(sale.get("paid"));
            row(summary, 5, null, "Pagamento", paid.signum() == 0 ? "Pendente" : paid.compareTo(total) < 0 ? "Parcialmente pago" : "Pago");
            row(summary, 6, null, "Entrega", sale.get("delivered_at") == null ? "Aguardando entrega" : "Entregue em " + date(sale.get("delivered_at")));
            var address = map(sale.get("address"));
            row(summary, 8, heading, "ENDEREÇO DA ENTREGA", "");
            row(summary, 9, null, "Rua", joined(address.get("street"), address.get("number")));
            row(summary, 10, null, "Bairro", address.get("district"));
            row(summary, 11, null, "Cidade / UF", joined(address.get("city"), address.get("state")));
            row(summary, 12, null, "CEP", address.get("postalCode"));
            row(summary, 13, null, "Complemento", address.get("complement"));
            row(summary, 14, null, "Referência", address.get("reference"));
            row(summary, 16, heading, "RESUMO FINANCEIRO", "");
            numericRow(summary, 17, "Total", total, currency);
            numericRow(summary, 18, "Recebido", paid, currency);
            numericRow(summary, 19, "Saldo a receber", total.subtract(paid), currency);

            var items = book.createSheet("Itens");
            row(items, 0, heading, "Produto", "Quantidade", "Valor unitário", "Subtotal");
            var lines = list(sale.get("items"));
            for (int i = 0; i < lines.size(); i++) {
                var line = lines.get(i); var r = items.createRow(i + 1);
                text(r.createCell(0), line.get("product_name"));
                number(r.createCell(1), decimal(line.get("quantity")), integer);
                number(r.createCell(2), decimal(line.get("price")), currency);
                var subtotal = r.createCell(3); subtotal.setCellFormula("B" + (i + 2) + "*C" + (i + 2)); subtotal.setCellStyle(currency);
            }
            var finalRow = items.createRow(lines.size() + 2);
            text(finalRow.createCell(0), "Total do pedido");
            var sum = finalRow.createCell(3); sum.setCellFormula("SUM(D2:D" + (lines.size() + 1) + ")"); sum.setCellStyle(currency);
            table(items, lines.size(), 3, 54, 16, 21, 22);

            var receipts = book.createSheet("Recebimentos");
            row(receipts, 0, heading, "Data", "Tipo", "Descrição", "Valor", "Responsável");
            var entries = list(sale.get("receipts"));
            for (int i = 0; i < entries.size(); i++) {
                var entry = entries.get(i); var r = receipts.createRow(i + 1);
                text(r.createCell(0), date(entry.get("created_at")));
                text(r.createCell(1), "REVERSAL".equals(entry.get("category")) ? "Estorno" : "Recebimento");
                text(r.createCell(2), entry.get("description"));
                number(r.createCell(3), decimal(entry.get("amount")), currency);
                text(r.createCell(4), entry.get("actor_name"));
            }
            table(receipts, entries.size(), 4, 23, 18, 54, 22, 40);
            book.getCreationHelper().createFormulaEvaluator().evaluateAll();
            book.write(bytes);
            return bytes.toByteArray();
        } catch (IOException e) { throw new UncheckedIOException("Não foi possível exportar o pedido.", e); }
    }

    private static void row(Sheet sheet, int index, CellStyle style, Object... values) {
        var r = sheet.createRow(index);
        for (int i = 0; i < values.length; i++) { var c = r.createCell(i); text(c, values[i]); if (style != null) c.setCellStyle(style); }
    }
    private static void numericRow(Sheet sheet, int index, String label, BigDecimal value, CellStyle style) {
        var r = sheet.createRow(index); text(r.createCell(0), label); number(r.createCell(1), value, style);
    }
    private static void text(Cell cell, Object value) { cell.setCellValue(value == null ? "" : value.toString()); }
    private static void number(Cell cell, BigDecimal value, CellStyle style) { cell.setCellValue(value.doubleValue()); cell.setCellStyle(style); }
    private static void table(Sheet sheet, int rows, int lastColumn, int... widths) {
        sheet.createFreezePane(0, 1); sheet.setAutoFilter(new CellRangeAddress(0, rows, 0, lastColumn));
        for (int i = 0; i < widths.length; i++) sheet.setColumnWidth(i, widths[i] * 256);
    }
    private static BigDecimal decimal(Object value) { return value instanceof BigDecimal d ? d : new BigDecimal(value.toString()); }
    private static String joined(Object first, Object second) { return String.valueOf(first == null ? "" : first) + (second == null || second.toString().isBlank() ? "" : " - " + second); }
    private static String date(Object value) {
        if (value == null) return "";
        Instant instant = value instanceof java.util.Date d ? d.toInstant() : value instanceof OffsetDateTime d ? d.toInstant() : Instant.parse(value.toString());
        return DATE.format(instant);
    }
    @SuppressWarnings("unchecked") private static Map<String, Object> map(Object value) { return (Map<String, Object>) value; }
    @SuppressWarnings("unchecked") private static List<Map<String, Object>> list(Object value) { return (List<Map<String, Object>>) value; }
}
