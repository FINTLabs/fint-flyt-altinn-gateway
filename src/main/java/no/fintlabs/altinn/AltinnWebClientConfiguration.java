package no.fintlabs.altinn;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class AltinnWebClientConfiguration {

    private static final int MAX_IN_MEMORY_SIZE = 50 * 1024 * 1024;

    @Value("${fint.altinn.api.base-url}")
    private String altinnApiBaseUrl;

    @Bean
    public WebClient altinnWebClient() {
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(clientCodecConfigurer ->
                        clientCodecConfigurer.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_SIZE))
                .build();

        return WebClient.builder()
                .exchangeStrategies(strategies)
                .baseUrl(altinnApiBaseUrl)
                .build();
    }
}
