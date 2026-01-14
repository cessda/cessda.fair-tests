package eu.cessda.fairtests.server;

import eu.cessda.fairtests.FairTests;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;

@SpringBootApplication
public class FairTestsApplication {
    public static void main(String[] args) {
        SpringApplication.run(FairTestsApplication.class, args);
    }

    @Bean
    public FairTests fairTests() throws XPathExpressionException, ParserConfigurationException {
        return new FairTests();
    }
}
