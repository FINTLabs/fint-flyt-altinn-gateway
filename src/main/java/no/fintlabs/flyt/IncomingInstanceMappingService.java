package no.fintlabs.flyt;

import lombok.extern.slf4j.Slf4j;
import no.fint.altinn.model.kafka.KafkaAltinnInstance;
import no.fintlabs.gateway.instance.InstanceMapper;
import no.fintlabs.gateway.instance.model.File;
import no.fintlabs.gateway.instance.model.instance.InstanceObject;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
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

        return Flux.fromIterable(Arrays.asList("ref-data-as-pdf", "dom-forelegg", "beskrivelse-yrkestransportloven", "politiattest-foretak", "politiattest-dagligleder"))
                .map(documentReference ->
                        webClient.get()
                                .uri(String.format("http://fint-altinn-service:8080/api/file/%s/%s", incomingInstance.getInstanceId(), documentReference))
                                .exchangeToMono(response ->
                                        response.bodyToMono(byte[].class)
                                                .map(body ->
                                                        File.builder()
                                                                .name("test.pdf")
                                                                .base64Contents(Base64.getEncoder().encodeToString(body))
                                                                .encoding("UTF-8").sourceApplicationId(sourceApplicationId)
                                                                .sourceApplicationInstanceId(incomingInstance.getInstanceId())
                                                                .type(response.headers().contentType().orElse(MediaType.APPLICATION_OCTET_STREAM))
                                                                .build()
                                                )
                                                .flatMap(file -> persistFile.apply(file)
                                                        .map(uuid -> Map.entry(uuid.toString(), file)))))
                .flatMap(x -> x)
                .collectList()
                .map(documents ->
                        InstanceObject.builder()
                                .valuePerKey(toValuePerKey(incomingInstance, documents))
                                .build());
    }

    private Map<String, String> toValuePerKey(KafkaAltinnInstance incomingInstance, List<Map.Entry<String, File>> documents) {
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
        entries.add(Map.entry("soknadFormat", String.valueOf(documents.get(0).getValue().getType())));
        entries.add(Map.entry("soknadFil", documents.get(0).getKey()));

        entries.add(Map.entry("domForeleggTittel", "Kopi av eventuelle dom/forelegg"));
        entries.add(Map.entry("domForeleggFormat", String.valueOf(documents.get(1).getValue().getType())));
        entries.add(Map.entry("domForeleggFil", documents.get(1).getKey()));

        entries.add(Map.entry("beskrivelseTittel", "Håndtering av Yrkestransportloven § 9 c og d"));
        entries.add(Map.entry("beskrivelseFormat", String.valueOf(documents.get(2).getValue().getType())));
        entries.add(Map.entry("beskrivelseFil", documents.get(2).getKey()));

        entries.add(Map.entry("politiattestForetakTittel", "Politiattest for foretaket"));
        entries.add(Map.entry("politiattestForetakFormat", String.valueOf(documents.get(3).getValue().getType())));
        entries.add(Map.entry("politiattestForetakFil", documents.get(3).getKey()));

        entries.add(Map.entry("politiattestLederTittel", "Politiattest for daglig leder"));
        entries.add(Map.entry("politiattestLederFormat", String.valueOf(documents.get(4).getValue().getType())));
        entries.add(Map.entry("politiattestLederFil", documents.get(4).getKey()));

        return entries.stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }


}
