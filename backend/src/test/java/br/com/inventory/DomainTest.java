package br.com.inventory;
import br.com.inventory.inventory.domain.StockPosition;
import br.com.inventory.customers.domain.Customer;
import br.com.inventory.auth.adapter.Totp;
import br.com.inventory.shared.domain.BusinessException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

class DomainTest {
    @Test void weightedAverageAndNoNegativeBalance(){
        var stock=new StockPosition(0,BigDecimal.ZERO).receive(10,new BigDecimal("10.00")).receive(10,new BigDecimal("12.00"));
        assertThat(stock.averageCost()).isEqualByComparingTo("11.000000");
        assertThat(stock.deduct(7).quantity()).isEqualTo(13);
        assertThatThrownBy(()->stock.deduct(21)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->stock.receive(0,BigDecimal.ONE)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->stock.receive(1,new BigDecimal("1.001"))).isInstanceOf(BusinessException.class);
    }
    @Test void addressAcceptsCityUfAndOptionalPostalCode(){
        assertThatThrownBy(()->new Customer.Address("","","Centro","Aracaju-SE","","","", "")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->new Customer.Address("Rua A, 10","","Centro","Cidade-SE","","12345","", "")).isInstanceOf(BusinessException.class);
        assertThat(new Customer.Address("Rua A, 10","","Centro","Aracaju-SE","","12345-678","", "").postalCode()).isEqualTo("12345678");
        assertThat(new Customer.Address("Rua A, 10","","Centro","Aracaju-SE","","","", "").postalCode()).isEmpty();
    }
    @Test void rfc6238KnownVectorAndReplayProtection(){
        String secret="GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
        assertThat(Totp.code(secret,59/30)).isEqualTo("287082");
        assertThat(Totp.verify(secret,"287082",1,-1)).isEqualTo(1);
        assertThat(Totp.verify(secret,"287082",1,1)).isEqualTo(-1);
        assertThat(Totp.verify(secret,"000000",1,-1)).isEqualTo(-1);
    }
}
