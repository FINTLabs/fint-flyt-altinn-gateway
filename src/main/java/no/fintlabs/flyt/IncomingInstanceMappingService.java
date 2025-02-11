package no.fintlabs.flyt;

import lombok.extern.slf4j.Slf4j;
import no.fint.altinn.model.kafka.KafkaAltinnInstance;
import no.fintlabs.gateway.instance.InstanceMapper;
import no.fintlabs.gateway.instance.model.File;
import no.fintlabs.gateway.instance.model.instance.InstanceObject;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class IncomingInstanceMappingService implements InstanceMapper<KafkaAltinnInstance> {

    public static final String EMPTY_STRING = "";
    private final WebClient webClient;

    public IncomingInstanceMappingService(WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public Mono<InstanceObject> map(
            Long sourceApplicationId,
            KafkaAltinnInstance incomingInstance,
            Function<File, Mono<UUID>> persistFile
    ) {
        log.info("Mapping incoming instance: {}, sourceApplicationId={}", incomingInstance, sourceApplicationId);
        return webClient.get()
                .uri(String.format("http://10.104.4.130:8080/api/file/%s/ref-data-as-pdf", incomingInstance.getInstanceId()))
                .exchangeToMono(response ->
                        response.bodyToMono(byte[].class)
                                .map(body -> File.builder()
                                        .name("test.pdf")
                                        .base64Contents(Base64.getEncoder().encodeToString(body))
                                        .encoding("UTF-8")
                                        .sourceApplicationId(sourceApplicationId)
                                        .sourceApplicationInstanceId(incomingInstance.getInstanceId())
                                        .type(response.headers().contentType().orElse(MediaType.APPLICATION_OCTET_STREAM))
                                        .build())
                                .flatMap(file -> persistFile.apply(file)
                                        .map(uuid -> InstanceObject.builder()
                                                .valuePerKey(toValuePerKey(incomingInstance, file, uuid))
                                                .build())));
    }

    private Map<String, String> toValuePerKey(KafkaAltinnInstance incomingInstance, File file, UUID uuid) {
        List<Map.Entry<String, String>> entries = new ArrayList<>();

        entries.add(Map.entry("virksomhetOrganisasjonsnummer", incomingInstance.getOrganizationNumber()));

        entries.add(Map.entry("virksomhetOrganisasjonsnavn", incomingInstance.getOrganizationName()));
        entries.add(Map.entry("virksomhetEpostadresse", incomingInstance.getCompanyEmail()));
        entries.add(Map.entry("virksomhetTelefonnummer", Optional.ofNullable(incomingInstance.getCompanyPhone()).orElse(EMPTY_STRING)));
        entries.add(Map.entry("virksomhetFylke", incomingInstance.getCountyName()));
        entries.add(Map.entry("virksomhetKommune", Optional.ofNullable(incomingInstance.getMunicipalityName()).orElse(EMPTY_STRING)));
        entries.add(Map.entry("virksomhetGateadresse", incomingInstance.getCompanyAdressStreet()));
        entries.add(Map.entry("virksomhetPostnummer", incomingInstance.getCompanyAdressPostcode()));
        entries.add(Map.entry("virksomhetPoststed", incomingInstance.getCompanyAdressPostplace()));

        entries.add(Map.entry("postadresseGateadresse", Optional.ofNullable(incomingInstance.getPostalAdressStreet()).orElse(EMPTY_STRING)));
        entries.add(Map.entry("postadressePostnummer", Optional.ofNullable(incomingInstance.getPostalAdressPostcode()).orElse(EMPTY_STRING)));
        entries.add(Map.entry("postadressePoststed", Optional.ofNullable(incomingInstance.getPostalAdressPostplace()).orElse(EMPTY_STRING)));

        entries.add(Map.entry("dagligLederFødselsnummer", Optional.ofNullable(incomingInstance.getPostalAdressPostplace()).orElse(EMPTY_STRING)));
        entries.add(Map.entry("dagligLederFornavn", Optional.ofNullable(incomingInstance.getPostalAdressPostplace()).orElse(EMPTY_STRING)));
        entries.add(Map.entry("dagligLederEtternavn", Optional.ofNullable(incomingInstance.getPostalAdressPostplace()).orElse(EMPTY_STRING)));
        entries.add(Map.entry("dagligLederEpostadresse", Optional.ofNullable(incomingInstance.getPostalAdressPostplace()).orElse(EMPTY_STRING)));
        entries.add(Map.entry("dagligLederTelefonnummer", Optional.ofNullable(incomingInstance.getPostalAdressPostplace()).orElse(EMPTY_STRING)));

        entries.add(Map.entry("soknadTittel", "Søknadsskjema"));
        entries.add(Map.entry("soknadFormat", file.getType().toString()));
        entries.add(Map.entry("soknadFil", uuid.toString()));

        return entries.stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
