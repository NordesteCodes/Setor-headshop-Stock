package br.com.inventory.shared.adapter.postgres;
import br.com.inventory.shared.domain.BusinessException;
import java.util.List;
public final class Rows {
    private Rows(){}
    public static <T> T one(List<T> rows) {
        if(rows.isEmpty())throw new BusinessException("NOT_FOUND","Registro não encontrado."); return rows.getFirst();
    }
    public static void updated(int count){if(count!=1)throw new BusinessException("STALE_VERSION","Registro alterado por outra operação. Atualize a página.");}
}
