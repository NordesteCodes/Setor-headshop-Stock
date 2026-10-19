package br.com.inventory.customers.application;
import br.com.inventory.customers.domain.Customer;
import br.com.inventory.shared.application.CommandExecutor;
import java.util.UUID;
public class CustomerService {
    private final CustomerRepository repo;private final CommandExecutor commands;
    public CustomerService(CustomerRepository repo,CommandExecutor commands){this.repo=repo;this.commands=commands;}
    public UUID save(UUID key,Customer customer,boolean create,UUID actor){return commands.execute(create?"customer.create":"customer.edit",key,customer,actor,()->{if(create)repo.create(customer);else repo.save(customer);return customer.id();});}
    public Customer get(UUID id){return repo.customer(id);}
}
