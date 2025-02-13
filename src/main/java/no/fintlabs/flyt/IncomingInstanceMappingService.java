package no.fintlabs.flyt;

import lombok.extern.slf4j.Slf4j;
import no.fint.altinn.model.kafka.KafkaAltinnInstance;
import no.fintlabs.altinn.AltinnFileService;
import no.fintlabs.gateway.instance.InstanceMapper;
import no.fintlabs.gateway.instance.model.File;
import no.fintlabs.gateway.instance.model.instance.InstanceObject;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class IncomingInstanceMappingService implements InstanceMapper<KafkaAltinnInstance> {

    public static final String EMPTY_STRING = "";
    private record DocumentEntry(String reference, String id, MediaType type) {}

    private static final Map<String, Map<String, String>> DOCUMENT_MAPPINGS = Map.of(
            "ref-data-as-pdf", Map.of(
                    "prefix", "soknad",
                    "title", "Søknadsskjema"),
            "dom-forelegg", Map.of(
                    "prefix", "domForelegg",
                    "title", "Kopi av eventuelle dom/forelegg"),
            "beskrivelse-yrkestransportloven", Map.of(
                    "prefix", "beskrivelse",
                    "title", "Håndtering av Yrkestransportloven § 9 c og d"),
            "politiattest-foretak", Map.of(
                    "prefix", "politiattestForetak",
                    "title", "Politiattest for foretaket"),
            "politiattest-dagligleder", Map.of(
                    "prefix", "politiattestLeder",
                    "title", "Politiattest for daglig leder")
    );

    private final AltinnFileService altinnFileService;

    public IncomingInstanceMappingService(AltinnFileService altinnFileService) {
        this.altinnFileService = altinnFileService;
    }

    @Override
    public Mono<InstanceObject> map(Long sourceApplicationId, KafkaAltinnInstance incomingInstance, Function<File, Mono<UUID>> persistFile) {
        log.info("Mapping incoming instance: {}, sourceApplicationId={}", incomingInstance, sourceApplicationId);

        return Flux.fromIterable(new ArrayList<>(DOCUMENT_MAPPINGS.keySet()))
                .flatMap(ref -> altinnFileService.fetchFile(incomingInstance.getInstanceId(), ref, sourceApplicationId)
                        .flatMap(file -> {
                            log.info("Downloaded file for {}", ref);
                            return persistFile.apply(file)
                                    .map(uuid -> {
                                        log.info("Persisted file {} to FLYT with uuid {}", ref, uuid);
                                        return new DocumentEntry(ref, uuid.toString(), file.getType());
                                    });
                        }))
                .collectList()
                .map(documentEntries -> InstanceObject.builder()
                        .valuePerKey(toValuePerKey(incomingInstance, documentEntries))
                        .build());
    }

    private Map<String, String> toValuePerKey(KafkaAltinnInstance incomingInstance, List<DocumentEntry> documents) {
        log.info("Mapping incoming instance with {} documents: {}", documents.size(), documents);

        List<Map.Entry<String, String>> entries = new ArrayList<>();

        entries.add(Map.entry("virksomhetOrganisasjonsnummer", incomingInstance.getOrganizationNumber()));

        entries.add(Map.entry("virksomhetOrganisasjonsnavn", incomingInstance.getOrganizationName()));
        entries.add(Map.entry("virksomhetEpostadresse", incomingInstance.getCompanyEmail()));
        entries.add(Map.entry("virksomhetTelefonnummer", emptyIfNull(incomingInstance.getCompanyPhone())));
        entries.add(Map.entry("virksomhetFylke", incomingInstance.getCountyName()));
        entries.add(Map.entry("virksomhetKommune", emptyIfNull(incomingInstance.getMunicipalityName())));
        entries.add(Map.entry("virksomhetGateadresse", incomingInstance.getCompanyAdressStreet()));
        entries.add(Map.entry("virksomhetPostnummer", incomingInstance.getCompanyAdressPostcode()));
        entries.add(Map.entry("virksomhetPoststed", incomingInstance.getCompanyAdressPostplace()));

        entries.add(Map.entry("postadresseGateadresse", emptyIfNull(incomingInstance.getPostalAdressStreet())));
        entries.add(Map.entry("postadressePostnummer", emptyIfNull(incomingInstance.getPostalAdressPostcode())));
        entries.add(Map.entry("postadressePoststed", emptyIfNull(incomingInstance.getPostalAdressPostplace())));

        entries.add(Map.entry("dagligLederFødselsnummer", emptyIfNull(incomingInstance.getManagerSocialSecurityNumber())));
        entries.add(Map.entry("dagligLederFornavn", emptyIfNull(incomingInstance.getManagerFirstName())));
        entries.add(Map.entry("dagligLederEtternavn", emptyIfNull(incomingInstance.getManagerLastName())));
        entries.add(Map.entry("dagligLederEpostadresse", emptyIfNull(incomingInstance.getManagerEmail())));
        entries.add(Map.entry("dagligLederTelefonnummer", emptyIfNull(incomingInstance.getManagerPhone())));

        documents.forEach(documentEntry -> {
            Map<String, String> values = DOCUMENT_MAPPINGS.get(documentEntry.reference());
            String prefix = values.get("prefix");
            entries.add(Map.entry(prefix + "Tittel", values.get("title")));
            entries.add(Map.entry(prefix + "Format", String.valueOf(documentEntry.type())));
            entries.add(Map.entry(prefix + "Fil", documentEntry.id()));
        });

        return entries.stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private String emptyIfNull(String value) {
        return Optional.ofNullable(value).orElse(EMPTY_STRING);
    }
}
