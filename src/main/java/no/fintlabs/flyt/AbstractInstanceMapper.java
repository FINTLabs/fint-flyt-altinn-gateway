package no.fintlabs.flyt;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import no.fint.altinn.model.kafka.KafkaAltinnInstance;
import no.fintlabs.altinn.AltinnFileService;
import no.fintlabs.gateway.instance.model.File;
import no.fintlabs.gateway.instance.model.instance.InstanceObject;
import org.springframework.http.MediaType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public abstract class AbstractInstanceMapper implements ApplicationInstanceMapper {

    protected static final String EMPTY_STRING = "";

    protected static final String SOKNAD = "ref-data-as-pdf";
    protected static final String POLITIATTTEST_DAGLIGLEDER_REF = "politiattest-dagligleder";
    protected static final String SKATTEATTEST_DAGLIGLEDER_REF = "skatteattest-dagligleder";
    protected static final String KONKURSATTEST_DAGLIGLEDER_REF = "konkursattest-dagligleder";
    protected static final String DOM_FORELEGG_REF = "dom-forelegg";
    protected static final String BESKRIVELSE_REF = "beskrivelse-yrkestransportloven";
    protected static final String POLITIATTEST_FORETAK_REF = "politiattest-foretak";
    protected static final String DOKUMENTASJON_FAGKOMPETANSE_REF = "dokumentasjon-fagkompetanse";

    protected static final String EBEVIS_KONKURSATTEST_FORETAK_REF = "KonkursDrosje";
    protected static final String EBEVIS_SKATTEATTEST_FORETAK_REF = "RestanserV2";

    protected record DocumentEntry(String reference, String id, MediaType type) {
    }

    protected static final Map<String, Map<String, String>> DOCUMENT_MAPPINGS = Map.of(
            SOKNAD, Map.of(
                    "prefix", "soknad",
                    "title", "Søknadsskjema"),
            POLITIATTTEST_DAGLIGLEDER_REF, Map.of(
                    "prefix", "politiattestLeder",
                    "title", "Politiattest for daglig leder"),
            SKATTEATTEST_DAGLIGLEDER_REF, Map.of(
                    "prefix", "skatteattestLeder",
                    "title", "Skatteattest for daglig leder"),
            KONKURSATTEST_DAGLIGLEDER_REF, Map.of(
                    "prefix", "konkursattestLeder",
                    "title", "Konkursattest for daglig leder"),
            DOM_FORELEGG_REF, Map.of(
                    "prefix", "domForelegg",
                    "title", "Kopi av eventuelle dom/forelegg"),
            BESKRIVELSE_REF, Map.of(
                    "prefix", "beskrivelse",
                    "title", "Håndtering av Yrkestransportloven § 9 c og d"),
            EBEVIS_KONKURSATTEST_FORETAK_REF, Map.of(
                    "prefix", "konkursattestForetak",
                    "title", "Konkursattest for foretaket"),
            EBEVIS_SKATTEATTEST_FORETAK_REF, Map.of(
                    "prefix", "skatteattestForetak",
                    "title", "Skatteattest for foretaket"),
            POLITIATTEST_FORETAK_REF, Map.of(
                    "prefix", "politiattestForetak",
                    "title", "Politiattest for foretaket"),
            DOKUMENTASJON_FAGKOMPETANSE_REF, Map.of(
                    "prefix", "kompetanse",
                    "title", "Dokumentasjon av fagkompetanse")
    );

    private final AltinnFileService altinnFileService;

    protected AbstractInstanceMapper(AltinnFileService altinnFileService) {
        this.altinnFileService = altinnFileService;
    }

    protected Mono<List<DocumentEntry>> mapAltinnDocuments(List<String> documentKeys,
                                                           KafkaAltinnInstance incomingInstance,
                                                           Long sourceApplicationId,
                                                           Function<File, Mono<UUID>> persistFile) {
        return mapDocuments(
                documentKeys,
                ref -> altinnFileService.fetchFile(incomingInstance.getInstanceId(), ref, sourceApplicationId),
                persistFile,
                "altinn"
        );
    }

    protected Mono<List<DocumentEntry>> mapEbevisDocuments(List<String> documentKeys,
                                                           KafkaAltinnInstance incomingInstance,
                                                           Long sourceApplicationId,
                                                           Function<File, Mono<UUID>> persistFile) {
        return mapDocuments(
                documentKeys,
                ref -> altinnFileService.fetchEbevisFile(incomingInstance.getInstanceId(), ref, sourceApplicationId),
                persistFile,
                "altinn ebevis"
        );
    }

    private Mono<List<DocumentEntry>> mapDocuments(List<String> documentKeys,
                                                   Function<String, Mono<File>> fetchFile,
                                                   Function<File, Mono<UUID>> persistFile,
                                                   String sourceName) {
        Set<String> refs = documentKeys.stream().filter(DOCUMENT_MAPPINGS::containsKey).collect(Collectors.toSet());
        log.debug("Mapping {} documents: {}", sourceName, refs);

        return Flux.fromIterable(refs)
                .flatMap(ref -> fetchFile.apply(ref)
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

    protected Map<String, Collection<InstanceObject>> mapCollectionsDynamically(List<DocumentEntry> entries, List<String> expectedReferences) {
        Map<String, Collection<InstanceObject>> result = expectedReferences.stream()
                .filter(DOCUMENT_MAPPINGS::containsKey)
                .collect(Collectors.toMap(
                        ref -> DOCUMENT_MAPPINGS.get(ref).get("prefix"),
                        ref -> new ArrayList<InstanceObject>(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        Map<String, List<InstanceObject>> grouped = entries.stream()
                .filter(entry -> DOCUMENT_MAPPINGS.containsKey(entry.reference()))
                .collect(Collectors.groupingBy(
                        entry -> DOCUMENT_MAPPINGS.get(entry.reference()).get("prefix"),
                        Collectors.mapping(
                                entry -> getInstanceObject(entry, DOCUMENT_MAPPINGS.get(entry.reference())),
                                Collectors.toList()
                        )
                ));

        grouped.forEach((key, value) -> result.put(key, new ArrayList<>(value)));

        return result;
    }

    protected abstract Map<String, String> toValuePerKey(KafkaAltinnInstance incomingInstance, List<DocumentEntry> altinnDocuments);

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

    protected Map.Entry<String, String> entry(String key, String value) {
        log.debug("Document entry: {}, {}", key, value);
        return Map.entry(key, value);
    }

    @NonNull
    protected String emptyIfNull(String value) {
        return Optional.ofNullable(value).orElse(EMPTY_STRING);
    }
}
