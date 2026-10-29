package br.com.inventory.reporting.adapter.postgres;
import br.com.inventory.reporting.application.Queries;
import br.com.inventory.shared.adapter.postgres.Rows;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static br.com.inventory.shared.domain.Rules.*;

@Repository
public class JdbcQueries implements Queries {
    private final JdbcTemplate db;private final ObjectMapper json;
    public JdbcQueries(JdbcTemplate db,ObjectMapper json){this.db=db;this.json=json;}
    public Page list(String resource,int page,int size,String search,String filter,String sort,UUID related){
        require(page>=0 && page<=100000 && size>=1 && size<=100,"Paginação inválida.");
        require(search!=null && search.length()<=150,"Busca inválida.");
        String sql;String order;var args=new ArrayList<Object>();
        switch(resource){
            case "products" -> {sql="SELECT p.id,p.name,p.sku,p.category_id,p.unit_id,p.price,p.purchase_price,p.minimum,p.active,p.version,c.name category,s.quantity,s.average_cost FROM product p JOIN category c ON c.id=p.category_id JOIN stock s ON s.product_id=p.id WHERE p.deleted_at IS NULL AND (p.name ILIKE ? OR coalesce(p.sku,'') ILIKE ? OR c.name ILIKE ? OR s.quantity::text ILIKE ? OR s.average_cost::text ILIKE ? OR replace(s.average_cost::text,'.',',') ILIKE ? OR p.purchase_price::text ILIKE ? OR replace(p.purchase_price::text,'.',',') ILIKE ? OR p.price::text ILIKE ? OR replace(p.price::text,'.',',') ILIKE ? OR p.minimum::text ILIKE ? OR (CASE WHEN NOT p.active THEN 'inativo oculto' WHEN s.quantity=0 THEN 'sem saldo' WHEN p.minimum>0 AND s.quantity<=p.minimum THEN 'estoque baixo' ELSE 'ativo disponível' END) ILIKE ?)";for(int i=0;i<12;i++)args.add("%"+search+"%");if(filter.equals("active"))sql+=" AND p.active";else if(filter.equals("inactive"))sql+=" AND NOT p.active";order=switch(sort){case "quantity"->"quantity DESC,id";case "quantity-asc"->"quantity ASC,id";case "purchase-price"->"purchase_price ASC,id";case "sale-price"->"price ASC,id";case "name-desc"->"name DESC,id";default->"name,id";};}
            case "customers" -> {
                sql="SELECT c.*,coalesce((SELECT sum(total-paid) FROM sale WHERE customer_id=c.id),0) debt,(SELECT count(*) FROM sale WHERE customer_id=c.id AND delivered_at IS NULL) pending_deliveries FROM customer c WHERE (c.name ILIKE ? OR coalesce(c.document,'') ILIKE ? OR coalesce(c.phone,'') ILIKE ? OR coalesce(c.email,'') ILIKE ? OR c.city ILIKE ?)";
                for(int i=0;i<5;i++)args.add("%"+search+"%");
                if(filter.equals("debt"))sql+=" AND EXISTS(SELECT 1 FROM sale WHERE customer_id=c.id AND paid<total)";
                if(filter.equals("pending"))sql+=" AND EXISTS(SELECT 1 FROM sale WHERE customer_id=c.id AND delivered_at IS NULL)";
                if(filter.equals("settled"))sql+=" AND NOT EXISTS(SELECT 1 FROM sale WHERE customer_id=c.id AND paid<total)";
                order="name,id";
            }
            case "sales" -> {
                sql="SELECT s.*,u.username actor_name,total-paid due,CASE WHEN paid=0 THEN 'PENDING' WHEN paid<total THEN 'PARTIAL' ELSE 'PAID' END payment_status FROM sale s JOIN app_user u ON u.id=s.actor WHERE (customer_name ILIKE ? OR s.id::text ILIKE ? OR EXISTS(SELECT 1 FROM effective_sale_line sl WHERE sl.sale_id=s.id AND sl.product_name ILIKE ?))";
                for(int i=0;i<3;i++)args.add("%"+search+"%");
                if(related!=null){sql+=" AND customer_id=?";args.add(related);}
                if(filter.equals("pending"))sql+=" AND delivered_at IS NULL";
                if(filter.equals("delivered"))sql+=" AND delivered_at IS NOT NULL";
                if(filter.equals("debt"))sql+=" AND paid<total";
                if(filter.equals("paid"))sql+=" AND paid=total";
                if(filter.equals("partial"))sql+=" AND paid>0 AND paid<total";
                order="created_at DESC,id";
            }
            case "stock-movements" -> {
                sql="SELECT m.*,p.name product_name,u.username actor_name FROM effective_stock_movement m JOIN product p ON p.id=m.product_id JOIN app_user u ON u.id=m.actor WHERE (p.name ILIKE ? OR m.reason ILIKE ? OR u.username ILIKE ?)";
                for(int i=0;i<3;i++)args.add("%"+search+"%");
                if(related!=null){sql+=" AND product_id=?";args.add(related);}
                if(filter.equals("damage"))sql+=" AND type='DAMAGE'";
                if(filter.equals("inbound"))sql+=" AND type='INBOUND'";
                if(filter.equals("delivery"))sql+=" AND type='DELIVERY'";
                order="created_at DESC,id";
            }
            case "cash" -> {sql="SELECT e.*,u.username actor_name,EXISTS(SELECT 1 FROM cash_entry r WHERE r.reversal_of=e.id) reversed FROM cash_entry e JOIN app_user u ON u.id=e.actor WHERE (e.description ILIKE ? OR u.username ILIKE ? OR EXISTS(SELECT 1 FROM sale s WHERE s.id=e.sale_id AND s.customer_name ILIKE ?))";for(int i=0;i<3;i++)args.add("%"+search+"%");if(filter.equals("archive"))sql+=" AND e.created_at<now()-interval '2 years'";else if(!filter.equals("all"))sql+=" AND e.created_at>=now()-interval '2 years'";if(related!=null){sql+=" AND sale_id=?";args.add(related);}order="created_at DESC,id";}
            case "categories" -> {sql="SELECT * FROM category WHERE name ILIKE ?";args.add("%"+search+"%");order="name,id";}
            case "units" -> {sql="SELECT * FROM unit WHERE name ILIKE ?";args.add("%"+search+"%");order="name,id";}
            default -> throw new br.com.inventory.shared.domain.BusinessException("NOT_FOUND","Recurso não encontrado.");
        }
        long total=db.queryForObject("SELECT count(*) FROM ("+sql+") q",Long.class,args.toArray());
        args.add(size);args.add(page*size);
        return new Page(db.queryForList(sql+" ORDER BY "+order+" LIMIT ? OFFSET ?",args.toArray()),page,size,total);
    }
    public Map<String,Object> sale(UUID id){
        var sale=new LinkedHashMap<>(Rows.one(db.queryForList("SELECT *,total-paid due FROM sale WHERE id=?",id)));
        try{sale.put("address",json.readValue(sale.get("address").toString(),Map.class));}catch(Exception e){throw new IllegalStateException(e);}
        sale.put("items",db.queryForList("SELECT * FROM effective_sale_line WHERE sale_id=? ORDER BY product_name",id));
        sale.put("receipts",db.queryForList("SELECT e.*,u.username actor_name FROM cash_entry e JOIN app_user u ON u.id=e.actor WHERE sale_id=? ORDER BY created_at",id));return sale;
    }
    public Map<String,Object> dashboard(){
        var zone=ZoneId.of("America/Sao_Paulo");
        var now=LocalDate.now(zone);
        var monthStart=now.withDayOfMonth(1);
        var todayStart=java.sql.Timestamp.from(now.atStartOfDay(zone).toInstant());
        var tomorrowStart=java.sql.Timestamp.from(now.plusDays(1).atStartOfDay(zone).toInstant());
        var monthStartInstant=java.sql.Timestamp.from(monthStart.atStartOfDay(zone).toInstant());
        // One round trip for all cards; no browser cache of balances or financial results.
        var out=new LinkedHashMap<String,Object>(db.queryForMap("""
                WITH bounds AS (SELECT ?::timestamptz month_start,?::timestamptz until),
                sales_summary AS (
                    SELECT coalesce(sum(total-paid),0) receivables,
                           count(*) FILTER(WHERE delivered_at IS NULL) pending_deliveries,
                           count(*) FILTER(WHERE paid<total) pending_payment_count,
                           coalesce(sum(total) FILTER(WHERE created_at>=b.month_start AND created_at<b.until),0) month_sales_total,
                           count(*) FILTER(WHERE created_at>=b.month_start AND created_at<b.until) month_sales_count,
                           count(*) FILTER(WHERE delivered_at IS NOT NULL AND settled_at IS NOT NULL AND paid=total
                             AND greatest(delivered_at,settled_at)>=b.month_start AND greatest(delivered_at,settled_at)<b.until) month_fulfilled_sales,
                           coalesce(sum(total) FILTER(WHERE delivered_at IS NOT NULL AND settled_at IS NOT NULL AND paid=total
                             AND greatest(delivered_at,settled_at)>=b.month_start AND greatest(delivered_at,settled_at)<b.until),0) month_realized_revenue
                    FROM sale CROSS JOIN bounds b
                ), stock_summary AS (
                    SELECT coalesce(sum(quantity*average_cost),0) stock_value,coalesce(sum(quantity),0) stock_quantity FROM stock
                ), movement_summary AS (
                    SELECT count(*) month_movement_count,
                           coalesce(sum(delta) FILTER(WHERE type='INBOUND'),0) month_inbound,
                           coalesce(sum(delta) FILTER(WHERE type='DELIVERY'),0) month_outbound,
                           coalesce(sum(delta) FILTER(WHERE type='DAMAGE'),0) month_damage
                    FROM stock_movement CROSS JOIN bounds b WHERE created_at>=b.month_start AND created_at<b.until
                ), profit_summary AS (
                    SELECT coalesce(sum((sl.price-sl.delivered_cost)*sl.quantity),0) month_realized_profit
                    FROM sale s JOIN effective_sale_line sl ON sl.sale_id=s.id CROSS JOIN bounds b
                    WHERE s.delivered_at IS NOT NULL AND s.settled_at IS NOT NULL AND s.paid=s.total
                      AND greatest(s.delivered_at,s.settled_at)>=b.month_start AND greatest(s.delivered_at,s.settled_at)<b.until
                )
                SELECT sales_summary.*,stock_summary.*,movement_summary.*,profit_summary.*,
                       (SELECT coalesce(sum(amount),0) FROM cash_entry) cash
                FROM sales_summary CROSS JOIN stock_summary CROSS JOIN movement_summary CROSS JOIN profit_summary
                """,monthStartInstant,tomorrowStart));
        out.put("today_orders",db.queryForList("""
                SELECT s.id,s.customer_name,s.total,s.paid,s.total-s.paid due,s.created_at,s.delivered_at,
                       c.document customer_document,
                       (SELECT string_agg(sl.quantity::text || ' × ' || sl.product_name, ', ' ORDER BY sl.product_name) FROM sale_line sl WHERE sl.sale_id=s.id) items_summary,
                       (SELECT sum(sl.quantity) FROM sale_line sl WHERE sl.sale_id=s.id) item_quantity,
                       CASE WHEN s.paid=0 THEN 'PENDING' WHEN s.paid<s.total THEN 'PARTIAL' ELSE 'PAID' END payment_status
                FROM sale s JOIN customer c ON c.id=s.customer_id
                WHERE s.created_at>=? AND s.created_at<? ORDER BY s.created_at DESC,s.id
                """,todayStart,tomorrowStart));
        var chartStart=monthStart.isBefore(now.minusDays(6))?monthStart:now.minusDays(6);
        var chartStartInstant=java.sql.Timestamp.from(chartStart.atStartOfDay(zone).toInstant());
        var activity=db.queryForList("""
                WITH calendar AS (
                    SELECT generate_series(?::date,?::date,interval '1 day')::date activity_date
                ), completed AS (
                    SELECT (greatest(s.delivered_at,s.settled_at) AT TIME ZONE 'America/Sao_Paulo')::date activity_date,
                           sum(sl.price*sl.quantity) revenue,
                           sum((sl.price-sl.delivered_cost)*sl.quantity) gross_profit
                    FROM sale s JOIN effective_sale_line sl ON sl.sale_id=s.id
                    WHERE s.delivered_at IS NOT NULL AND s.settled_at IS NOT NULL AND s.paid=s.total
                      AND greatest(s.delivered_at,s.settled_at)>=? AND greatest(s.delivered_at,s.settled_at)<?
                    GROUP BY 1
                ), movements AS (
                    SELECT (created_at AT TIME ZONE 'America/Sao_Paulo')::date activity_date,
                           coalesce(sum(delta) FILTER(WHERE type='INBOUND'),0) inbound,
                           coalesce(-sum(delta) FILTER(WHERE type='DELIVERY'),0) outbound,
                           coalesce(-sum(delta) FILTER(WHERE type='DAMAGE'),0) damage
                    FROM stock_movement WHERE created_at>=? AND created_at<? GROUP BY 1
                )
                SELECT c.activity_date,coalesce(s.revenue,0) revenue,coalesce(s.gross_profit,0) gross_profit,
                       coalesce(m.inbound,0) inbound,coalesce(m.outbound,0) outbound,coalesce(m.damage,0) damage
                FROM calendar c LEFT JOIN completed s USING(activity_date) LEFT JOIN movements m USING(activity_date)
                ORDER BY c.activity_date
                """,java.sql.Date.valueOf(chartStart),java.sql.Date.valueOf(now),
                chartStartInstant,tomorrowStart,chartStartInstant,tomorrowStart);
        out.put("daily_activity",activity);
        out.put("daily_profit",activity.stream()
                .filter(day->!((java.sql.Date)day.get("activity_date")).toLocalDate().isBefore(monthStart))
                .map(day->Map.of("sales_date",day.get("activity_date"),"gross_profit",day.get("gross_profit"))).toList());
        return out;
    }

    public Map<String,Object> customerSummary(UUID id){return db.queryForMap("SELECT coalesce(sum(total-paid),0) debt,count(*) FILTER(WHERE delivered_at IS NULL) pending_deliveries,coalesce(sum(total),0) purchased_total FROM sale WHERE customer_id=?",id);}
}
