package br.com.inventory.finance.application;
import br.com.inventory.sales.application.SaleRepository;
import br.com.inventory.shared.application.CommandExecutor;
import java.math.BigDecimal;
import java.util.*;
import static br.com.inventory.shared.domain.Rules.*;

public class CashService {
    private final CashRepository cash; private final SaleRepository sales; private final CommandExecutor commands;
    public CashService(CashRepository cash,SaleRepository sales,CommandExecutor commands) {this.cash=cash;this.sales=sales;this.commands=commands;}
    public record Manual(BigDecimal amount,String category,String description,UUID damageId) {}
    public UUID manual(UUID key,Manual input,UUID actor) {
        return commands.execute("cash.manual",key,input,actor,()-> {
            require(input.category()!=null && Set.of("OPENING","CONTRIBUTION","WITHDRAWAL","EXPENSE","PURCHASE").contains(input.category()),"Categoria inválida.");
            var amount=money(input.amount()); require(amount.signum()>0,"Valor deve ser positivo."); text(input.description(),500);
            require(input.damageId()==null,"Referência de avaria inválida.");
            if(Set.of("WITHDRAWAL","EXPENSE","PURCHASE").contains(input.category())) amount=amount.negate();
            UUID id=UUID.randomUUID(); cash.append(new CashRepository.Entry(id,amount,input.category(),input.description(),null,input.damageId(),null),actor); return id;
        });
    }
    public UUID reverse(UUID key,UUID id,String reason,UUID actor) {
        return commands.execute("cash.reverse",key,List.of(id,reason),actor,()-> {
            text(reason,500);
            var entry=cash.lock(id); require(!entry.category().equals("REVERSAL") && !entry.category().equals("OPENING"),"Este lançamento não pode ser estornado.");
            require(!cash.reversed(id),"Lançamento já estornado.");
            if(entry.saleId()!=null) {sales.lock(entry.saleId()); sales.addPayment(entry.saleId(),entry.amount().negate());}
            UUID result=UUID.randomUUID(); cash.append(new CashRepository.Entry(result,entry.amount().negate(),"REVERSAL",reason,entry.saleId(),entry.damageId(),id),actor); return result;
        });
    }
}
