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
import java.util.stream.Stream;

@Service
@Slf4j
public class IncomingInstanceMappingService implements InstanceMapper<KafkaAltinnInstance> {

    public static final String EMPTY_STRING = "";

    private record DocumentEntry(String reference, String id, MediaType type) {
    }

    private static final Map<String, Map<String, String>> DOCUMENT_MAPPINGS = Map.of(
            "ref-data-as-pdf", Map.of(
                    "prefix", "soknad",
                    "title", "Søknadsskjema"),
            "politiattest-foretak", Map.of(
                    "prefix", "politiattestForetak",
                    "title", "Politiattest for foretaket"),
            "politiattest-dagligleder", Map.of(
                    "prefix", "politiattestLeder",
                    "title", "Politiattest for daglig leder"),
            "skatteattest-dagligleder", Map.of(
                    "prefix", "skatteattestLeder",
                    "title", "Skatteattest for daglig leder"),
            "konkursattest-dagligleder", Map.of(
                    "prefix", "konkursattestLeder",
                    "title", "Konkursattest for daglig leder")
    );

    private static final Map<String, Map<String, String>> DOM_FORELEGG_COLLECTION_MAPPINGS = Map.of(
            "dom-forelegg", Map.of(
                    "prefix", "domForelegg",
                    "title", "Kopi av eventuelle dom/forelegg")
    );

    private static final Map<String, Map<String, String>> BESKRIVELSE_COLLECTION_MAPPINGS = Map.of(
            "beskrivelse-yrkestransportloven", Map.of(
                    "prefix", "beskrivelse",
                    "title", "Håndtering av Yrkestransportloven § 9 c og d")
    );

    private static final Map<String, Map<String, String>> EBEVIS_MAPPINGS = Map.of(
            "KonkursDrosje", Map.of(
                    "prefix", "konkursattest",
                    "title", "Konkursattest"),
            "RestanserV2", Map.of(
                    "prefix", "skattattest",
                    "title", "Skatteattest")
    );

    private final AltinnFileService altinnFileService;

    public IncomingInstanceMappingService(AltinnFileService altinnFileService) {
        this.altinnFileService = altinnFileService;
    }

    @Override
    public Mono<InstanceObject> map(Long sourceApplicationId, KafkaAltinnInstance incomingInstance, Function<File, Mono<UUID>> persistFile) {
        log.info("Mapping incoming instance: {}, sourceApplicationId={}", incomingInstance, sourceApplicationId);

        Mono<List<DocumentEntry>> mandatoryDocuments = mapAltinnDocuments(DOCUMENT_MAPPINGS.keySet(),
                incomingInstance, sourceApplicationId,  persistFile);
        Mono<List<DocumentEntry>> domForeleggDocuments = mapAltinnDocuments(DOM_FORELEGG_COLLECTION_MAPPINGS.keySet(),
                incomingInstance, sourceApplicationId,  persistFile);
        Mono<List<DocumentEntry>> beskrivelseDocuments = mapAltinnDocuments(BESKRIVELSE_COLLECTION_MAPPINGS.keySet(),
                incomingInstance, sourceApplicationId,  persistFile);
        Mono<List<DocumentEntry>> ebevisDocuments = mapEbevisDocuments(EBEVIS_MAPPINGS.keySet(),
                incomingInstance, sourceApplicationId, persistFile);

        return Mono.zip(mandatoryDocuments, domForeleggDocuments, beskrivelseDocuments, ebevisDocuments)
                .map(zip -> {
                    List<DocumentEntry> allMandatoryDocuments = Stream.of(zip.getT1(), zip.getT4()).flatMap(List::stream).toList();

                    return InstanceObject.builder()
                                    .valuePerKey(toValuePerKey(incomingInstance, allMandatoryDocuments))
                                    .objectCollectionPerKey(
                                            Map.of("domForelegg", mapCollections(zip.getT2(), DOM_FORELEGG_COLLECTION_MAPPINGS),
                                                    "beskrivelse", mapCollections(zip.getT3(), BESKRIVELSE_COLLECTION_MAPPINGS)
                                            ))
                                    .build();
                        }
                );
    }

    private Mono<List<DocumentEntry>> mapAltinnDocuments(Set<String> refs, KafkaAltinnInstance incomingInstance,
                                                         Long sourceApplicationId,
                                                         Function<File, Mono<UUID>> persistFile) {
        return Flux.fromIterable(refs)
                .flatMap(ref -> altinnFileService.fetchFile(incomingInstance.getInstanceId(), ref, sourceApplicationId)
                        .flatMap(file -> persistFile.apply(file)
                                .map(uuid -> {
                                    log.info("Persisted file {} to FLYT with uuid {}", ref, uuid);
                                    return new DocumentEntry(ref, uuid.toString(), file.getType());
                                })
                                .doOnError(e -> {
                                    throw new RuntimeException("Failed to persist " + file.getName(), e);
                                }))
                        .doOnNext(file -> log.info("Downloaded file ({}) for {}", file, ref))
                )
                .doOnError(e -> {
                    throw new RuntimeException("Error mapping files", e);
                })
                .collect(Collectors.toList());
    }

    private Mono<List<DocumentEntry>> mapEbevisDocuments(Set<String> refs, KafkaAltinnInstance incomingInstance,
                                                         Long sourceApplicationId, Function<File, Mono<UUID>> persistFile) {
        return Flux.fromIterable(refs)
                .flatMap(ref -> altinnFileService.fetchEbevisFile(incomingInstance.getInstanceId(), ref, sourceApplicationId)
                        .flatMap(file -> persistFile.apply(file)
                                .map(uuid -> {
                                    log.info("Persisted file {} to FLYT with uuid {}", ref, uuid);
                                    return new DocumentEntry(ref, uuid.toString(), file.getType());
                                })
                                .doOnError(e -> {
                                    throw new RuntimeException("Failed to persist " + file.getName(), e);
                                })).doOnNext(file -> log.info("Downloaded file ({}) for {}", file, ref))
                )
                .doOnError(e -> {
                    throw new RuntimeException("Error mapping files", e);
                })
                .collect(Collectors.toList());

    }

    private List<InstanceObject> mapCollections(List<DocumentEntry> entries, Map<String, Map<String, String>> mapping) {
        return entries.stream()
                .map(entry -> getInstanceObject(entry, mapping.get(entry.reference())))
                .toList();
    }

    private InstanceObject getInstanceObject(DocumentEntry documentEntry, Map<String, String> values) {
        String prefix = values.get("prefix");
        List<Map.Entry<String, String>> entries = new ArrayList<>();
        entries.add(Map.entry(prefix + "Tittel", values.get("title")));
        entries.add(Map.entry(prefix + "Format", String.valueOf(documentEntry.type())));
        entries.add(Map.entry(prefix + "Fil", documentEntry.id()));

        return InstanceObject.builder()
                .valuePerKey(entries.stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))
                .build();
    }

    private Map<String, String> toValuePerKey(KafkaAltinnInstance incomingInstance, List<DocumentEntry> altinnDocuments) {
        log.info("Mapping incoming instance with {} documents: {}", altinnDocuments.size(), altinnDocuments);

        Stream<Map.Entry<String, String>> virksomhet = Stream.of(
                entry("virksomhetOrganisasjonsnummer", incomingInstance.getOrganizationNumber()),
                entry("virksomhetOrganisasjonsnavn", incomingInstance.getOrganizationName()),
                entry("virksomhetEpostadresse", incomingInstance.getCompanyEmail()),
                entry("virksomhetTelefonnummer", emptyIfNull(incomingInstance.getCompanyPhone())),
                entry("virksomhetFylke", incomingInstance.getCountyName()),
                entry("virksomhetKommune", emptyIfNull(incomingInstance.getMunicipalityName())),
                entry("virksomhetGateadresse", incomingInstance.getCompanyAdressStreet()),
                entry("virksomhetPostnummer", incomingInstance.getCompanyAdressPostcode()),
                entry("virksomhetPoststed", incomingInstance.getCompanyAdressPostplace())
        );

        Stream<Map.Entry<String, String>> postadresse = Stream.of(
                entry("postadresseGateadresse", emptyIfNull(incomingInstance.getPostalAdressStreet())),
                entry("postadressePostnummer", emptyIfNull(incomingInstance.getPostalAdressPostcode())),
                entry("postadressePoststed", emptyIfNull(incomingInstance.getPostalAdressPostplace()))
        );

        Stream<Map.Entry<String, String>> dagligLeder = Stream.of(
                entry("dagligLederFødselsnummer", emptyIfNull(incomingInstance.getManagerSocialSecurityNumber())),
                entry("dagligLederFornavn", emptyIfNull(incomingInstance.getManagerFirstName())),
                entry("dagligLederEtternavn", emptyIfNull(incomingInstance.getManagerLastName())),
                entry("dagligLederEpostadresse", emptyIfNull(incomingInstance.getManagerEmail())),
                entry("dagligLederTelefonnummer", emptyIfNull(incomingInstance.getManagerPhone()))
        );

        Stream<Map.Entry<String, String>> dokumenter = altinnDocuments.stream()
                .flatMap(doc -> {
                    Map<String, String> values = DOCUMENT_MAPPINGS.get(doc.reference());
                    String prefix = values.get("prefix");
                    return Stream.of(
                            entry(prefix + "Tittel", values.get("title")),
                            entry(prefix + "Format", String.valueOf(doc.type())),
                            entry(prefix + "Fil", doc.id())
                    );
                });

        return Stream.concat(Stream.concat(Stream.concat(virksomhet, postadresse), dagligLeder), dokumenter)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Map.Entry<String, String> entry(String key, String value) {
        return Map.entry(key, value);
    }

    private String emptyIfNull(String value) {
        return Optional.ofNullable(value).orElse(EMPTY_STRING);
    }
}
