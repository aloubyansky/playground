package com.jeeconf.quarkus.forex;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ForexController {
  
  @Value("${quarkus.http.port}")
  private String serverPort;
  
  @Autowired
  private ExchangeValueRepository repository;
  
  @GetMapping("/currency-exchange/from/{from}/to/{to}")
  public ExchangeValue retrieveExchangeValue
    (@PathVariable String from, @PathVariable String to){
    
    ExchangeValue exchangeValue = 
        repository.findByFromCurrencyAndToCurrency(from, to);

	  System.out.println("Converting " + from + " -> " + to + " " + exchangeValue);

/*    if(exchangeValue != null) {
        exchangeValue.setPort(Integer.parseInt(serverPort));
    }*/
    
    return new ExchangeValue();
  }
}