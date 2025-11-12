package no.fintlabs.flyt;

import no.fint.altinn.model.kafka.KafkaAltinnInstance;
import no.fintlabs.altinn.AltinnFileService;
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
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IncomingInstanceMappingServiceTest {

    @Mock
    private AltinnFileService altinnFileService;

    private IncomingInstanceMappingService incomingInstanceMappingService;

    private final UUID uuid = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        incomingInstanceMappingService = new IncomingInstanceMappingService(altinnFileService);
    }

    @Test
    void shouldMapInstanceSuccessfully() {
        // Given
        Long sourceApplicationId = 5L;
        String instanceId = "test-instance-id";
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

        byte[] pdfContent = "test-pdf-content".getBytes();
        File expectedFile = File.builder()
                .name("test.pdf")
                .sourceApplicationId(sourceApplicationId)
                .type(MediaType.APPLICATION_PDF)
                .encoding("base64")
                .base64Contents(java.util.Base64.getEncoder().encodeToString(pdfContent))
                .build();

        Function<File, Mono<UUID>> persistFile = file -> Mono.just(uuid);

        when(altinnFileService.fetchFile(instanceId, "ref-data-as-pdf", sourceApplicationId))
                .thenReturn(Mono.just(expectedFile));
        when(altinnFileService.fetchFile(instanceId, "dom-forelegg", sourceApplicationId))
                .thenReturn(Mono.just(expectedFile));
        when(altinnFileService.fetchFile(instanceId, "beskrivelse-yrkestransportloven", sourceApplicationId))
                .thenReturn(Mono.just(expectedFile));
        when(altinnFileService.fetchFile(instanceId, "politiattest-dagligleder", sourceApplicationId))
                .thenReturn(Mono.just(expectedFile));
        when(altinnFileService.fetchFile(instanceId, "skatteattest-dagligleder", sourceApplicationId))
                .thenReturn(Mono.just(expectedFile));
        when(altinnFileService.fetchFile(instanceId, "konkursattest-dagligleder", sourceApplicationId))
                .thenReturn(Mono.just(expectedFile));
        when(altinnFileService.fetchEbevisFile(instanceId, "RestanserV2", sourceApplicationId))
                .thenReturn(Mono.just(expectedFile));
        when(altinnFileService.fetchEbevisFile(instanceId, "KonkursDrosje", sourceApplicationId))
                .thenReturn(Mono.just(expectedFile));
        // When
        Mono<InstanceObject> result = incomingInstanceMappingService.map(sourceApplicationId, instance, persistFile);

        // Then
        StepVerifier.create(result)
                .assertNext(instanceObject -> {
                    Map<String, String> valuePerKey = instanceObject.getValuePerKey();
                    Map<String, Collection<InstanceObject>> objectCollection = instanceObject.getObjectCollectionPerKey();

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

                            .containsEntry("politiattestLederTittel", "Politiattest for daglig leder")
                            .containsEntry("politiattestLederFormat", "application/pdf")
                            .containsEntry("politiattestLederFil", uuid.toString())
                    ;

                    assertThat(objectCollection).hasSize(4);
                    assertThat(objectCollection.get("domForelegg")).hasSize(1);
                    assertThat(objectCollection.get("beskrivelse")).hasSize(1);;
                    assertThat(objectCollection.get("skatteattestLeder")).hasSize(1);;
                    assertThat(objectCollection.get("konkursattestLeder")).hasSize(1);;
                })
                .verifyComplete();
    }

    @Test
    void shouldMapMinimumInstanceSuccessfully() {
        // Given
        Long sourceApplicationId = 5L;
        String instanceId = "test-instance-id";
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

        byte[] pdfContent = "test-pdf-content".getBytes();
        File expectedFile = File.builder()
                .name("test.pdf")
                .sourceApplicationId(sourceApplicationId)
                .type(MediaType.APPLICATION_PDF)
                .encoding("base64")
                .base64Contents(java.util.Base64.getEncoder().encodeToString(pdfContent))
                .build();

        Function<File, Mono<UUID>> persistFile = file -> Mono.just(uuid);

        when(altinnFileService.fetchFile(instanceId, "ref-data-as-pdf", sourceApplicationId))
                .thenReturn(Mono.just(expectedFile));
        when(altinnFileService.fetchFile(instanceId, "dom-forelegg", sourceApplicationId))
                .thenReturn(Mono.empty());
        when(altinnFileService.fetchFile(instanceId, "beskrivelse-yrkestransportloven", sourceApplicationId))
                .thenReturn(Mono.empty());
        when(altinnFileService.fetchFile(instanceId, "politiattest-dagligleder", sourceApplicationId))
                .thenReturn(Mono.just(expectedFile));
        when(altinnFileService.fetchFile(instanceId, "skatteattest-dagligleder", sourceApplicationId))
                .thenReturn(Mono.empty());
        when(altinnFileService.fetchFile(instanceId, "konkursattest-dagligleder", sourceApplicationId))
                .thenReturn(Mono.empty());
        when(altinnFileService.fetchEbevisFile(instanceId, "RestanserV2", sourceApplicationId))
                .thenReturn(Mono.just(expectedFile));
        when(altinnFileService.fetchEbevisFile(instanceId, "KonkursDrosje", sourceApplicationId))
                .thenReturn(Mono.just(expectedFile));
        // When
        Mono<InstanceObject> result = incomingInstanceMappingService.map(sourceApplicationId, instance, persistFile);

        // Then
        StepVerifier.create(result)
                .assertNext(instanceObject -> {
                    Map<String, String> valuePerKey = instanceObject.getValuePerKey();
                    Map<String, Collection<InstanceObject>> objectCollection = instanceObject.getObjectCollectionPerKey();

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

                            .containsEntry("politiattestLederTittel", "Politiattest for daglig leder")
                            .containsEntry("politiattestLederFormat", "application/pdf")
                            .containsEntry("politiattestLederFil", uuid.toString())
                    ;

                    assertThat(objectCollection).hasSize(4);
                    assertThat(objectCollection.get("domForelegg")).isEmpty();
                    assertThat(objectCollection.get("beskrivelse")).isEmpty();
                    assertThat(objectCollection.get("skatteattestLeder")).isEmpty();
                    assertThat(objectCollection.get("konkursattestLeder")).isEmpty();
                })
                .verifyComplete();
    }
}
