package no.fintlabs.flyt;

import no.fint.altinn.model.kafka.KafkaAltinnInstance;
import no.fintlabs.gateway.instance.model.File;
import no.fintlabs.gateway.instance.model.instance.InstanceObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IncomingInstanceMappingServiceTest {

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private ClientResponse clientResponse;

    @Mock
    private ClientResponse.Headers clientResponseHeaders;

    private IncomingInstanceMappingService incomingInstanceMappingService;

    private UUID uuid = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        incomingInstanceMappingService = new IncomingInstanceMappingService(webClient);
    }

    @Test
    void shouldMapInstanceSuccessfully() {
        // Given
        Long sourceApplicationId = 5L;
        String instanceId = "test-instance-id";
        byte[] pdfContent = "test pdf content".getBytes();

        KafkaAltinnInstance instance = KafkaAltinnInstance.builder()
                .instanceId(instanceId)
                .organizationNumber("123456789")
                .organizationName("Test Org")
                .companyEmail("test@example.com")
                .companyPhone("12345678")
                .countyName("Test County")
                .municipalityName("Test Municipality")
                .companyAdressStreet("Test Street 1")
                .companyAdressPostcode("1234")
                .companyAdressPostplace("Test City")
                .postalAdressStreet("Postal Street 1")
                .postalAdressPostcode("4321")
                .postalAdressPostplace("Postal City")
                .build();

        Function<File, Mono<UUID>> persistFile = file -> Mono.just(uuid);

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(String.format("http://10.104.4.130:8080/api/file/%s/ref-data-as-pdf", instanceId)))
                .thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.exchangeToMono(any())).thenAnswer(invocation -> {
            Function<ClientResponse, Mono<byte[]>> function = invocation.getArgument(0);
            when(clientResponse.headers()).thenReturn(clientResponseHeaders);
            when(clientResponse.headers().contentType()).thenReturn(Optional.of(MediaType.APPLICATION_PDF));
            when(clientResponse.bodyToMono(byte[].class)).thenReturn(Mono.just(pdfContent));
            return function.apply(clientResponse);
        });

        // When
        Mono<InstanceObject> result = incomingInstanceMappingService.map(sourceApplicationId, instance, persistFile);

        // Then
        StepVerifier.create(result)
                .assertNext(instanceObject -> {
                    Map<String, String> valuePerKey = instanceObject.getValuePerKey();
                    assertThat(valuePerKey)
                            .containsEntry("virksomhetOrganisasjonsnummer", "123456789")
                            .containsEntry("virksomhetOrganisasjonsnavn", "Test Org")
                            .containsEntry("virksomhetEpostadresse", "test@example.com")
                            .containsEntry("virksomhetTelefonnummer", "12345678")
                            .containsEntry("virksomhetFylke", "Test County")
                            .containsEntry("virksomhetKommune", "Test Municipality")
                            .containsEntry("virksomhetGateadresse", "Test Street 1")
                            .containsEntry("virksomhetPostnummer", "1234")
                            .containsEntry("virksomhetPoststed", "Test City")
                            .containsEntry("postadresseGateadresse", "Postal Street 1")
                            .containsEntry("postadressePostnummer", "4321")
                            .containsEntry("postadressePoststed", "Postal City")

                            .containsEntry("soknadTittel", "Søknadsskjema")
                            .containsEntry("soknadFormat", "application/pdf")
                            .containsEntry("soknadFil", uuid.toString())
                    ;
                })
                .verifyComplete();
    }
}
