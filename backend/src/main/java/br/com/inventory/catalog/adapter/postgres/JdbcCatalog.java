package br.com.inventory.catalog.adapter.postgres;
import br.com.inventory.catalog.application.CatalogRepository;
import br.com.inventory.catalog.domain.Product;
import br.com.inventory.shared.adapter.postgres.Rows;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public class JdbcCatalog implements CatalogRepository {
    private final JdbcTemplate db; public JdbcCatalog(JdbcTemplate db){this.db=db;}
    public Product product(UUID id){return Rows.one(db.query("SELECT * FROM product WHERE id=?",(r,n)->new Product(id,r.getString("name"),r.getString("sku"),r.getString("description"),r.getObject("category_id",UUID.class),r.getObject("unit_id",UUID.class),r.getBigDecimal("price"),r.getBigDecimal("purchase_price"),r.getLong("minimum"),r.getBoolean("active"),r.getLong("version")),id));}
    public void create(Product p){
        db.update("INSERT INTO product(id,name,sku,description,category_id,unit_id,price,purchase_price,minimum,active) VALUES (?,?,?,?,?,?,?,?,?,?)",p.id(),p.name(),p.sku(),p.description(),p.categoryId(),p.unitId(),p.price(),p.purchasePrice(),p.minimum(),p.active());
        db.update("INSERT INTO stock(product_id) VALUES (?)",p.id());
    }
    public void save(Product p){
        // Same lock order as stock commands: stock then product. Unit cannot change after a movement.
        db.queryForList("SELECT product_id FROM stock WHERE product_id=? FOR UPDATE",p.id());
        var old=product(p.id());
        br.com.inventory.shared.domain.Rules.require(old.unitId().equals(p.unitId()) || db.queryForObject("SELECT count(*) FROM stock_movement WHERE product_id=?",Long.class,p.id())==0,"Unidade não pode mudar após movimentações.");
        Rows.updated(db.update("UPDATE product SET name=?,sku=?,category_id=?,unit_id=?,price=?,purchase_price=?,minimum=?,active=?,version=version+1,updated_at=now() WHERE id=? AND version=? AND deleted_at IS NULL",p.name(),p.sku(),p.categoryId(),p.unitId(),p.price(),p.purchasePrice(),p.minimum(),p.active(),p.id(),p.version()));
    }
    public void namedValue(String type,UUID id,String name){
        String table=switch(type){case "category"->"category";case "unit"->"unit";default->throw new IllegalArgumentException();};
        db.update("INSERT INTO "+table+"(id,name) VALUES (?,?)",id,name);
    }
    public void renameCategory(UUID id,String name){
        br.com.inventory.shared.domain.Rules.require(db.update("UPDATE category SET name=? WHERE id=?",name,id)==1,"Categoria não encontrada.");
    }
    public void deleteProduct(UUID id){
        br.com.inventory.shared.domain.Rules.require(db.update("UPDATE product SET active=false,deleted_at=now(),version=version+1,updated_at=now() WHERE id=? AND deleted_at IS NULL",id)==1,"Produto não encontrado.");
    }
}
