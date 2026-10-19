package br.com.inventory.customers.domain;

import java.util.UUID;
import static br.com.inventory.shared.domain.Rules.*;

public record Customer(UUID id, String name, String document, String phone, String email, Address address, long version) {
    public Customer {
        name = text(name,150);
        require(address != null, "Endereço obrigatório.");
        document = clean(document,18); phone = clean(phone,30); email = clean(email,200);
        require(document.isEmpty() || document.replaceAll("\\D", "").matches("[0-9]{11}|[0-9]{14}"), "CPF/CNPJ deve conter 11 ou 14 dígitos.");
        require(email.isEmpty() || email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"), "Email inválido.");
    }
    private static String clean(String value,int max) {
        value = value == null ? "" : value.strip(); require(value.length()<=max,"Campo muito longo."); return value;
    }
    public record Address(String street,String number,String district,String city,String state,String postalCode,String complement,String reference) {
        public Address {
            street=text(street,200); number=clean(number,30); district=text(district,100); city=text(city,100);
            state=clean(state,2).toUpperCase(java.util.Locale.ROOT);
            postalCode=clean(postalCode,9).replace("-", "");
            require(postalCode.isEmpty() || postalCode.matches("[0-9]{8}"),"CEP inválido.");
            complement=clean(complement,200); reference=clean(reference,200);
        }
    }
}
