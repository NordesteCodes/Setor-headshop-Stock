package br.com.inventory.customers.application;
import br.com.inventory.customers.domain.Customer;
import java.util.UUID;
public interface CustomerRepository {
    Customer customer(UUID id);
    void create(Customer customer);
    void save(Customer customer);
}
