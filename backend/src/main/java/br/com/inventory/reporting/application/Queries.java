package br.com.inventory.reporting.application;
import java.util.*;
public interface Queries {
    record Page(List<Map<String,Object>> items,int page,int size,long total){}
    Page list(String resource,int page,int size,String search,String filter,String sort,UUID related);
    Map<String,Object> dashboard();
    Map<String,Object> sale(UUID id);
    Map<String,Object> customerSummary(UUID id);
}
