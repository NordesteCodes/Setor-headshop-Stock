package br.com.inventory.catalog.application;
import br.com.inventory.catalog.domain.Product;
import br.com.inventory.shared.application.CommandExecutor;
import java.util.Map;
import java.util.UUID;
import static br.com.inventory.shared.domain.Rules.*;
public class CatalogService {
    private final CatalogRepository repo; private final CommandExecutor commands;
    public CatalogService(CatalogRepository repo,CommandExecutor commands){this.repo=repo;this.commands=commands;}
    public UUID save(UUID key,Product p,boolean create,UUID actor){return commands.execute(create?"product.create":"product.edit",key,p,actor,()->{if(create)repo.create(p);else repo.save(p);return p.id();});}
    public UUID named(UUID key,String type,String name,UUID actor){return commands.execute("catalog."+type,key,name,actor,()->{var id=UUID.randomUUID();repo.namedValue(type,id,text(name,type.equals("unit")?30:100));return id;});}
    public UUID renameCategory(UUID key,UUID id,String name,UUID actor){var normalized=text(name,100);return commands.execute("catalog.category.edit",key,Map.of("id",id,"name",normalized),actor,()->{repo.renameCategory(id,normalized);return id;});}
    public UUID deleteProduct(UUID key,UUID id,UUID actor){return commands.execute("product.delete",key,id,actor,()->{repo.deleteProduct(id);return id;});}
}
